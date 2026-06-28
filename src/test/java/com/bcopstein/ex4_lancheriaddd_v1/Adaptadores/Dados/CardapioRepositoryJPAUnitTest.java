package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.ProdutosRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Produto;

/*
 * Casos de teste -- CardapioRepositoryJPA (logica pura, sem Spring):
 *  1. indicacoesDoChefProdutoAusenteRetornaListaVazia
 *     -- quando o produto sugerido (id 2) nao existe, recuperaProdutoPorid
 *        retorna null e indicacoesDoChef NAO pode lancar NullPointerException
 *        (List.of(null) lanca); deve retornar lista vazia.
 */
class CardapioRepositoryJPAUnitTest {

    private static class FakeProdutosRepository implements ProdutosRepository {
        @Override
        public Produto recuperaProdutoPorid(long id) {
            return null; // simula produto inexistente
        }

        @Override
        public List<Produto> recuperaProdutosCardapio(long id) {
            return List.of();
        }
    }

    @Test
    void indicacoesDoChefProdutoAusenteRetornaListaVazia() {
        // EntityManager e ConfiguracaoRepositoryJDBC nao sao usados por
        // indicacoesDoChef, entao podem ficar nulos neste teste de unidade.
        CardapioRepositoryJPA repo =
                new CardapioRepositoryJPA(new FakeProdutosRepository(), null);

        List<Produto> sugestoes = repo.indicacoesDoChef();

        assertNotNull(sugestoes);
        assertTrue(sugestoes.isEmpty());
    }
}
