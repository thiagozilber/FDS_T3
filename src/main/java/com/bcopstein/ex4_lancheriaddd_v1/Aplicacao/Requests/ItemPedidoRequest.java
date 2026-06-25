package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Requests;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

// Item do carrinho recebido no corpo do POST /pedidos. Quantidade limitada a 100 por item (anti-abuso).
public record ItemPedidoRequest(
        @Positive long produtoId,
        @Min(1) @Max(100) int quantidade) {
}
