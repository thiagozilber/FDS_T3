package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Requests;

import java.util.List;

// Corpo do POST /pedidos (UC6). O cliente e identificado pelo cpf ate a autenticacao (P5).
public record SubmeterPedidoRequest(String clienteCpf, String enderecoEntrega, List<ItemPedidoRequest> itens) {
}
