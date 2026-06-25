package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Requests;

// Item do carrinho recebido no corpo do POST /pedidos.
public record ItemPedidoRequest(long produtoId, int quantidade) {
}
