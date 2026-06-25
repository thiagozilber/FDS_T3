package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses;

import java.util.List;

// Resultado de UC6. Em caso de RECUSADO, itensIndisponiveis traz as descricoes dos produtos sem estoque.
public record SubmeterPedidoResponse(long id, String status, double valor, double desconto,
        double impostos, double valorCobrado, List<String> itensIndisponiveis) {
}
