package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.IngredientesRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Ingrediente;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Repository
public class IngredientesRepositoryJPA implements IngredientesRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Recupera todos os ingredientes cadastrados.
     */
    public List<Ingrediente> recuperaTodos() {
        return entityManager.createQuery(
                "SELECT i FROM Ingrediente i ORDER BY i.id",
                Ingrediente.class)
            .getResultList();
    }

    /**
     * Recupera os ingredientes pertencentes a uma receita.
     */
    @Override
    public List<Ingrediente> recuperaIngredientesReceita(long id) {
        return entityManager.createQuery("""
                SELECT i
                FROM Receita r
                    JOIN r.ingredientes i
                WHERE r.id = :id
                """, Ingrediente.class)
            .setParameter("id", id)
            .getResultList();
    }
}