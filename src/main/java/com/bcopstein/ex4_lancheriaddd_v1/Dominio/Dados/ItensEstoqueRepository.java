package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados;

import java.util.List;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.ItemEstoque;

// Porta do estoque de ingredientes. Leitura de todos os itens e atualizacao de quantidade.
public interface ItensEstoqueRepository {
    List<ItemEstoque> recuperaTodos();
    void defineQuantidade(long ingredienteId, int novaQuantidade);
}
