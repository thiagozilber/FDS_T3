package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses;

// DTO de um pedido entregue (UC10). dataHoraEntrega serializada como String ISO-8601,
// espelhando como PedidoStatusResponse renderiza timestamps.
public record PedidoEntregueResponse(
        long id,
        String clienteCpf,
        String status,
        double valorCobrado,
        String enderecoEntrega,
        String dataHoraEntrega) {
}
