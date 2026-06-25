package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Config.ImpostoProperties;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.DescontoRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.HistoricoStatusRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.ItensEstoqueRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.PedidoRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Cliente;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Ingrediente;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.ItemEstoque;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.ItemPedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Produto;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Receita;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Excecoes.RecursoNaoEncontradoException;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto.FabricaEstrategiaDesconto;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto.FidelidadeFrequente;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto.PromocaoVerao;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto.SemDesconto;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Imposto.FabricaEstrategiaImposto;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Imposto.Lei0412de2022;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Imposto.Lei5762de2026;

/*
 * Casos de teste -- ServicoPedido (submeter UC6, cancelar UC8, transicoes/historico, fidelidade):
 *  1. submeterAprovadoSemDesconto      : 1xcalabresa(55) + 2xmargherita(40), SemDesconto -> APROVADO, valor 135, imposto 13.5, cobrado 148.5
 *  2. submeterComPromocaoVerao         : mesma cesta, PromocaoVerao(5%) -> desconto 6.75, cobrado 141.75
 *  3. fidelidadeAcimaDoLimiteAplica7   : Fidelidade7, contagem 4 (>3) -> desconto 9.45, cobrado 139.05
 *  4. fidelidadeNoLimiteNaoAplica      : Fidelidade7, contagem 3 (== limite) -> desconto 0.0
 *  5. submeterSemEstoqueRecusa         : ingrediente zerado -> RECUSADO, valores zerados, estoque NAO baixado
 *  6. submeterCarrinhoVazioLanca       : itens vazios -> IllegalArgumentException
 *  7. submeterEnderecoVazioLanca       : endereco em branco -> IllegalArgumentException
 *  8. aprovadoBaixaEstoque             : apos APROVADO, estoque de cada ingrediente e decrementado
 *  9. historicoRegistraNovoEAprovado   : submeter aprovado -> historico [NOVO, APROVADO]
 * 10. cancelarApenasAprovado           : cancelar pedido PAGO -> IllegalArgumentException, status permanece PAGO
 * 11. cancelarInexistenteLanca         : cancelar(999) -> RecursoNaoEncontradoException
 * 12. pagoCarimbaDataHoraPagamento     : registrarTransicao(PAGO) -> dataHoraPagamento != null + historico inclui PAGO
 * 13. cancelarAprovadoDevolveEstoque   : cancelar APROVADO -> CANCELADO + estoque devolvido + historico inclui CANCELADO
 * 14. listarEntreguesDatasNulasLanca   : ini ou fim null -> IllegalArgumentException
 * 15. listarEntreguesIniAposFimLanca   : ini > fim -> IllegalArgumentException
 * 16. listarEntreguesDelegaAoRepo      : janela valida -> devolve o que o PedidoRepository retornou
 * (a semantica da janela [ini, fim) e do JOIN ENTREGUE e verificada no driver de integracao
 *  PedidoRepositoryEntreguesTest, que exercita o SQL real -- aqui o fake nao reimplementa o filtro.)
 */
class ServicoPedidoTest {
    private static final double DELTA = 1e-9;

    // ----- Fakes in-memory dos ports (sem Mockito) -----
    private static class FakePedidoRepository implements PedidoRepository {
        private final Map<Long, Pedido> store = new HashMap<>();
        private final Map<Long, Pedido.Status> statusAtual = new HashMap<>();
        private final Map<Long, LocalDateTime> pagamentos = new HashMap<>();
        private long seq = 0;
        int contagemPagos = 0;
        List<PedidoEntregue> entreguesResult = new ArrayList<>(); // seedavel para o teste de delegacao (UC10)

        @Override public long salvar(Pedido pedido) {
            long id = ++seq;
            store.put(id, pedido);
            statusAtual.put(id, pedido.getStatus());
            return id;
        }
        @Override public Pedido recuperaPorId(long id) {
            Pedido base = store.get(id);
            if (base == null) return null;
            return new Pedido(id, base.getCliente(), pagamentos.get(id), base.getItens(),
                statusAtual.get(id), base.getValor(), base.getImpostos(), base.getDesconto(),
                base.getValorCobrado(), base.getEnderecoEntrega());
        }
        @Override public void atualizaStatus(long id, Pedido.Status novo) { statusAtual.put(id, novo); }
        @Override public void atualizaDataHoraPagamento(long id, LocalDateTime quando) { pagamentos.put(id, quando); }
        @Override public int contarPedidosPagosCliente(String cpf, LocalDateTime desde) { return contagemPagos; }
        // Delegacao pura: devolve o conjunto semeado (o filtro real da janela esta no SQL, coberto pela IT).
        @Override public List<PedidoEntregue> entreguesEntre(LocalDateTime ini, LocalDateTime fim) { return entreguesResult; }
    }

    private static class FakeHistoricoStatusRepository implements HistoricoStatusRepository {
        private final Map<Long, List<TransicaoStatus>> hist = new HashMap<>();
        @Override public void registrar(long pedidoId, Pedido.Status status, LocalDateTime quando) {
            hist.computeIfAbsent(pedidoId, k -> new ArrayList<>()).add(new TransicaoStatus(status, quando));
        }
        @Override public List<TransicaoStatus> historico(long pedidoId) {
            return hist.getOrDefault(pedidoId, List.of());
        }
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
        int qtd(long ingredienteId) { return estoque.getOrDefault(ingredienteId, 0); }
    }

    private static class FakeDescontoRepository implements DescontoRepository {
        private String corrente;
        FakeDescontoRepository(String inicial) { this.corrente = inicial; }
        @Override public String politicaCorrente() { return corrente; }
        @Override public void definePolitica(String codigo) { this.corrente = codigo; }
    }

    // ----- Builders -----
    private ServicoImposto servicoImpostoReal() {
        ImpostoProperties props = new ImpostoProperties();
        props.setLeiVigente("0412/2022"); // 10%
        return new ServicoImposto(new FabricaEstrategiaImposto(
            List.of(new Lei0412de2022(), new Lei5762de2026()), props));
    }

    private ServicoDesconto servicoDescontoReal(String politica) {
        return new ServicoDesconto(new FabricaEstrategiaDesconto(
            List.of(new SemDesconto(), new FidelidadeFrequente(), new PromocaoVerao())),
            new FakeDescontoRepository(politica));
    }

    private Produto produto(long id, String descricao, int precoCentavos, long... ingredienteIds) {
        List<Ingrediente> ingredientes = new ArrayList<>();
        for (long ingId : ingredienteIds) {
            ingredientes.add(new Ingrediente(ingId, "ing" + ingId));
        }
        return new Produto(id, descricao, new Receita(id, "receita" + id, ingredientes), precoCentavos);
    }

    private Map<Long, Integer> estoqueCheio() {
        Map<Long, Integer> m = new HashMap<>();
        for (long i = 1; i <= 4; i++) m.put(i, 100);
        return m;
    }

    private List<ItemPedido> cestaPadrao() {
        Produto calabresa = produto(1L, "Pizza calabresa", 5500, 1L, 2L, 3L);
        Produto margherita = produto(3L, "Pizza margherita", 4000, 1L, 2L, 4L);
        return List.of(new ItemPedido(calabresa, 1), new ItemPedido(margherita, 2));
    }

    private final Cliente cliente = new Cliente("9001", "Huguinho", "5199", "Rua A", "h@e.com");

    // Campos para inspecao apos montar()
    private FakeItensEstoqueRepository repoEstoque;
    private FakeHistoricoStatusRepository repoHistorico;
    private FakePedidoRepository repoPedido;

    private ServicoPedido montar(String politica, int contagemPagos, Map<Long, Integer> stock) {
        repoEstoque = new FakeItensEstoqueRepository(stock);
        repoHistorico = new FakeHistoricoStatusRepository();
        repoPedido = new FakePedidoRepository();
        repoPedido.contagemPagos = contagemPagos;
        ServicoEstoque servicoEstoque = new ServicoEstoque(repoEstoque);
        return new ServicoPedido(repoPedido, repoHistorico, servicoEstoque,
            servicoImpostoReal(), servicoDescontoReal(politica));
    }

    @Test
    void submeterAprovadoSemDesconto() {
        ServicoPedido servico = montar("SemDesconto", 0, estoqueCheio());
        Pedido pedido = servico.submeter(cliente, "Rua X, 10", cestaPadrao());
        assertEquals(Pedido.Status.APROVADO, pedido.getStatus());
        assertEquals(135.0, pedido.getValor(), DELTA);
        assertEquals(0.0, pedido.getDesconto(), DELTA);
        assertEquals(13.5, pedido.getImpostos(), DELTA);
        assertEquals(148.5, pedido.getValorCobrado(), DELTA);
    }

    @Test
    void submeterComPromocaoVerao() {
        ServicoPedido servico = montar("PromocaoVerao", 0, estoqueCheio());
        Pedido pedido = servico.submeter(cliente, "Rua X, 10", cestaPadrao());
        assertEquals(6.75, pedido.getDesconto(), DELTA);
        assertEquals(13.5, pedido.getImpostos(), DELTA);
        assertEquals(141.75, pedido.getValorCobrado(), DELTA);
    }

    @Test
    void fidelidadeAcimaDoLimiteAplica7() {
        ServicoPedido servico = montar("Fidelidade7", 4, estoqueCheio());
        Pedido pedido = servico.submeter(cliente, "Rua X, 10", cestaPadrao());
        assertEquals(9.45, pedido.getDesconto(), DELTA);
        assertEquals(139.05, pedido.getValorCobrado(), DELTA);
    }

    @Test
    void fidelidadeNoLimiteNaoAplica() {
        ServicoPedido servico = montar("Fidelidade7", 3, estoqueCheio());
        Pedido pedido = servico.submeter(cliente, "Rua X, 10", cestaPadrao());
        assertEquals(0.0, pedido.getDesconto(), DELTA);
        assertEquals(148.5, pedido.getValorCobrado(), DELTA);
    }

    @Test
    void submeterSemEstoqueRecusa() {
        Map<Long, Integer> stock = estoqueCheio();
        stock.put(2L, 0); // ingrediente 2 zerado -> calabresa indisponivel
        ServicoPedido servico = montar("SemDesconto", 0, stock);
        Produto calabresa = produto(1L, "Pizza calabresa", 5500, 1L, 2L, 3L);
        Pedido pedido = servico.submeter(cliente, "Rua X, 10", List.of(new ItemPedido(calabresa, 1)));
        assertEquals(Pedido.Status.RECUSADO, pedido.getStatus());
        assertEquals(0.0, pedido.getValorCobrado(), DELTA);
        assertEquals(100, repoEstoque.qtd(1L)); // estoque NAO foi baixado
    }

    @Test
    void submeterCarrinhoVazioLanca() {
        ServicoPedido servico = montar("SemDesconto", 0, estoqueCheio());
        assertThrows(IllegalArgumentException.class,
            () -> servico.submeter(cliente, "Rua X, 10", List.of()));
    }

    @Test
    void submeterEnderecoVazioLanca() {
        ServicoPedido servico = montar("SemDesconto", 0, estoqueCheio());
        assertThrows(IllegalArgumentException.class,
            () -> servico.submeter(cliente, "   ", cestaPadrao()));
    }

    @Test
    void aprovadoBaixaEstoque() {
        ServicoPedido servico = montar("SemDesconto", 0, estoqueCheio());
        servico.submeter(cliente, "Rua X, 10", cestaPadrao());
        // ingrediente 1 usado por 1 calabresa + 2 margheritas = 3 unidades
        assertEquals(97, repoEstoque.qtd(1L));
        assertEquals(97, repoEstoque.qtd(2L));
        assertEquals(99, repoEstoque.qtd(3L)); // so a calabresa usa o 3
        assertEquals(98, repoEstoque.qtd(4L)); // so as 2 margheritas usam o 4
    }

    @Test
    void historicoRegistraNovoEAprovado() {
        ServicoPedido servico = montar("SemDesconto", 0, estoqueCheio());
        Pedido pedido = servico.submeter(cliente, "Rua X, 10", cestaPadrao());
        List<TransicaoStatus> historico = repoHistorico.historico(pedido.getId());
        assertEquals(2, historico.size());
        assertEquals(Pedido.Status.NOVO, historico.get(0).status());
        assertEquals(Pedido.Status.APROVADO, historico.get(1).status());
    }

    @Test
    void cancelarApenasAprovado() {
        ServicoPedido servico = montar("SemDesconto", 0, estoqueCheio());
        Pedido pedido = servico.submeter(cliente, "Rua X, 10", cestaPadrao());
        servico.registrarTransicao(pedido.getId(), Pedido.Status.PAGO);
        assertThrows(IllegalArgumentException.class, () -> servico.cancelar(pedido.getId()));
        assertEquals(Pedido.Status.PAGO, servico.recuperaPorId(pedido.getId()).getStatus());
    }

    @Test
    void cancelarInexistenteLanca() {
        ServicoPedido servico = montar("SemDesconto", 0, estoqueCheio());
        assertThrows(RecursoNaoEncontradoException.class, () -> servico.cancelar(999L));
    }

    @Test
    void pagoCarimbaDataHoraPagamento() {
        ServicoPedido servico = montar("SemDesconto", 0, estoqueCheio());
        Pedido pedido = servico.submeter(cliente, "Rua X, 10", cestaPadrao());
        servico.registrarTransicao(pedido.getId(), Pedido.Status.PAGO);
        assertNotNull(servico.recuperaPorId(pedido.getId()).getDataHoraPagamento());
        List<TransicaoStatus> historico = repoHistorico.historico(pedido.getId());
        assertEquals(Pedido.Status.PAGO, historico.get(historico.size() - 1).status());
    }

    @Test
    void cancelarAprovadoDevolveEstoque() {
        ServicoPedido servico = montar("SemDesconto", 0, estoqueCheio());
        Pedido pedido = servico.submeter(cliente, "Rua X, 10", cestaPadrao());
        // baixa na aprovacao: ing1=97, ing2=97, ing3=99, ing4=98
        servico.cancelar(pedido.getId());
        assertEquals(Pedido.Status.CANCELADO, servico.recuperaPorId(pedido.getId()).getStatus());
        // devolucao no cancelamento: o estoque volta ao original
        assertEquals(100, repoEstoque.qtd(1L));
        assertEquals(100, repoEstoque.qtd(2L));
        assertEquals(100, repoEstoque.qtd(3L));
        assertEquals(100, repoEstoque.qtd(4L));
        List<TransicaoStatus> historico = repoHistorico.historico(pedido.getId());
        assertEquals(Pedido.Status.CANCELADO, historico.get(historico.size() - 1).status());
    }

    // ----- UC10: listarEntreguesEntre -----

    @Test
    void listarEntreguesDatasNulasLanca() {
        ServicoPedido servico = montar("SemDesconto", 0, estoqueCheio());
        LocalDateTime agora = LocalDateTime.now();
        assertThrows(IllegalArgumentException.class, () -> servico.listarEntreguesEntre(null, agora));
        assertThrows(IllegalArgumentException.class, () -> servico.listarEntreguesEntre(agora, null));
    }

    @Test
    void listarEntreguesIniAposFimLanca() {
        ServicoPedido servico = montar("SemDesconto", 0, estoqueCheio());
        LocalDateTime ini = LocalDateTime.of(2026, 6, 30, 0, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 6, 1, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> servico.listarEntreguesEntre(ini, fim));
    }

    @Test
    void listarEntreguesDelegaAoRepo() {
        ServicoPedido servico = montar("SemDesconto", 0, estoqueCheio());
        LocalDateTime entrega = LocalDateTime.of(2026, 6, 15, 19, 42);
        Pedido entregue = new Pedido(7L, cliente, null, cestaPadrao(), Pedido.Status.ENTREGUE,
            135.0, 13.5, 0.0, 148.5, "Rua X, 10");
        repoPedido.entreguesResult = List.of(new PedidoEntregue(entregue, entrega));

        List<PedidoEntregue> resultado = servico.listarEntreguesEntre(
            LocalDateTime.of(2026, 6, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0));

        assertEquals(1, resultado.size());
        assertEquals(7L, resultado.get(0).pedido().getId());
        assertEquals(Pedido.Status.ENTREGUE, resultado.get(0).pedido().getStatus());
        assertEquals(entrega, resultado.get(0).dataHoraEntrega());
    }
}
