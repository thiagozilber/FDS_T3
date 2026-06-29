package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Portas;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Papel;

// Porta (DIP) para emissao/validacao de tokens de sessao.
// Implementada na camada de adaptadores (RepositorioSessaoMemoria).
public interface RepositorioSessao {

    // Emite um token opaco para o principal autenticado com o papel informado.
    String criar(String principal, Papel papel);

    // Resolve o token em uma sessao; retorna null quando o token e desconhecido.
    Sessao validar(String token);
}
