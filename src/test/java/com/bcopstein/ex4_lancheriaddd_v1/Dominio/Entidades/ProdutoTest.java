package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/*
 * Casos de teste -- Produto:
 *  1. criaProdutoValido
 *  2. criaProdutoPrecoInvalidoLanca
 *  3. criaProdutoDescricaoVaziaLanca
 *  4. criaProdutoReceitaNulaLanca
 *  5. alteraPrecoValido
 *  6. alteraPrecoInvalidoLanca
 *  7. precoValidoRetornaTrue
 *  8. precoInvalidoRetornaFalse
 */
class ProdutoTest {

    private Receita receitaValida() {
        return new Receita(
                1L,
                "Receita Teste",
                List.of()
        );
    }

    @Test
    void criaProdutoValido() {

        Produto produto = new Produto(
                1L,
                "Pizza",
                receitaValida(),
                5000
        );

        assertEquals(1L, produto.getId());
        assertEquals("Pizza", produto.getDescricao());
        assertEquals(5000, produto.getPreco());
        assertNotNull(produto.getReceita());
    }

    @Test
    void criaProdutoPrecoInvalidoLanca() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new Produto(
                        1L,
                        "Pizza",
                        receitaValida(),
                        0
                )
        );
    }

    @Test
    void criaProdutoDescricaoVaziaLanca() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new Produto(
                        1L,
                        "",
                        receitaValida(),
                        5000
                )
        );
    }

    @Test
    void criaProdutoReceitaNulaLanca() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new Produto(
                        1L,
                        "Pizza",
                        null,
                        5000
                )
        );
    }

    @Test
    void alteraPrecoValido() {

        Produto produto = new Produto(
                1L,
                "Pizza",
                receitaValida(),
                5000
        );

        produto.setPreco(6500);

        assertEquals(6500, produto.getPreco());
    }

    @Test
    void alteraPrecoInvalidoLanca() {

        Produto produto = new Produto(
                1L,
                "Pizza",
                receitaValida(),
                5000
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> produto.setPreco(0)
        );
    }

    @Test
    void precoValidoRetornaTrue() {

        assertTrue(Produto.precoValido(1));
        assertTrue(Produto.precoValido(5000));
    }

    @Test
    void precoInvalidoRetornaFalse() {

        assertFalse(Produto.precoValido(0));
        assertFalse(Produto.precoValido(-10));
    }
}