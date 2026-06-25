package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses.PedidoStatusResponse;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.ServicoPedido;

@Component
public class CancelarPedidoUC {
    private final ServicoPedido servicoPedido;

    @Autowired
    public CancelarPedidoUC(ServicoPedido servicoPedido) {
        this.servicoPedido = servicoPedido;
    }

    public PedidoStatusResponse run(long idPedido) {
        servicoPedido.cancelar(idPedido);
        Pedido pedido = servicoPedido.recuperaPorId(idPedido);
        return PedidoStatusResponse.de(pedido, servicoPedido.historico(idPedido));
    }
}
