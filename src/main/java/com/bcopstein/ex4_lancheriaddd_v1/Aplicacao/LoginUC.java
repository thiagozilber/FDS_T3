package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Config.AdminProperties;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Portas.RepositorioSessao;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Requests.LoginRequest;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses.LoginResponse;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Cliente;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Papel;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Excecoes.CredenciaisInvalidasException;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.ServicoCliente;

// UC12 (Entrar no sistema). Autentica admin (via config) ou cliente (via dominio)
// e emite um token Bearer. Falhas usam CredenciaisInvalidasException -> 401 (sem enumeracao).
@Component
public class LoginUC {

    private final ServicoCliente servicoCliente;
    private final AdminProperties admin;
    private final RepositorioSessao repositorioSessao;

    @Autowired
    public LoginUC(ServicoCliente servicoCliente, AdminProperties admin, RepositorioSessao repositorioSessao) {
        this.servicoCliente = servicoCliente;
        this.admin = admin;
        this.repositorioSessao = repositorioSessao;
    }

    public LoginResponse run(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Requisicao de login nao pode ser nula.");
        }

        // Admin via config (verificado primeiro para que um cliente nao possa "sombrear" o e-mail do admin).
        if (admin.getEmail() != null && admin.getEmail().equalsIgnoreCase(request.email())) {
            if (admin.getSenha() == null || !admin.getSenha().equals(request.senha())) {
                throw new CredenciaisInvalidasException("Credenciais inválidas.");
            }
            String token = repositorioSessao.criar(admin.getEmail(), Papel.ADMIN);
            return new LoginResponse(token, null, "Administrador", admin.getEmail(), Papel.ADMIN.name());
        }

        // Cliente via dominio (reaproveita autenticar -> CredenciaisInvalidasException -> 401).
        Cliente cliente = servicoCliente.autenticar(request.email(), request.senha());
        String token = repositorioSessao.criar(cliente.getCpf(), Papel.CLIENTE);
        return new LoginResponse(token, cliente.getCpf(), cliente.getNome(), cliente.getEmail(), Papel.CLIENTE.name());
    }
}
