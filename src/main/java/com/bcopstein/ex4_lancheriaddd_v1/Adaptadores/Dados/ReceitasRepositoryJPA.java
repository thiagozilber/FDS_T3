package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import org.springframework.stereotype.Repository;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.ReceitasRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Receita;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Repository
public class ReceitasRepositoryJPA implements ReceitasRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Recupera uma receita juntamente com seus ingredientes.
     */
    @Override
    public Receita recuperaReceita(long id) {
        return entityManager.find(Receita.class, id);
    }
}