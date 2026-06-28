package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.IngredientesRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Ingrediente;

/*
 * Casos de teste -- IngredientesRepositoryJPA:
 *  1. recuperaTodosRetornaIngredientes
 *  2. recuperaIngredientesReceitaExistente
 *  3. recuperaIngredientesReceitaInexistenteRetornaListaVazia
 */
@SpringBootTest
@Transactional
class IngredientesRepositoryJPATest {

    @Autowired
    private IngredientesRepository ingredientesRepository;

    @Test
    void recuperaTodosRetornaIngredientes() {

        List<Ingrediente> ingredientes =
                ingredientesRepository.recuperaTodos();

        assertNotNull(ingredientes);
        assertFalse(ingredientes.isEmpty());
    }

    @Test
    void recuperaIngredientesReceitaExistente() {

        // Receita de id = 1 presente no data.sql
        List<Ingrediente> ingredientes =
                ingredientesRepository.recuperaIngredientesReceita(1L);

        assertNotNull(ingredientes);
        assertFalse(ingredientes.isEmpty());

        ingredientes.forEach(i -> {
            assertNotNull(i.getId());
            assertNotNull(i.getDescricao());
        });
    }

    @Test
    void recuperaIngredientesReceitaInexistenteRetornaListaVazia() {

        List<Ingrediente> ingredientes =
                ingredientesRepository.recuperaIngredientesReceita(99999L);

        assertNotNull(ingredientes);
        assertTrue(ingredientes.isEmpty());
    }
}