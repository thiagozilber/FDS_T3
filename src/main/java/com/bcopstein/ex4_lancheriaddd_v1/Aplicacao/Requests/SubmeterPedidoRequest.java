package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Requests;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

// Corpo do POST /pedidos (UC6). O cliente e identificado pelo cpf ate a autenticacao (P5).
// Bean Validation (@Valid no controller) protege o limite HTTP: cpf/endereco nao vazios e dentro do
// tamanho das colunas; lista de itens nao vazia e limitada (anti-abuso). @Valid cascateia aos itens.
public record SubmeterPedidoRequest(
        @NotBlank @Size(max = 15) String clienteCpf,
        @NotBlank @Size(max = 255) String enderecoEntrega,
        @NotEmpty @Size(max = 50) @Valid List<ItemPedidoRequest> itens) {
}
