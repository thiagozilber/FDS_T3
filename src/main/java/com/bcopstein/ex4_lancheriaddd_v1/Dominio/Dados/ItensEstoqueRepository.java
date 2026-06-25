package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados;

import java.util.List;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.ItemEstoque;

// Porta do estoque de ingredientes. Leitura de todos os itens, baixa atomica condicional e devolucao.
public interface ItensEstoqueRepository {
    List<ItemEstoque> recuperaTodos();

    // Baixa atomica e condicional: decrementa a quantidade SOMENTE se houver saldo suficiente.
    // Retorna true se baixou; false se o saldo era insuficiente (linha inalterada). Elimina a
    // corrida check-then-act ao mover a verificacao para a propria operacao de UPDATE.
    boolean baixaSeDisponivel(long ingredienteId, int quantidade);

    // Devolve (incrementa) o saldo do ingrediente. Usada no cancelamento de pedido aprovado (UC8).
    void devolve(long ingredienteId, int quantidade);
}
