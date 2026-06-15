package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Apresentacao;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Apresentacao.Presenters.PoliticasDescontoPresenter;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.DefinirPoliticaDescontoUC;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.ListarPoliticasDescontoUC;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses.PoliticasDescontoResponse;

@RestController
@RequestMapping("/descontos")
public class DescontoController {
    private ListarPoliticasDescontoUC listarPoliticasUC;
    private DefinirPoliticaDescontoUC definirPoliticaUC;

    public DescontoController(ListarPoliticasDescontoUC listarPoliticasUC,
                              DefinirPoliticaDescontoUC definirPoliticaUC) {
        this.listarPoliticasUC = listarPoliticasUC;
        this.definirPoliticaUC = definirPoliticaUC;
    }

    @GetMapping("/politicas")
    @CrossOrigin("*")
    public PoliticasDescontoPresenter listarPoliticas() {
        PoliticasDescontoResponse resp = listarPoliticasUC.run();
        return new PoliticasDescontoPresenter(resp.codigos(), resp.corrente());
    }

    @PutMapping("/corrente/{codigo}")
    @CrossOrigin("*")
    public PoliticasDescontoPresenter definirPolitica(@PathVariable(value="codigo") String codigo) {
        definirPoliticaUC.run(codigo);
        PoliticasDescontoResponse resp = listarPoliticasUC.run();
        return new PoliticasDescontoPresenter(resp.codigos(), resp.corrente());
    }
}
