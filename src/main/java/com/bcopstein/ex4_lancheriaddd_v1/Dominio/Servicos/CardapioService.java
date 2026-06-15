package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.CardapioRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.CabecalhoCardapio;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Cardapio;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Produto;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Excecoes.RecursoNaoEncontradoException;

@Service
public class CardapioService {
    private CardapioRepository cardapioRepository;

    @Autowired
    public CardapioService(CardapioRepository cardapioRepository){
        this.cardapioRepository = cardapioRepository;
    }

    public Cardapio recuperaCardapio(long Id){
        Cardapio cardapio = cardapioRepository.recuperaPorId(Id);
        if (cardapio == null) {
            throw new RecursoNaoEncontradoException("Cardapio inexistente: " + Id);
        }
        return cardapio;
    }

    public List<CabecalhoCardapio> recuperaListaDeCardapios(){
        return cardapioRepository.cardapiosDisponiveis();
    }

    public List<Produto> recuperaSugestoesDoChef(){
        return cardapioRepository.indicacoesDoChef();
    }

    public void defineCardapioCorrente(long id){
        if (cardapioRepository.recuperaPorId(id) == null) {
            throw new IllegalArgumentException("Cardapio inexistente: " + id);
        }
        cardapioRepository.defineCorrente(id);
    }

    public Cardapio recuperaCardapioCorrente(){
        Cardapio corrente = cardapioRepository.recuperaCorrente();
        if (corrente == null) {
            throw new IllegalStateException("Nenhum cardapio corrente definido");
        }
        return corrente;
    }
}
