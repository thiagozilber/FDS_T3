package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Excecoes;

// Saldo de estoque insuficiente no momento da baixa atomica (corrida entre verificacao e baixa).
// Mapeada para HTTP 409 (Conflict) pelo RestExceptionHandler.
public class EstoqueInsuficienteException extends RuntimeException {
    public EstoqueInsuficienteException(String mensagem) {
        super(mensagem);
    }
}
