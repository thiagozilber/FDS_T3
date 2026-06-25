package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses;

import java.util.List;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.PedidoEntregue;

// Lista de pedidos entregues no intervalo (UC10). A fabrica 'de' concentra o mapeamento
// dominio -> DTO (DRY), espelhando PedidoStatusResponse.de.
public record PedidosEntreguesResponse(List<PedidoEntregueResponse> pedidos) {

    public static PedidosEntreguesResponse de(List<PedidoEntregue> entregues) {
        List<PedidoEntregueResponse> dtos = entregues.stream()
            .map(pe -> new PedidoEntregueResponse(
                pe.pedido().getId(),
                pe.pedido().getCliente().getCpf(),
                pe.pedido().getStatus().name(),
                pe.pedido().getValorCobrado(),
                pe.pedido().getEnderecoEntrega(),
                pe.dataHoraEntrega().toString()))
            .toList();
        return new PedidosEntreguesResponse(dtos);
    }
}
