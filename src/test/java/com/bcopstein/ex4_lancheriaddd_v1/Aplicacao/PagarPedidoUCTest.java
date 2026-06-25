package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Config.ImpostoProperties;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses.PedidoStatusResponse;
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
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Excecoes.PagamentoRecusadoException;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.ICozinhaService;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.IPagamentoService;
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
 * Casos de teste -- PagarPedidoUC (UC9):
 *  1. pagarAprovadoVaiParaAguardando : APROVADO -> pagar -> statusAtual AGUARDANDO, handoff cozinha chamado, pagamento carimbado
 *  2. pagarNaoAprovadoLanca          : pagar pedido ja AGUARDANDO -> IllegalArgumentException
 *  3. pagamentoFalhaLanca            : pagamento retorna false -> PagamentoRecusadoException
 */
class PagarPedidoUCTest {

    private static class FakePedidoRepository implements PedidoRepository {
        private final Map<Long, Pedido> store = new HashMap<>();
        private final Map<Long, Pedido.Status> statusAtual = new HashMap<>();
        private final Map<Long, LocalDateTime> pagamentos = new HashMap<>();
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
            return new Pedido(id, base.getCliente(), pagamentos.get(id), base.getItens(),
                statusAtual.get(id), base.getValor(), base.getImpostos(), base.getDesconto(),
                base.getValorCobrado(), base.getEnderecoEntrega());
        }
        @Override public void atualizaStatus(long id, Pedido.Status novo) { statusAtual.put(id, novo); }
        @Override public void atualizaDataHoraPagamento(long id, LocalDateTime quando) { pagamentos.put(id, quando); }
        @Override public int contarPedidosPagosCliente(String cpf, LocalDateTime desde) { return 0; }
        @Override public java.util.List<com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.PedidoEntregue>
            entreguesEntre(LocalDateTime ini, LocalDateTime fim) { return java.util.List.of(); }
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
        FakeItensEstoqueRepository() { for (long i = 1; i <= 4; i++) estoque.put(i, 100); }
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

    private static class FakeDescontoRepository implements DescontoRepository {
        @Override public String politicaCorrente() { return "SemDesconto"; }
        @Override public void definePolitica(String codigo) { }
    }

    private static class FakeCozinha implements ICozinhaService {
        final List<Long> recebidos = new ArrayList<>();
        @Override public void chegadaDePedido(Pedido p) { recebidos.add(p.getId()); }
        @Override public void pedidoPronto() { }
    }

    private static class FakePagamento implements IPagamentoService {
        private final boolean sucesso;
        FakePagamento(boolean sucesso) { this.sucesso = sucesso; }
        @Override public boolean processarPagamento(Pedido pedido) { return sucesso; }
    }

    private final Cliente cliente = new Cliente("9001", "Huguinho", "5199", "Rua A", "h@e.com");

    private ServicoPedido servicoPedido() {
        ImpostoProperties props = new ImpostoProperties();
        props.setLeiVigente("0412/2022");
        ServicoImposto servicoImposto = new ServicoImposto(new FabricaEstrategiaImposto(
            List.of(new Lei0412de2022(), new Lei5762de2026()), props));
        ServicoDesconto servicoDesconto = new ServicoDesconto(new FabricaEstrategiaDesconto(
            List.of(new SemDesconto(), new FidelidadeFrequente(), new PromocaoVerao())),
            new FakeDescontoRepository());
        ServicoEstoque servicoEstoque = new ServicoEstoque(new FakeItensEstoqueRepository());
        return new ServicoPedido(new FakePedidoRepository(), new FakeHistoricoStatusRepository(),
            servicoEstoque, servicoImposto, servicoDesconto);
    }

    private List<ItemPedido> cesta() {
        Receita r = new Receita(1L, "calabresa", List.of(new Ingrediente(1L, "a"), new Ingrediente(2L, "b")));
        return List.of(new ItemPedido(new Produto(1L, "Pizza calabresa", r, 5500), 1));
    }

    @Test
    void pagarAprovadoVaiParaAguardando() {
        ServicoPedido servicoPedido = servicoPedido();
        FakeCozinha cozinha = new FakeCozinha();
        PagarPedidoUC uc = new PagarPedidoUC(servicoPedido, new FakePagamento(true), cozinha);
        Pedido pedido = servicoPedido.submeter(cliente, "Rua X, 10", cesta());

        PedidoStatusResponse resp = uc.run(pedido.getId());

        assertEquals("AGUARDANDO", resp.statusAtual());
        assertTrue(cozinha.recebidos.contains(pedido.getId()));
        assertNotNull(servicoPedido.recuperaPorId(pedido.getId()).getDataHoraPagamento());
    }

    @Test
    void pagarNaoAprovadoLanca() {
        ServicoPedido servicoPedido = servicoPedido();
        PagarPedidoUC uc = new PagarPedidoUC(servicoPedido, new FakePagamento(true), new FakeCozinha());
        Pedido pedido = servicoPedido.submeter(cliente, "Rua X, 10", cesta());
        uc.run(pedido.getId()); // primeira vez: vai para AGUARDANDO
        assertThrows(IllegalArgumentException.class, () -> uc.run(pedido.getId())); // segunda: nao esta APROVADO
    }

    @Test
    void pagamentoFalhaLanca() {
        ServicoPedido servicoPedido = servicoPedido();
        PagarPedidoUC uc = new PagarPedidoUC(servicoPedido, new FakePagamento(false), new FakeCozinha());
        Pedido pedido = servicoPedido.submeter(cliente, "Rua X, 10", cesta());
        assertThrows(PagamentoRecusadoException.class, () -> uc.run(pedido.getId()));
    }
}
