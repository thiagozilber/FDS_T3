package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.HistoricoStatusRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.PedidoRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Cliente;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.ItemPedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Excecoes.RecursoNaoEncontradoException;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto.ContextoDesconto;

// Servico do ciclo do pedido. UNICO escritor de historico_status (Seam #2): toda transicao passa
// por registrarTransicao(...), que carimba o instante, atualiza pedidos.status e registra o historico.
@Service
public class ServicoPedido implements IRegistradorStatus {
    private static final int JANELA_FIDELIDADE_DIAS = 20;

    private final PedidoRepository pedidoRepository;
    private final HistoricoStatusRepository historicoStatusRepository;
    private final ServicoEstoque servicoEstoque;
    private final ServicoImposto servicoImposto;
    private final ServicoDesconto servicoDesconto;

    @Autowired
    public ServicoPedido(PedidoRepository pedidoRepository,
                         HistoricoStatusRepository historicoStatusRepository,
                         ServicoEstoque servicoEstoque,
                         ServicoImposto servicoImposto,
                         ServicoDesconto servicoDesconto) {
        this.pedidoRepository = pedidoRepository;
        this.historicoStatusRepository = historicoStatusRepository;
        this.servicoEstoque = servicoEstoque;
        this.servicoImposto = servicoImposto;
        this.servicoDesconto = servicoDesconto;
    }

    // Arredonda para centavos (2 casas), evitando ruido de ponto flutuante na moeda persistida.
    private static double arredonda(double v) {
        return Math.round(v * 100) / 100.0;
    }

    // UC6: submete o pedido. Recusa se faltar estoque (NOVO->RECUSADO, sem baixar); senao precifica,
    // baixa estoque e aprova (NOVO->APROVADO). custoFinal = (subtotal - desconto) + imposto (D8).
    // @Transactional: criar o pedido (cabecalho + itens + historico + baixa de estoque) e atomico;
    // qualquer falha (ex.: cliente_cpf inexistente) faz rollback e nao deixa linhas orfas.
    @Transactional
    public Pedido submeter(Cliente cliente, String enderecoEntrega, List<ItemPedido> itens) {
        if (itens == null || itens.isEmpty()) {
            throw new IllegalArgumentException("Pedido sem itens");
        }
        if (enderecoEntrega == null || enderecoEntrega.isBlank()) {
            throw new IllegalArgumentException("Endereco de entrega obrigatorio");
        }

        long subtotalCentavos = 0;
        for (ItemPedido item : itens) {
            subtotalCentavos += (long) item.getItem().getPreco() * item.getQuantidade();
        }
        double subtotal = subtotalCentavos / 100.0;

        if (!servicoEstoque.haDisponibilidade(itens)) {
            Pedido recusado = new Pedido(0L, cliente, null, itens, Pedido.Status.NOVO,
                0.0, 0.0, 0.0, 0.0, enderecoEntrega);
            long id = pedidoRepository.salvar(recusado);
            registrarTransicao(id, Pedido.Status.NOVO);
            registrarTransicao(id, Pedido.Status.RECUSADO);
            return recuperaPorId(id);
        }

        int pedidosRecentes = pedidoRepository.contarPedidosPagosCliente(
            cliente.getCpf(), LocalDateTime.now().minusDays(JANELA_FIDELIDADE_DIAS));
        ContextoDesconto contexto = new ContextoDesconto(pedidosRecentes);

        double desconto = arredonda(servicoDesconto.calcularDesconto(subtotal, contexto));
        double imposto = arredonda(servicoImposto.calcularImposto(subtotal));
        double custoFinal = arredonda((subtotal - desconto) + imposto);

        Pedido pedido = new Pedido(0L, cliente, null, itens, Pedido.Status.NOVO,
            arredonda(subtotal), imposto, desconto, custoFinal, enderecoEntrega);
        long id = pedidoRepository.salvar(pedido);
        registrarTransicao(id, Pedido.Status.NOVO);
        registrarTransicao(id, Pedido.Status.APROVADO);
        servicoEstoque.baixaEstoque(itens);
        return recuperaPorId(id);
    }

    public Pedido recuperaPorId(long id) {
        Pedido pedido = pedidoRepository.recuperaPorId(id);
        if (pedido == null) {
            throw new RecursoNaoEncontradoException("Pedido inexistente: " + id);
        }
        return pedido;
    }

    public List<TransicaoStatus> historico(long id) {
        recuperaPorId(id); // valida existencia (404 se inexistente)
        return historicoStatusRepository.historico(id);
    }

    // UC10: pedidos entregues no intervalo [ini, fim) (somente leitura, sem @Transactional).
    // Recebe a janela ja expandida pela UC; valida apenas a consistencia ini <= fim.
    public List<PedidoEntregue> listarEntreguesEntre(LocalDateTime ini, LocalDateTime fim) {
        if (ini == null || fim == null) {
            throw new IllegalArgumentException("Datas ini e fim sao obrigatorias");
        }
        if (ini.isAfter(fim)) {
            throw new IllegalArgumentException(
                "Data inicial nao pode ser posterior a final: " + ini + " > " + fim);
        }
        return pedidoRepository.entreguesEntre(ini, fim);
    }

    // UC8: cancela apenas pedido APROVADO (e ainda nao pago). @Transactional: a transicao para
    // CANCELADO e a devolucao ao estoque (baixado na aprovacao) sao atomicas.
    @Transactional
    public void cancelar(long id) {
        Pedido pedido = recuperaPorId(id);
        if (pedido.getStatus() != Pedido.Status.APROVADO) {
            throw new IllegalArgumentException(
                "Somente pedido APROVADO e nao pago pode ser cancelado; status atual: " + pedido.getStatus());
        }
        registrarTransicao(id, Pedido.Status.CANCELADO);
        servicoEstoque.devolveEstoque(pedido.getItens());
    }

    // Seam #2: unico ponto de escrita do status/historico. Carimba o instante e, no PAGO, a data de pagamento.
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void registrarTransicao(long pedidoId, Pedido.Status novo) {
        LocalDateTime agora = LocalDateTime.now();
        pedidoRepository.atualizaStatus(pedidoId, novo);
        if (novo == Pedido.Status.PAGO) {
            pedidoRepository.atualizaDataHoraPagamento(pedidoId, agora);
        }
        historicoStatusRepository.registrar(pedidoId, novo, agora);
    }
}
