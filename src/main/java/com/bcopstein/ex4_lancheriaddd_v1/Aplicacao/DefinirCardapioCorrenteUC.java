package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.CardapioService;

@Component
public class DefinirCardapioCorrenteUC {
    private CardapioService cardapioService;

    @Autowired
    public DefinirCardapioCorrenteUC(CardapioService cardapioService) {
        this.cardapioService = cardapioService;
    }

    public long run(long idCardapio) {
        cardapioService.defineCardapioCorrente(idCardapio);
        return idCardapio;
    }
}
