package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.ServicoDesconto;

@Component
public class DefinirPoliticaDescontoUC {
    private ServicoDesconto servicoDesconto;

    @Autowired
    public DefinirPoliticaDescontoUC(ServicoDesconto servicoDesconto) {
        this.servicoDesconto = servicoDesconto;
    }

    public String run(String codigo) {
        servicoDesconto.definirPolitica(codigo);
        return codigo;
    }
}
