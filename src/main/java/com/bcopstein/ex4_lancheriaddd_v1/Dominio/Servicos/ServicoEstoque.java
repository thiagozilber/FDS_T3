package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.ItensEstoqueRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Ingrediente;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Excecoes.EstoqueInsuficienteException;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.ItemEstoque;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.ItemPedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Produto;

// Servico de estoque: disponibilidade DERIVADA do estoque (sem flag persistida) e baixa.
// Modelo: 1 unidade de cada ingrediente listado na receita por unidade do produto (a receita
// nao carrega quantidade por ingrediente). Ver decisao D11 do plano.
@Service
public class ServicoEstoque {
    private final ItensEstoqueRepository itensEstoqueRepository;

    @Autowired
    public ServicoEstoque(ItensEstoqueRepository itensEstoqueRepository) {
        this.itensEstoqueRepository = itensEstoqueRepository;
    }

    private Map<Long, Integer> requeridoPorIngrediente(List<ItemPedido> itens) {
        Map<Long, Integer> requerido = new HashMap<>();
        for (ItemPedido item : itens) {
            for (Ingrediente ing : item.getItem().getReceita().getIngredientes()) {
                requerido.merge(ing.getId(), item.getQuantidade(), Integer::sum);
            }
        }
        return requerido;
    }

    private Map<Long, Integer> estoqueAtual() {
        Map<Long, Integer> estoque = new HashMap<>();
        for (ItemEstoque item : itensEstoqueRepository.recuperaTodos()) {
            estoque.put(item.getIngrediente().getId(), item.getQuantidade());
        }
        return estoque;
    }

    // Produtos do pedido que nao podem ser atendidos por falta de algum ingrediente (ordenados por id).
    public List<Produto> itensIndisponiveis(List<ItemPedido> itens) {
        Map<Long, Integer> requerido = requeridoPorIngrediente(itens);
        Map<Long, Integer> estoque = estoqueAtual();
        Map<Long, Produto> indisponiveis = new TreeMap<>(); // TreeMap por id -> ordenado + sem duplicatas
        for (ItemPedido item : itens) {
            Produto produto = item.getItem();
            for (Ingrediente ing : produto.getReceita().getIngredientes()) {
                if (requerido.getOrDefault(ing.getId(), 0) > estoque.getOrDefault(ing.getId(), 0)) {
                    indisponiveis.put(produto.getId(), produto);
                    break;
                }
            }
        }
        return new ArrayList<>(indisponiveis.values());
    }

    public boolean haDisponibilidade(List<ItemPedido> itens) {
        return itensIndisponiveis(itens).isEmpty();
    }

    // Baixa o estoque dos ingredientes consumidos via baixa ATOMICA e condicional por ingrediente
    // (UPDATE ... WHERE quantidade >= ?), eliminando a corrida check-then-act. Se algum ingrediente
    // nao tiver saldo (corrida com outro pedido concorrente), lanca EstoqueInsuficienteException —
    // a transacao do pedido (ServicoPedido.submeter @Transactional) sofre rollback, sem baixa parcial.
    public void baixaEstoque(List<ItemPedido> itens) {
        Map<Long, Integer> requerido = requeridoPorIngrediente(itens);
        for (Map.Entry<Long, Integer> e : requerido.entrySet()) {
            if (!itensEstoqueRepository.baixaSeDisponivel(e.getKey(), e.getValue())) {
                throw new EstoqueInsuficienteException(
                    "Estoque insuficiente para o ingrediente " + e.getKey());
            }
        }
    }

    // Devolve ao estoque os ingredientes consumidos por um pedido (UC8: cancelamento de pedido aprovado).
    public void devolveEstoque(List<ItemPedido> itens) {
        Map<Long, Integer> requerido = requeridoPorIngrediente(itens);
        for (Map.Entry<Long, Integer> e : requerido.entrySet()) {
            itensEstoqueRepository.devolve(e.getKey(), e.getValue());
        }
    }
}
