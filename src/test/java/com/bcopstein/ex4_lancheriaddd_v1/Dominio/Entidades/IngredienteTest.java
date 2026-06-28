package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/*
 * Casos de teste -- Ingrediente:
 *  1. criaIngredienteComDadosValidos : verifica se o construtor inicializa corretamente os atributos.
 */
class IngredienteTest {

    @Test
    void criaIngredienteComDadosValidos() {

        Ingrediente ingrediente = new Ingrediente(
                1L,
                "Queijo Mussarela"
        );

        assertEquals(1L, ingrediente.getId());
        assertEquals("Queijo Mussarela", ingrediente.getDescricao());
    }
}