package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Apresentacao;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;

// Driver de integracao da autenticacao/autorizacao (P5 / UC12).
// Usa plain @SpringBootTest (mesmo contexto cacheado dos demais testes de integracao, para nao
// re-executar o data.sql num 2o contexto e violar a PK de clientes) e monta o MockMvc a partir do
// WebApplicationContext — assim o AutenticacaoInterceptor (registrado via WebConfig) e aplicado.
// Clientes vem do data.sql (cpf 9001 huguinho.pato@email.com / senha123); admin vem do application.yaml.
// As asserts evitam mutar o banco compartilhado (rotas de leitura + corpo invalido no cadastro).
//
// ───────────────────────────────────────────────────────────────────────────────
// Casos de teste:
//  1. semToken_rotaProtegida_retorna401         - GET /pedidos/1/status sem token -> 401
//  2. loginCliente_retornaTokenEPapelSemSenha   - POST /auth/login (cliente) -> 200, token + papel CLIENTE, sem senha
//  3. clienteToken_rotaCliente_naoEhBloqueado   - GET /cardapio/corrente com token cliente -> != 401/403 (UC5)
//  4. clienteToken_consultaPedido_naoEhBloqueado- GET /pedidos/1/status com token cliente -> != 401/403 (404 ok)
//  5. clienteToken_rotaAdmin_retorna403         - GET /descontos/politicas com token cliente -> 403
//  6. loginAdmin_retornaPapelAdmin              - POST /auth/login (admin) -> 200, papel ADMIN
//  7. adminToken_rotaAdminDescontos_retorna200  - GET /descontos/politicas com token admin -> 200 (UC3)
//  8. adminToken_rotaAdminCardapio_naoBloqueado - GET /cardapio/lista com token admin -> != 401/403 (UC1)
//  9. tokenInvalido_retorna401                  - Bearer desconhecido em rota protegida -> 401
// 10. loginSenhaErrada_retorna401               - POST /auth/login senha incorreta -> 401 (uniforme, sem enumeracao)
// 11. rotasPublicas_naoExigemToken              - POST /clientes (corpo invalido) e POST /auth/login sem token -> != 401
// ───────────────────────────────────────────────────────────────────────────────
@SpringBootTest
class AutenticacaoIntegrationTest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mvc;

    private static final String CLIENTE_EMAIL = "huguinho.pato@email.com";
    private static final String CLIENTE_SENHA = "senha123";
    private static final String ADMIN_EMAIL = "admin@pizzaria.com";
    private static final String ADMIN_SENHA = "admin123";

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(wac).build();
    }

    private String corpoLogin(String email, String senha) {
        return "{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}";
    }

    private String login(String email, String senha) throws Exception {
        MvcResult res = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin(email, senha)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private int statusDe(MvcResult res) {
        return res.getResponse().getStatus();
    }

    @Test
    void semToken_rotaProtegida_retorna401() throws Exception {
        mvc.perform(get("/pedidos/1/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginCliente_retornaTokenEPapelSemSenha() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin(CLIENTE_EMAIL, CLIENTE_SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.papel").value("CLIENTE"))
                .andExpect(jsonPath("$.email").value(CLIENTE_EMAIL))
                .andExpect(jsonPath("$.senha").doesNotExist());
    }

    @Test
    void clienteToken_rotaCliente_naoEhBloqueado() throws Exception {
        String token = login(CLIENTE_EMAIL, CLIENTE_SENHA);
        int s = statusDe(mvc.perform(get("/cardapio/corrente")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn());
        assertNotEquals(401, s, "cliente autenticado nao deve receber 401 em rota de cliente");
        assertNotEquals(403, s, "cliente autenticado nao deve receber 403 em rota de cliente");
    }

    @Test
    void clienteToken_consultaPedido_naoEhBloqueado() throws Exception {
        String token = login(CLIENTE_EMAIL, CLIENTE_SENHA);
        int s = statusDe(mvc.perform(get("/pedidos/1/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn());
        assertNotEquals(401, s);
        assertNotEquals(403, s);
    }

    @Test
    void clienteToken_rotaAdmin_retorna403() throws Exception {
        String token = login(CLIENTE_EMAIL, CLIENTE_SENHA);
        mvc.perform(get("/descontos/politicas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginAdmin_retornaPapelAdmin() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin(ADMIN_EMAIL, ADMIN_SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.papel").value("ADMIN"));
    }

    @Test
    void adminToken_rotaAdminDescontos_retorna200() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_SENHA);
        mvc.perform(get("/descontos/politicas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void adminToken_rotaAdminCardapio_naoBloqueado() throws Exception {
        String token = login(ADMIN_EMAIL, ADMIN_SENHA);
        int s = statusDe(mvc.perform(get("/cardapio/lista")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn());
        assertNotEquals(401, s);
        assertNotEquals(403, s);
    }

    @Test
    void tokenInvalido_retorna401() throws Exception {
        mvc.perform(get("/pedidos/1/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-inexistente"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginSenhaErrada_retorna401() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin(CLIENTE_EMAIL, "senha-errada")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotasPublicas_naoExigemToken() throws Exception {
        // POST /clientes e publico: com corpo invalido retorna 400 (Bean Validation), NUNCA 401.
        // Corpo invalido evita persistir e poluir o banco compartilhado entre os testes.
        int sCadastro = statusDe(mvc.perform(post("/clientes")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn());
        assertNotEquals(401, sCadastro, "POST /clientes deve ser publico");

        // POST /auth/login em si e publico.
        int sLogin = statusDe(mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoLogin(CLIENTE_EMAIL, CLIENTE_SENHA))).andReturn());
        assertNotEquals(401, sLogin, "POST /auth/login deve ser publico");
    }
}
