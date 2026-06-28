package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados.Jpa.ItemEstoqueJpaEntity;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.ItensEstoqueRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Ingrediente;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.ItemEstoque;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

// Implementacao JPA da porta ItensEstoqueRepository (Approach B / Seam #3). Substitui
// ItensEstoqueRepositoryJDBC, preservando a baixa ATOMICA e condicional via UPDATE em bloco.
@Repository
public class ItensEstoqueRepositoryJPA implements ItensEstoqueRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<ItemEstoque> recuperaTodos() {
        // A descricao do ingrediente nao e carregada: os consumidores (ServicoEstoque) usam apenas
        // o id e a quantidade, e nao acoplar a tabela ingredientes mantem o estoque fora do JPA da Pessoa 2.
        List<ItemEstoqueJpaEntity> linhas = this.entityManager.createQuery(
                "SELECT e FROM ItemEstoqueJpaEntity e", ItemEstoqueJpaEntity.class)
            .getResultList();

        List<ItemEstoque> itens = new ArrayList<>();
        for (ItemEstoqueJpaEntity linha : linhas) {
            itens.add(new ItemEstoque(
                new Ingrediente(linha.getIngredienteId(), null), linha.getQuantidade()));
        }
        return itens;
    }

    @Override
    @Transactional
    public boolean baixaSeDisponivel(long ingredienteId, int quantidade) {
        // Baixa atomica e condicional: o WHERE quantidade >= :q garante que so decrementa quando ha
        // saldo, num unico UPDATE. executeUpdate retorna as linhas afetadas (>0 => baixou).
        int linhasAfetadas = this.entityManager.createQuery(
                "UPDATE ItemEstoqueJpaEntity e SET e.quantidade = e.quantidade - :q " +
                "WHERE e.ingredienteId = :id AND e.quantidade >= :q")
            .setParameter("q", quantidade)
            .setParameter("id", ingredienteId)
            .executeUpdate();
        return linhasAfetadas > 0;
    }

    @Override
    @Transactional
    public void devolve(long ingredienteId, int quantidade) {
        this.entityManager.createQuery(
                "UPDATE ItemEstoqueJpaEntity e SET e.quantidade = e.quantidade + :q " +
                "WHERE e.ingredienteId = :id")
            .setParameter("q", quantidade)
            .setParameter("id", ingredienteId)
            .executeUpdate();
    }
}
