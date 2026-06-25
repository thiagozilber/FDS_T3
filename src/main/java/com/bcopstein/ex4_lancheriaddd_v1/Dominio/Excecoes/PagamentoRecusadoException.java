package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Excecoes;

// Pagamento recusado pelo provedor (condicao de negocio, NAO erro interno do servidor).
// Mapeada para HTTP 402 (Payment Required) pelo RestExceptionHandler.
public class PagamentoRecusadoException extends RuntimeException {
    public PagamentoRecusadoException(String mensagem) {
        super(mensagem);
    }
}
