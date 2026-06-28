package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.ProdutosRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Produto;

/*
 * Casos de teste -- ProdutosRepositoryJPA:
 *  1. recuperaProdutoExistente
 *  2. recuperaProdutoInexistenteRetornaNull
 *  3. recuperaProdutosCardapioExistente
 *  4. recuperaProdutosCardapioInexistenteRetornaListaVazia
 */
@SpringBootTest
@Transactional
class ProdutosRepositoryJPATest {

    @Autowired
    private ProdutosRepository produtosRepository;

    @Test
    void recuperaProdutoExistente() {

        Produto produto = produtosRepository.recuperaProdutoPorid(1L);

        assertNotNull(produto);
        assertEquals(1L, produto.getId());
        assertNotNull(produto.getDescricao());
        assertTrue(produto.getPreco() > 0);
    }

    @Test
    void recuperaProdutoInexistenteRetornaNull() {

        Produto produto = produtosRepository.recuperaProdutoPorid(99999L);

        assertNull(produto);
    }

    @Test
    void recuperaProdutosCardapioExistente() {

        List<Produto> produtos =
                produtosRepository.recuperaProdutosCardapio(1L);

        assertNotNull(produtos);
        assertFalse(produtos.isEmpty());

        produtos.forEach(produto -> {
            assertNotNull(produto.getDescricao());
            assertTrue(produto.getPreco() > 0);
        });
    }

    @Test
    void recuperaProdutosCardapioInexistenteRetornaListaVazia() {

        List<Produto> produtos =
                produtosRepository.recuperaProdutosCardapio(99999L);

        assertNotNull(produtos);
        assertTrue(produtos.isEmpty());
    }
}