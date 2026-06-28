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

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.CardapioRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.CabecalhoCardapio;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Cardapio;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Produto;

/*
 * Casos de teste -- CardapioRepositoryJPA:
 *  1. recuperaCardapioExistente
 *  2. recuperaCardapioInexistenteRetornaNull
 *  3. cardapiosDisponiveisRetornaLista
 *  4. indicacoesDoChefRetornaProduto
 *  5. defineERecuperaCardapioCorrente
 */
@SpringBootTest
@Transactional
class CardapioRepositoryJPATest {

    @Autowired
    private CardapioRepository cardapioRepository;

    @Test
    void recuperaCardapioExistente() {

        Cardapio cardapio = cardapioRepository.recuperaPorId(1L);

        assertNotNull(cardapio);
        assertEquals(1L, cardapio.getCabecalhoCardapio().id());
        assertNotNull(cardapio.getCabecalhoCardapio().titulo());

        assertNotNull(cardapio.getProdutos());
        assertFalse(cardapio.getProdutos().isEmpty());
    }

    @Test
    void recuperaCardapioInexistenteRetornaNull() {

        Cardapio cardapio = cardapioRepository.recuperaPorId(99999L);

        assertNull(cardapio);
    }

    @Test
    void cardapiosDisponiveisRetornaLista() {

        List<CabecalhoCardapio> cardapios =
                cardapioRepository.cardapiosDisponiveis();

        assertNotNull(cardapios);
        assertFalse(cardapios.isEmpty());

        cardapios.forEach(c -> {
            assertTrue(c.id() > 0);
            assertNotNull(c.titulo());
        });
    }

    @Test
    void indicacoesDoChefRetornaProduto() {

        List<Produto> produtos =
                cardapioRepository.indicacoesDoChef();

        assertNotNull(produtos);
        assertEquals(1, produtos.size());

        Produto produto = produtos.getFirst();

        assertEquals(2L, produto.getId());
        assertNotNull(produto.getDescricao());
    }

    @Test
    void defineERecuperaCardapioCorrente() {

        cardapioRepository.defineCorrente(1L);

        Cardapio corrente = cardapioRepository.recuperaCorrente();

        assertNotNull(corrente);
        assertEquals(1L, corrente.getCabecalhoCardapio().id());
    }
}