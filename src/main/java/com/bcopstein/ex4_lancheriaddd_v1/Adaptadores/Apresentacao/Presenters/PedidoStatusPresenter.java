package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Apresentacao.Presenters;

import java.util.List;

public record PedidoStatusPresenter(long id, String statusAtual, List<TransicaoStatusPresenter> historico) {
}
