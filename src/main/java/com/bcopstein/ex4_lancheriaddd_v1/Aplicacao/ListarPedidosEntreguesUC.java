package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses.PedidosEntreguesResponse;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.ServicoPedido;

// UC10: lista os pedidos entregues entre duas datas. A UC expande as datas para uma janela
// de instantes [ini 00:00, fim+1 00:00) -- o dia 'fim' inteiro fica incluso (D15).
@Component
public class ListarPedidosEntreguesUC {
    private final ServicoPedido servicoPedido;

    @Autowired
    public ListarPedidosEntreguesUC(ServicoPedido servicoPedido) {
        this.servicoPedido = servicoPedido;
    }

    public PedidosEntreguesResponse run(LocalDate ini, LocalDate fim) {
        if (ini == null || fim == null) {
            throw new IllegalArgumentException("Datas ini e fim sao obrigatorias");
        }
        if (ini.isAfter(fim)) {
            throw new IllegalArgumentException(
                "Data inicial nao pode ser posterior a final: " + ini + " > " + fim);
        }
        return PedidosEntreguesResponse.de(
            servicoPedido.listarEntreguesEntre(ini.atStartOfDay(), fim.plusDays(1).atStartOfDay()));
    }
}
