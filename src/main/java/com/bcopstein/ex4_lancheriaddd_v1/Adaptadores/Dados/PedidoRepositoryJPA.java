package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados.Jpa.HistoricoStatusJpaEntity;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.PedidoRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.PedidoEntregue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

// Implementacao JPA da porta PedidoRepository (Approach A — Seam #3/OQ#2 G1): persiste/recupera a
// PROPRIA entidade de dominio Pedido (anotada com JPA), sem modelo de persistencia separado. O
// agregado (Pedido + itens_pedido) e gerenciado pelo ORM via @OneToMany(cascade); Produto e
// re-hidratado pela associacao @ManyToOne de ItemPedido (Produto e entidade JPA da Pessoa 2).
@Repository
public class PedidoRepositoryJPA implements PedidoRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public long salvar(Pedido pedido) {
        // persist em cascata grava pedidos + itens_pedido (back-refs ligados no construtor de Pedido).
        this.entityManager.persist(pedido);
        // flush: materializa o id (IDENTITY) e revela violacao de FK (ex.: cliente_cpf inexistente)
        // como DataIntegrityViolationException (traduzida pelo @Repository) -> 400, como no JDBC.
        this.entityManager.flush();
        return pedido.getId();
    }

    @Override
    public Pedido recuperaPorId(long id) {
        // find retorna a entidade de dominio gerenciada (itens e cliente EAGER); null preserva o 404.
        return this.entityManager.find(Pedido.class, id);
    }

    @Override
    @Transactional
    public void atualizaStatus(long id, Pedido.Status novo) {
        // Load-mutate (update gerenciado), NAO bulk update: mantem a entidade gerenciada coerente
        // para que um recuperaPorId posterior na mesma transacao enxergue o novo status.
        Pedido pedido = this.entityManager.find(Pedido.class, id);
        if (pedido != null) {
            pedido.setStatus(novo);
        }
    }

    @Override
    @Transactional
    public void atualizaDataHoraPagamento(long id, LocalDateTime quando) {
        Pedido pedido = this.entityManager.find(Pedido.class, id);
        if (pedido != null) {
            pedido.setDataHoraPagamento(quando);
        }
    }

    @Override
    public int contarPedidosPagosCliente(String cpf, LocalDateTime desde) {
        Long count = this.entityManager.createQuery(
                "SELECT COUNT(p) FROM Pedido p " +
                "WHERE p.cliente.cpf = :cpf " +
                "AND p.dataHoraPagamento IS NOT NULL AND p.dataHoraPagamento >= :desde",
                Long.class)
            .setParameter("cpf", cpf)
            .setParameter("desde", desde)
            .getSingleResult();
        return count == null ? 0 : count.intValue();
    }

    // UC10 (Seam #4 / D15): pedidos cuja transicao ENTREGUE caiu em [ini, fim). Filtra pelo carimbo
    // em historico_status; janela meio-aberta; ordenado por data_hora e id. Um pedido tem no maximo
    // uma linha ENTREGUE (estado terminal), logo nao precisa DISTINCT.
    @Override
    public List<PedidoEntregue> entreguesEntre(LocalDateTime ini, LocalDateTime fim) {
        List<Object[]> linhas = this.entityManager.createQuery(
                "SELECT p, h.dataHora FROM Pedido p, HistoricoStatusJpaEntity h " +
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
            Pedido pedido = (Pedido) linha[0];
            LocalDateTime dataHoraEntrega = (LocalDateTime) linha[1];
            entregues.add(new PedidoEntregue(pedido, dataHoraEntrega));
        }
        return entregues;
    }
}
