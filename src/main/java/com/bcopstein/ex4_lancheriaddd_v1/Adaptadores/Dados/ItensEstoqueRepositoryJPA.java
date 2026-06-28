package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.ItensEstoqueRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.ItemEstoque;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

// Implementacao JPA da porta ItensEstoqueRepository (Approach A — Seam #3/OQ#2 G1): opera sobre a
// PROPRIA entidade de dominio ItemEstoque (anotada com JPA), sem modelo separado. A baixa continua
// ATOMICA e condicional via UPDATE em bloco (WHERE quantidade >= :q).
@Repository
public class ItensEstoqueRepositoryJPA implements ItensEstoqueRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<ItemEstoque> recuperaTodos() {
        return this.entityManager.createQuery(
                "SELECT e FROM ItemEstoque e ORDER BY e.id", ItemEstoque.class)
            .getResultList();
    }

    @Override
    @Transactional
    public boolean baixaSeDisponivel(long ingredienteId, int quantidade) {
        // Baixa atomica e condicional: o WHERE quantidade >= :q garante que so decrementa quando ha
        // saldo, num unico UPDATE. executeUpdate retorna as linhas afetadas (>0 => baixou).
        int linhasAfetadas = this.entityManager.createQuery(
                "UPDATE ItemEstoque e SET e.quantidade = e.quantidade - :q " +
                "WHERE e.ingrediente.id = :id AND e.quantidade >= :q")
            .setParameter("q", quantidade)
            .setParameter("id", ingredienteId)
            .executeUpdate();
        return linhasAfetadas > 0;
    }

    @Override
    @Transactional
    public void devolve(long ingredienteId, int quantidade) {
        this.entityManager.createQuery(
                "UPDATE ItemEstoque e SET e.quantidade = e.quantidade + :q " +
                "WHERE e.ingrediente.id = :id")
            .setParameter("q", quantidade)
            .setParameter("id", ingredienteId)
            .executeUpdate();
    }
}
