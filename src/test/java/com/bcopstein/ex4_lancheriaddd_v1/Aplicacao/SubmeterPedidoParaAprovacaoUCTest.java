package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Config.ImpostoProperties;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Requests.ItemPedidoRequest;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Requests.SubmeterPedidoRequest;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses.SubmeterPedidoResponse;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.DescontoRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.HistoricoStatusRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.ItensEstoqueRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.PedidoRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.ProdutosRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Ingrediente;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.ItemEstoque;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Produto;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Receita;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.ServicoDesconto;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.ServicoEstoque;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.ServicoImposto;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.ServicoPedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.TransicaoStatus;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto.FabricaEstrategiaDesconto;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto.FidelidadeFrequente;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto.PromocaoVerao;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto.SemDesconto;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Imposto.FabricaEstrategiaImposto;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Imposto.Lei0412de2022;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Imposto.Lei5762de2026;

/*
 * Casos de teste -- SubmeterPedidoParaAprovacaoUC (UC6, alvo de teste P6):
 *  1. submeterAprovadoRetornaTotais     : cesta valida com estoque -> APROVADO, valor 135, cobrado 148.5, itensIndisponiveis vazio
 *  2. submeterProdutoInexistenteLanca   : produtoId 999 -> IllegalArgumentException
 *  3. submeterCarrinhoVazioLanca        : itens vazios -> IllegalArgumentException
 *  4. submeterSemEstoqueRetornaRecusado : ingrediente zerado -> RECUSADO + itensIndisponiveis com a descricao
 *  5. quantidadeInvalidaLanca           : quantidade 0 -> IllegalArgumentException
 */
class SubmeterPedidoParaAprovacaoUCTest {
    private static final double DELTA = 1e-9;

    private static class FakePedidoRepository implements PedidoRepository {
        private final Map<Long, Pedido> store = new HashMap<>();
        private final Map<Long, Pedido.Status> statusAtual = new HashMap<>();
        private long seq = 0;
        @Override public long salvar(Pedido pedido) {
            long id = ++seq;
            store.put(id, pedido);
            statusAtual.put(id, pedido.getStatus());
            return id;
        }
        @Override public Pedido recuperaPorId(long id) {
            Pedido base = store.get(id);
            if (base == null) return null;
            return new Pedido(id, base.getCliente(), null, base.getItens(), statusAtual.get(id),
                base.getValor(), base.getImpostos(), base.getDesconto(), base.getValorCobrado(),
                base.getEnderecoEntrega());
        }
        @Override public void atualizaStatus(long id, Pedido.Status novo) { statusAtual.put(id, novo); }
        @Override public void atualizaDataHoraPagamento(long id, LocalDateTime quando) { }
        @Override public int contarPedidosPagosCliente(String cpf, LocalDateTime desde) { return 0; }
        @Override public java.util.List<com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.PedidoEntregue>
            entreguesEntre(LocalDateTime ini, LocalDateTime fim) { return java.util.List.of(); }
    }

    private static class FakeHistoricoStatusRepository implements HistoricoStatusRepository {
        @Override public void registrar(long pedidoId, Pedido.Status status, LocalDateTime quando) { }
        @Override public List<TransicaoStatus> historico(long pedidoId) { return List.of(); }
    }

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
        @Override public boolean baixaSeDisponivel(long ingredienteId, int quantidade) {
            int atual = estoque.getOrDefault(ingredienteId, 0);
            if (atual < quantidade) return false;
            estoque.put(ingredienteId, atual - quantidade);
            return true;
        }
        @Override public void devolve(long ingredienteId, int quantidade) {
            estoque.merge(ingredienteId, quantidade, Integer::sum);
        }
    }

    private static class FakeProdutosRepository implements ProdutosRepository {
        private final Map<Long, Produto> produtos = new HashMap<>();
        FakeProdutosRepository() {
            produtos.put(1L, produto(1L, "Pizza calabresa", 5500, 1L, 2L, 3L));
            produtos.put(3L, produto(3L, "Pizza margherita", 4000, 1L, 2L, 4L));
        }
        @Override public Produto recuperaProdutoPorid(long id) { return produtos.get(id); }
        @Override public List<Produto> recuperaProdutosCardapio(long id) { return new ArrayList<>(produtos.values()); }
    }

    private static class FakeDescontoRepository implements DescontoRepository {
        private String corrente;
        FakeDescontoRepository(String inicial) { this.corrente = inicial; }
        @Override public String politicaCorrente() { return corrente; }
        @Override public void definePolitica(String codigo) { this.corrente = codigo; }
    }

    private static Produto produto(long id, String descricao, int precoCentavos, long... ingredienteIds) {
        List<Ingrediente> ingredientes = new ArrayList<>();
        for (long ingId : ingredienteIds) {
            ingredientes.add(new Ingrediente(ingId, "ing" + ingId));
        }
        return new Produto(id, descricao, new Receita(id, "receita" + id, ingredientes), precoCentavos);
    }

    private ImpostoProperties impostoProps() {
        ImpostoProperties props = new ImpostoProperties();
        props.setLeiVigente("0412/2022");
        return props;
    }

    private SubmeterPedidoParaAprovacaoUC montar(Map<Long, Integer> stock) {
        ServicoImposto servicoImposto = new ServicoImposto(new FabricaEstrategiaImposto(
            List.of(new Lei0412de2022(), new Lei5762de2026()), impostoProps()));
        ServicoDesconto servicoDesconto = new ServicoDesconto(new FabricaEstrategiaDesconto(
            List.of(new SemDesconto(), new FidelidadeFrequente(), new PromocaoVerao())),
            new FakeDescontoRepository("SemDesconto"));
        ServicoEstoque servicoEstoque = new ServicoEstoque(new FakeItensEstoqueRepository(stock));
        ServicoPedido servicoPedido = new ServicoPedido(new FakePedidoRepository(),
            new FakeHistoricoStatusRepository(), servicoEstoque, servicoImposto, servicoDesconto);
        return new SubmeterPedidoParaAprovacaoUC(servicoPedido, servicoEstoque, new FakeProdutosRepository());
    }

    private Map<Long, Integer> estoqueCheio() {
        Map<Long, Integer> m = new HashMap<>();
        for (long i = 1; i <= 4; i++) m.put(i, 100);
        return m;
    }

    @Test
    void submeterAprovadoRetornaTotais() {
        SubmeterPedidoParaAprovacaoUC uc = montar(estoqueCheio());
        SubmeterPedidoRequest req = new SubmeterPedidoRequest("9001", "Rua X, 10",
            List.of(new ItemPedidoRequest(1L, 1), new ItemPedidoRequest(3L, 2)));
        SubmeterPedidoResponse resp = uc.run(req);
        assertEquals("APROVADO", resp.status());
        assertEquals(135.0, resp.valor(), DELTA);
        assertEquals(148.5, resp.valorCobrado(), DELTA);
        assertTrue(resp.itensIndisponiveis().isEmpty());
    }

    @Test
    void submeterProdutoInexistenteLanca() {
        SubmeterPedidoParaAprovacaoUC uc = montar(estoqueCheio());
        SubmeterPedidoRequest req = new SubmeterPedidoRequest("9001", "Rua X, 10",
            List.of(new ItemPedidoRequest(999L, 1)));
        assertThrows(IllegalArgumentException.class, () -> uc.run(req));
    }

    @Test
    void submeterCarrinhoVazioLanca() {
        SubmeterPedidoParaAprovacaoUC uc = montar(estoqueCheio());
        SubmeterPedidoRequest req = new SubmeterPedidoRequest("9001", "Rua X, 10", List.of());
        assertThrows(IllegalArgumentException.class, () -> uc.run(req));
    }

    @Test
    void submeterSemEstoqueRetornaRecusado() {
        Map<Long, Integer> stock = estoqueCheio();
        stock.put(2L, 0); // calabresa indisponivel
        SubmeterPedidoParaAprovacaoUC uc = montar(stock);
        SubmeterPedidoRequest req = new SubmeterPedidoRequest("9001", "Rua X, 10",
            List.of(new ItemPedidoRequest(1L, 1)));
        SubmeterPedidoResponse resp = uc.run(req);
        assertEquals("RECUSADO", resp.status());
        assertEquals(List.of("Pizza calabresa"), resp.itensIndisponiveis());
    }

    @Test
    void quantidadeInvalidaLanca() {
        SubmeterPedidoParaAprovacaoUC uc = montar(estoqueCheio());
        SubmeterPedidoRequest req = new SubmeterPedidoRequest("9001", "Rua X, 10",
            List.of(new ItemPedidoRequest(1L, 0)));
        assertThrows(IllegalArgumentException.class, () -> uc.run(req));
    }
}
