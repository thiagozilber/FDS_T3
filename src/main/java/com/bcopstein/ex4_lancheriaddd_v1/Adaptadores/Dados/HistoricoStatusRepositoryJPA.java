package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados.Jpa.HistoricoStatusJpaEntity;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.HistoricoStatusRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.TransicaoStatus;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

// Implementacao JPA da porta HistoricoStatusRepository (Approach B / Seam #3). Escrita feita
// exclusivamente por ServicoPedido (Seam #2). Substitui HistoricoStatusRepositoryJDBC.
@Repository
public class HistoricoStatusRepositoryJPA implements HistoricoStatusRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void registrar(long pedidoId, Pedido.Status status, LocalDateTime quando) {
        this.entityManager.persist(new HistoricoStatusJpaEntity(pedidoId, status, quando));
    }

    @Override
    public List<TransicaoStatus> historico(long pedidoId) {
        // Ordena por id como desempate para manter a ordem de insercao em transicoes no mesmo instante.
        List<HistoricoStatusJpaEntity> linhas = this.entityManager.createQuery(
                "SELECT h FROM HistoricoStatusJpaEntity h WHERE h.pedidoId = :pid " +
                "ORDER BY h.dataHora ASC, h.id ASC", HistoricoStatusJpaEntity.class)
            .setParameter("pid", pedidoId)
            .getResultList();

        List<TransicaoStatus> transicoes = new ArrayList<>();
        for (HistoricoStatusJpaEntity linha : linhas) {
            transicoes.add(new TransicaoStatus(linha.getStatus(), linha.getDataHora()));
        }
        return transicoes;
    }
}
