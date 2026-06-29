package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Portas;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Papel;

// Sessao autenticada resolvida a partir de um token.
// principal = CPF (CLIENTE) ou e-mail (ADMIN).
public record Sessao(String principal, Papel papel) {
}
