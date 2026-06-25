package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Apresentacao;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Apresentacao.Presenters.PedidoEntreguePresenter;
import com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Apresentacao.Presenters.PedidoStatusPresenter;
import com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Apresentacao.Presenters.SubmeterPedidoPresenter;
import com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Apresentacao.Presenters.TransicaoStatusPresenter;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.CancelarPedidoUC;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.ConsultarStatusPedidoUC;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.ListarPedidosEntreguesUC;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.PagarPedidoUC;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.SubmeterPedidoParaAprovacaoUC;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Requests.SubmeterPedidoRequest;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses.PedidoStatusResponse;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses.SubmeterPedidoResponse;

@RestController
@RequestMapping("/pedidos")
public class PedidoController {
    private final SubmeterPedidoParaAprovacaoUC submeterUC;
    private final ConsultarStatusPedidoUC consultarStatusUC;
    private final CancelarPedidoUC cancelarUC;
    private final PagarPedidoUC pagarUC;
    private final ListarPedidosEntreguesUC listarEntreguesUC;

    public PedidoController(SubmeterPedidoParaAprovacaoUC submeterUC,
                            ConsultarStatusPedidoUC consultarStatusUC,
                            CancelarPedidoUC cancelarUC,
                            PagarPedidoUC pagarUC,
                            ListarPedidosEntreguesUC listarEntreguesUC) {
        this.submeterUC = submeterUC;
        this.consultarStatusUC = consultarStatusUC;
        this.cancelarUC = cancelarUC;
        this.pagarUC = pagarUC;
        this.listarEntreguesUC = listarEntreguesUC;
    }

    @PostMapping("")
    public ResponseEntity<SubmeterPedidoPresenter> submeter(@Valid @RequestBody SubmeterPedidoRequest req) {
        SubmeterPedidoResponse r = submeterUC.run(req);
        SubmeterPedidoPresenter presenter = new SubmeterPedidoPresenter(r.id(), r.status(), r.valor(),
            r.desconto(), r.impostos(), r.valorCobrado(), r.itensIndisponiveis());
        // 201 Created + Location apontando para o recurso de status do pedido recem-criado.
        return ResponseEntity.created(URI.create("/pedidos/" + r.id() + "/status")).body(presenter);
    }

    @GetMapping("/{id}/status")
    public PedidoStatusPresenter status(@PathVariable(value="id") long id) {
        return paraPresenter(consultarStatusUC.run(id));
    }

    @PostMapping("/{id}/cancelar")
    public PedidoStatusPresenter cancelar(@PathVariable(value="id") long id) {
        return paraPresenter(cancelarUC.run(id));
    }

    @PostMapping("/{id}/pagar")
    public PedidoStatusPresenter pagar(@PathVariable(value="id") long id) {
        return paraPresenter(pagarUC.run(id));
    }

    // UC10: GET /pedidos/entregues?ini=YYYY-MM-DD&fim=YYYY-MM-DD. Segmento literal 'entregues',
    // sem colisao com as rotas /{id}/... (essas tem dois segmentos). ini/fim obrigatorios.
    @GetMapping("/entregues")
    public List<PedidoEntreguePresenter> entregues(
            @RequestParam("ini") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ini,
            @RequestParam("fim") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return listarEntreguesUC.run(ini, fim).pedidos().stream()
            .map(r -> new PedidoEntreguePresenter(r.id(), r.clienteCpf(), r.status(),
                r.valorCobrado(), r.enderecoEntrega(), r.dataHoraEntrega()))
            .toList();
    }

    private PedidoStatusPresenter paraPresenter(PedidoStatusResponse resp) {
        List<TransicaoStatusPresenter> historico = resp.historico().stream()
            .map(t -> new TransicaoStatusPresenter(t.status(), t.dataHora()))
            .toList();
        return new PedidoStatusPresenter(resp.id(), resp.statusAtual(), historico);
    }
}
