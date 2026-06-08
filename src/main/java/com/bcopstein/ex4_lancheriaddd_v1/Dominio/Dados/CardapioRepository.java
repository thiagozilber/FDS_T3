package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados;

import java.util.List;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.CabecalhoCardapio;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Cardapio;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Produto;

public interface CardapioRepository {
    List<CabecalhoCardapio> cardapiosDisponiveis();
    Cardapio recuperaPorId(long id);
    List<Produto> indicacoesDoChef();
}
