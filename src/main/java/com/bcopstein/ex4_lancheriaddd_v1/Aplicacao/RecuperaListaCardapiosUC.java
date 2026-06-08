package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses.CabecalhoCardapioResponse;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.CardapioService;

@Component
public class RecuperaListaCardapiosUC {
    private CardapioService cardapioService;

    @Autowired
    public RecuperaListaCardapiosUC(CardapioService cardapioService){
        this.cardapioService = cardapioService;
    }

    public CabecalhoCardapioResponse run(){
        return new CabecalhoCardapioResponse(cardapioService.recuperaListaDeCardapios());
    }    
}
