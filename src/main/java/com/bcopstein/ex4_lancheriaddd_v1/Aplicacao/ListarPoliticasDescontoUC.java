package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses.PoliticasDescontoResponse;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.ServicoDesconto;

@Component
public class ListarPoliticasDescontoUC {
    private ServicoDesconto servicoDesconto;

    @Autowired
    public ListarPoliticasDescontoUC(ServicoDesconto servicoDesconto) {
        this.servicoDesconto = servicoDesconto;
    }

    public PoliticasDescontoResponse run() {
        // ordem estavel e deterministica (keySet de HashMap nao garante ordem)
        return new PoliticasDescontoResponse(
            servicoDesconto.listarPoliticas().stream().sorted().toList(),
            servicoDesconto.getPoliticaCorrente());
    }
}
