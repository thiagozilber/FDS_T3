package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Excecoes;

// Falha de autenticacao (e-mail inexistente ou senha incorreta) -> mapeada para HTTP 401
// pelo RestExceptionHandler. Distinta de IllegalArgumentException (campos ausentes/mal formados = 400).
public class CredenciaisInvalidasException extends RuntimeException {
    public CredenciaisInvalidasException(String mensagem) {
        super(mensagem);
    }
}
