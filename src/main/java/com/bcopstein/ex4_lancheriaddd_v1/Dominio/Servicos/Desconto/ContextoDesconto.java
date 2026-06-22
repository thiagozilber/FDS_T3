package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto;

// Contexto de avaliacao das politicas de desconto.
// pedidosUltimos20Dias e fornecido pelo chamador (nao ha persistencia de pedidos na fase P1).
public record ContextoDesconto(int pedidosUltimos20Dias) {
    public ContextoDesconto {
        if (pedidosUltimos20Dias < 0) {
            throw new IllegalArgumentException(
                "pedidosUltimos20Dias nao pode ser negativo: " + pedidosUltimos20Dias);
        }
    }
}
