package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Seguranca;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Portas.RepositorioSessao;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Portas.Sessao;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Papel;

// Armazenamento de sessoes em memoria. Tokens opacos (UUID), sem expiracao;
// reinicia a cada boot (consistente com o banco H2 in-memory). ConcurrentHashMap
// porque login e as simulacoes Cozinha/Entrega rodam em threads distintas.
@Component
public class RepositorioSessaoMemoria implements RepositorioSessao {

    private final Map<String, Sessao> sessoes = new ConcurrentHashMap<>();

    @Override
    public String criar(String principal, Papel papel) {
        String token = UUID.randomUUID().toString();
        sessoes.put(token, new Sessao(principal, papel));
        return token;
    }

    @Override
    public Sessao validar(String token) {
        return token == null ? null : sessoes.get(token);
    }
}
