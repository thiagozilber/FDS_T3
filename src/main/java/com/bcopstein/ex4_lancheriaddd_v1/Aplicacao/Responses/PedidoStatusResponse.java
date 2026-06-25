package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses;

import java.util.List;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.TransicaoStatus;

// Status atual + historico carimbado de um pedido (UC7/UC8/UC9). A fabrica 'de' concentra o
// mapeamento dominio -> DTO usado pelos tres casos de uso (DRY).
public record PedidoStatusResponse(long id, String statusAtual, List<TransicaoStatusResponse> historico) {

    public static PedidoStatusResponse de(Pedido pedido, List<TransicaoStatus> historico) {
        List<TransicaoStatusResponse> transicoes = historico.stream()
            .map(t -> new TransicaoStatusResponse(t.status().name(), t.dataHora().toString()))
            .toList();
        return new PedidoStatusResponse(pedido.getId(), pedido.getStatus().name(), transicoes);
    }
}
