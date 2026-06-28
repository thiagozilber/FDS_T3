package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.ReceitasRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Receita;

/*
 * Casos de teste -- ReceitasRepositoryJPA:
 *  1. recuperaReceitaExistente
 *  2. recuperaReceitaExistenteComIngredientes
 *  3. recuperaReceitaInexistenteRetornaNull
 */
@SpringBootTest
@Transactional
class ReceitasRepositoryJPATest {

    @Autowired
    private ReceitasRepository receitasRepository;

    @Test
    void recuperaReceitaExistente() {

        Receita receita = receitasRepository.recuperaReceita(1L);

        assertNotNull(receita);
        assertEquals(1L, receita.getId());
        assertNotNull(receita.getTitulo());
    }

    @Test
    void recuperaReceitaExistenteComIngredientes() {

        Receita receita = receitasRepository.recuperaReceita(1L);

        assertNotNull(receita);
        assertNotNull(receita.getIngredientes());
        assertFalse(receita.getIngredientes().isEmpty());

        receita.getIngredientes().forEach(ingrediente -> {
            assertNotNull(ingrediente.getId());
            assertNotNull(ingrediente.getDescricao());
        });
    }

    @Test
    void recuperaReceitaInexistenteRetornaNull() {

        Receita receita = receitasRepository.recuperaReceita(99999L);

        assertNull(receita);
    }
}