package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.ItensEstoqueRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Ingrediente;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.ItemEstoque;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.ItemPedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Produto;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Receita;

/*
 * Casos de teste -- ServicoEstoque (disponibilidade DERIVADA do estoque + baixa):
 *  1. pedidoComEstoqueSuficienteDisponivel : todos ingredientes com estoque -> haDisponibilidade true, indisponiveis vazio
 *  2. pedidoSemEstoqueIndisponivel         : 1 ingrediente zerado -> produto correspondente em itensIndisponiveis
 *  3. baixaEstoqueDecrementaQuantidades    : baixaEstoque reduz a quantidade de cada ingrediente consumido
 *  4. quantidadeMultiplaSomaConsumo        : quantidade 2 do produto requer 2 de cada ingrediente (estoque 1 -> indisponivel)
 */
class ServicoEstoqueTest {

    // Fake in-memory do port ItensEstoqueRepository (sem Mockito; cultura POJO do projeto).
    private static class FakeItensEstoqueRepository implements ItensEstoqueRepository {
        private final Map<Long, Integer> estoque = new HashMap<>();
        FakeItensEstoqueRepository(Map<Long, Integer> inicial) { estoque.putAll(inicial); }
        @Override public List<ItemEstoque> recuperaTodos() {
            List<ItemEstoque> itens = new ArrayList<>();
            for (Map.Entry<Long, Integer> e : estoque.entrySet()) {
                itens.add(new ItemEstoque(new Ingrediente(e.getKey(), "ing" + e.getKey()), e.getValue()));
            }
            return itens;
        }
        @Override public void defineQuantidade(long ingredienteId, int novaQuantidade) {
            estoque.put(ingredienteId, novaQuantidade);
        }
        int qtd(long ingredienteId) { return estoque.getOrDefault(ingredienteId, 0); }
    }

    // Produto (id 1) cuja receita usa ingredientes 1, 2 e 3 (1 unidade de cada por unidade do produto).
    private Produto produtoCalabresa() {
        Receita receita = new Receita(1L, "Pizza calabresa",
            List.of(new Ingrediente(1L, "Disco"), new Ingrediente(2L, "Molho"), new Ingrediente(3L, "Mussarela")));
        return new Produto(1L, "Pizza calabresa", receita, 5500);
    }

    private Map<Long, Integer> estoqueUniforme(int quantidade) {
        Map<Long, Integer> m = new HashMap<>();
        m.put(1L, quantidade);
        m.put(2L, quantidade);
        m.put(3L, quantidade);
        return m;
    }

    @Test
    void pedidoComEstoqueSuficienteDisponivel() {
        ServicoEstoque servico = new ServicoEstoque(new FakeItensEstoqueRepository(estoqueUniforme(30)));
        List<ItemPedido> itens = List.of(new ItemPedido(produtoCalabresa(), 1));
        assertTrue(servico.haDisponibilidade(itens));
        assertTrue(servico.itensIndisponiveis(itens).isEmpty());
    }

    @Test
    void pedidoSemEstoqueIndisponivel() {
        Map<Long, Integer> estoque = estoqueUniforme(30);
        estoque.put(2L, 0); // ingrediente 2 zerado
        ServicoEstoque servico = new ServicoEstoque(new FakeItensEstoqueRepository(estoque));
        List<ItemPedido> itens = List.of(new ItemPedido(produtoCalabresa(), 1));
        assertFalse(servico.haDisponibilidade(itens));
        List<Produto> indisponiveis = servico.itensIndisponiveis(itens);
        assertEquals(1, indisponiveis.size());
        assertEquals(1L, indisponiveis.get(0).getId());
    }

    @Test
    void baixaEstoqueDecrementaQuantidades() {
        FakeItensEstoqueRepository repo = new FakeItensEstoqueRepository(estoqueUniforme(30));
        ServicoEstoque servico = new ServicoEstoque(repo);
        List<ItemPedido> itens = List.of(new ItemPedido(produtoCalabresa(), 2));
        servico.baixaEstoque(itens);
        assertEquals(28, repo.qtd(1L));
        assertEquals(28, repo.qtd(2L));
        assertEquals(28, repo.qtd(3L));
    }

    @Test
    void quantidadeMultiplaSomaConsumo() {
        ServicoEstoque servico = new ServicoEstoque(new FakeItensEstoqueRepository(estoqueUniforme(1)));
        List<ItemPedido> itens = List.of(new ItemPedido(produtoCalabresa(), 2)); // requer 2 de cada
        assertFalse(servico.haDisponibilidade(itens));
    }
}
