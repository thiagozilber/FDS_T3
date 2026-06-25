package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses.PedidoStatusResponse;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.ServicoPedido;

@Component
public class ConsultarStatusPedidoUC {
    private ServicoPedido servicoPedido;

    @Autowired
    public ConsultarStatusPedidoUC(ServicoPedido servicoPedido) {
        this.servicoPedido = servicoPedido;
    }

    public PedidoStatusResponse run(long idPedido) {
        Pedido pedido = servicoPedido.recuperaPorId(idPedido);
        return PedidoStatusResponse.de(pedido, servicoPedido.historico(idPedido));
    }
}
