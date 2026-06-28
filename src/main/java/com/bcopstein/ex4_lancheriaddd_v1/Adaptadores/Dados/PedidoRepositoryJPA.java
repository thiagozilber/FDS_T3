package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados.Jpa.ItemPedidoJpaEntity;
import com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados.Jpa.PedidoJpaEntity;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.PedidoRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.ProdutosRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Cliente;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.ItemPedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Produto;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.PedidoEntregue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

// Implementacao JPA da porta PedidoRepository (Approach B / Seam #3): usa EntityManager sobre os
// modelos de persistencia *JpaEntity, mantendo a entidade de dominio Pedido um POJO puro. O Produto
// (entidade da Pessoa 2, ainda JDBC) e re-hidratado via ProdutosRepository — preservando o
// comportamento do antigo PedidoRepositoryJDBC, agora sem JdbcTemplate.
@Repository
public class PedidoRepositoryJPA implements PedidoRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private final ProdutosRepository produtosRepository;

    public PedidoRepositoryJPA(ProdutosRepository produtosRepository) {
        this.produtosRepository = produtosRepository;
    }

    @Override
    @Transactional
    public long salvar(Pedido pedido) {
        PedidoJpaEntity e = new PedidoJpaEntity();
        e.setClienteCpf(pedido.getCliente().getCpf());
        e.setStatus(pedido.getStatus());
        e.setValor(pedido.getValor());
        e.setImpostos(pedido.getImpostos());
        e.setDesconto(pedido.getDesconto());
        e.setValorCobrado(pedido.getValorCobrado());
        e.setDataHoraPagamento(pedido.getDataHoraPagamento());
        e.setEnderecoEntrega(pedido.getEnderecoEntrega());

        this.entityManager.persist(e);
        // flush: materializa o id (IDENTITY) e revela violacao de FK (ex.: cliente_cpf inexistente)
        // como DataIntegrityViolationException (traduzida pelo @Repository) -> 400, como no JDBC.
        this.entityManager.flush();
        long pedidoId = e.getId();

        for (ItemPedido item : pedido.getItens()) {
            this.entityManager.persist(
                new ItemPedidoJpaEntity(pedidoId, item.getItem().getId(), item.getQuantidade()));
        }
        this.entityManager.flush();
        return pedidoId;
    }

    @Override
    public Pedido recuperaPorId(long id) {
        PedidoJpaEntity e = this.entityManager.find(PedidoJpaEntity.class, id);
        return e == null ? null : toDomain(e); // null preserva o contrato 404 de ServicoPedido
    }

    @Override
    @Transactional
    public void atualizaStatus(long id, Pedido.Status novo) {
        // Load-mutate (update gerenciado), NAO bulk update: mantem a entidade gerenciada coerente
        // para que um recuperaPorId posterior na mesma transacao enxergue o novo status.
        PedidoJpaEntity e = this.entityManager.find(PedidoJpaEntity.class, id);
        if (e != null) {
            e.setStatus(novo);
        }
    }

    @Override
    @Transactional
    public void atualizaDataHoraPagamento(long id, LocalDateTime quando) {
        PedidoJpaEntity e = this.entityManager.find(PedidoJpaEntity.class, id);
        if (e != null) {
            e.setDataHoraPagamento(quando);
        }
    }

    @Override
    public int contarPedidosPagosCliente(String cpf, LocalDateTime desde) {
        Long count = this.entityManager.createQuery(
                "SELECT COUNT(p) FROM PedidoJpaEntity p " +
                "WHERE p.clienteCpf = :cpf " +
                "AND p.dataHoraPagamento IS NOT NULL AND p.dataHoraPagamento >= :desde",
                Long.class)
            .setParameter("cpf", cpf)
            .setParameter("desde", desde)
            .getSingleResult();
        return count == null ? 0 : count.intValue();
    }

    // UC10 (Seam #4 / D15): pedidos cuja transicao ENTREGUE caiu em [ini, fim). Filtra pelo carimbo
    // em historico_status; janela meio-aberta; ordenado por data_hora e id. Um pedido tem no maximo
    // uma linha ENTREGUE (estado terminal), logo nao precisa DISTINCT. Re-hidratacao de itens N+1
    // por linha, igual ao JDBC (aceitavel na escala do trabalho).
    @Override
    public List<PedidoEntregue> entreguesEntre(LocalDateTime ini, LocalDateTime fim) {
        List<Object[]> linhas = this.entityManager.createQuery(
                "SELECT p, h.dataHora FROM PedidoJpaEntity p, HistoricoStatusJpaEntity h " +
                "WHERE h.pedidoId = p.id AND h.status = :st " +
                "AND h.dataHora >= :ini AND h.dataHora < :fim " +
                "ORDER BY h.dataHora ASC, p.id ASC",
                Object[].class)
            .setParameter("st", Pedido.Status.ENTREGUE)
            .setParameter("ini", ini)
            .setParameter("fim", fim)
            .getResultList();

        List<PedidoEntregue> entregues = new ArrayList<>();
        for (Object[] linha : linhas) {
            PedidoJpaEntity e = (PedidoJpaEntity) linha[0];
            LocalDateTime dataHoraEntrega = (LocalDateTime) linha[1];
            entregues.add(new PedidoEntregue(toDomain(e), dataHoraEntrega));
        }
        return entregues;
    }

    // Mapeia o modelo de persistencia -> entidade de dominio, re-hidratando itens e Produto.
    private Pedido toDomain(PedidoJpaEntity e) {
        Cliente cliente = new Cliente(e.getClienteCpf(), null, null, null, null, null);
        List<ItemPedido> itens = recuperaItens(e.getId());
        return new Pedido(
            e.getId(), cliente, e.getDataHoraPagamento(), itens, e.getStatus(),
            e.getValor(), e.getImpostos(), e.getDesconto(), e.getValorCobrado(),
            e.getEnderecoEntrega());
    }

    private List<ItemPedido> recuperaItens(long pedidoId) {
        List<ItemPedidoJpaEntity> linhas = this.entityManager.createQuery(
                "SELECT i FROM ItemPedidoJpaEntity i WHERE i.pedidoId = :pid", ItemPedidoJpaEntity.class)
            .setParameter("pid", pedidoId)
            .getResultList();

        List<ItemPedido> itens = new ArrayList<>();
        for (ItemPedidoJpaEntity linha : linhas) {
            Produto produto = produtosRepository.recuperaProdutoPorid(linha.getProdutoId());
            itens.add(new ItemPedido(produto, linha.getQuantidade()));
        }
        return itens;
    }
}
