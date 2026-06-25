package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses.PedidoStatusResponse;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Excecoes.PagamentoRecusadoException;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.ICozinhaService;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.IPagamentoService;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.ServicoPedido;

// UC9: orquestra o pagamento. A orquestracao mora aqui (nao em ServicoPedido) para evitar ciclo de
// beans entre ServicoPedido e ICozinhaService (decisao D13). As transicoes de status sao persistidas
// por ServicoPedido (Seam #2); a sim de cozinha avanca PREPARACAO/PRONTO depois do handoff (Pessoa 2).
@Component
public class PagarPedidoUC {
    private final ServicoPedido servicoPedido;
    private final IPagamentoService pagamentoService;
    private final ICozinhaService cozinhaService;

    @Autowired
    public PagarPedidoUC(ServicoPedido servicoPedido, IPagamentoService pagamentoService,
                         ICozinhaService cozinhaService) {
        this.servicoPedido = servicoPedido;
        this.pagamentoService = pagamentoService;
        this.cozinhaService = cozinhaService;
    }

    // @Transactional: pagamento + transicoes (PAGO, AGUARDANDO) + handoff de cozinha sao atomicos.
    @Transactional
    public PedidoStatusResponse run(long idPedido) {
        Pedido pedido = servicoPedido.recuperaPorId(idPedido);
        if (pedido.getStatus() != Pedido.Status.APROVADO) {
            throw new IllegalArgumentException(
                "Pedido nao esta apto a pagamento; status atual: " + pedido.getStatus());
        }
        if (!pagamentoService.processarPagamento(pedido)) {
            throw new PagamentoRecusadoException("Pagamento recusado para o pedido " + idPedido);
        }
        servicoPedido.registrarTransicao(idPedido, Pedido.Status.PAGO);
        servicoPedido.registrarTransicao(idPedido, Pedido.Status.AGUARDANDO);
        // Handoff para a simulacao de cozinha (Pessoa 2); ela carimba os proximos status via Seam #2.
        cozinhaService.chegadaDePedido(servicoPedido.recuperaPorId(idPedido));

        Pedido atual = servicoPedido.recuperaPorId(idPedido);
        return PedidoStatusResponse.de(atual, servicoPedido.historico(idPedido));
    }
}
