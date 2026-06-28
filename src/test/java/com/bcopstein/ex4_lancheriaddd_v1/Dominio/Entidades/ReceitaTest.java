package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import org.junit.jupiter.api.Test;

/*
 * Casos de teste -- Receita:
 *  1. criaReceitaComDadosValidos : verifica se o construtor inicializa corretamente os atributos.
 */
class ReceitaTest {

    @Test
    void criaReceitaComDadosValidos() {

        Ingrediente queijo = new Ingrediente(1L, "Queijo");
        Ingrediente tomate = new Ingrediente(2L, "Tomate");

        List<Ingrediente> ingredientes = List.of(queijo, tomate);

        Receita receita = new Receita(
                10L,
                "Pizza Margherita",
                ingredientes
        );

        assertEquals(10L, receita.getId());
        assertEquals("Pizza Margherita", receita.getTitulo());

        assertEquals(2, receita.getIngredientes().size());
        assertSame(ingredientes, receita.getIngredientes());

        assertEquals("Queijo",
                receita.getIngredientes().get(0).getDescricao());

        assertEquals("Tomate",
                receita.getIngredientes().get(1).getDescricao());
    }
}