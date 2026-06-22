package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Excecoes;

// Recurso de dominio inexistente -> mapeado para HTTP 404 pelo RestExceptionHandler.
public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
