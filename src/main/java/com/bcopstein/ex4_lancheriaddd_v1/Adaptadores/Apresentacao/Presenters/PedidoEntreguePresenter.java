package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Apresentacao.Presenters;

public record PedidoEntreguePresenter(
        long id,
        String clienteCpf,
        String status,
        double valorCobrado,
        String enderecoEntrega,
        String dataHoraEntrega) {
}
