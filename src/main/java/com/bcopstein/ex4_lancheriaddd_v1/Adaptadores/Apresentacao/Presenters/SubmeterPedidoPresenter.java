package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Apresentacao.Presenters;

import java.util.List;

public record SubmeterPedidoPresenter(long id, String status, double valor, double desconto,
        double impostos, double valorCobrado, List<String> itensIndisponiveis) {
}
