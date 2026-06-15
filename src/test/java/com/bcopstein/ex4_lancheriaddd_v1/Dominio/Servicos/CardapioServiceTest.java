package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.CardapioRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.CabecalhoCardapio;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Cardapio;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Produto;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Excecoes.RecursoNaoEncontradoException;

/*
 * Casos de teste -- CardapioService (caminho infeliz / corrente):
 *  1. recuperaCardapioInexistenteLanca404      : id desconhecido -> RecursoNaoEncontradoException (HTTP 404)
 *  2. recuperaCardapioExistenteRetorna         : id conhecido -> devolve o cardapio
 *  3. defineCorrenteInexistenteLanca           : id desconhecido -> IllegalArgumentException (nao persiste)
 *  4. defineCorrenteExistentePersisteEReflete  : id conhecido -> grava e recuperaCorrente reflete
 *  5. recuperaCorrenteAusenteLancaIllegalState : sem corrente -> IllegalStateException (HTTP 500)
 */
class CardapioServiceTest {

    // Fake in-memory do port CardapioRepository (sem Mockito; cultura POJO do projeto).
    private static class FakeCardapioRepository implements CardapioRepository {
        private final Cardapio existente;
        private Long corrente;

        FakeCardapioRepository(Cardapio existente) {
            this.existente = existente;
        }

        @Override
        public List<CabecalhoCardapio> cardapiosDisponiveis() {
            return List.of(existente.getCabecalhoCardapio());
        }

        @Override
        public Cardapio recuperaPorId(long id) {
            return id == existente.getCabecalhoCardapio().id() ? existente : null;
        }

        @Override
        public List<Produto> indicacoesDoChef() {
            return List.of();
        }

        @Override
        public void defineCorrente(long id) {
            this.corrente = id;
        }

        @Override
        public Cardapio recuperaCorrente() {
            return corrente == null ? null : recuperaPorId(corrente);
        }
    }

    private CardapioService servicoCom(long idExistente) {
        Cardapio c = new Cardapio(new CabecalhoCardapio(idExistente, "Cardapio Teste"), List.of());
        return new CardapioService(new FakeCardapioRepository(c));
    }

    @Test
    void recuperaCardapioInexistenteLanca404() {
        assertThrows(RecursoNaoEncontradoException.class, () -> servicoCom(1L).recuperaCardapio(99L));
    }

    @Test
    void recuperaCardapioExistenteRetorna() {
        assertEquals("Cardapio Teste", servicoCom(1L).recuperaCardapio(1L).getCabecalhoCardapio().titulo());
    }

    @Test
    void defineCorrenteInexistenteLanca() {
        assertThrows(IllegalArgumentException.class, () -> servicoCom(1L).defineCardapioCorrente(99L));
    }

    @Test
    void defineCorrenteExistentePersisteEReflete() {
        CardapioService servico = servicoCom(1L);
        servico.defineCardapioCorrente(1L);
        assertEquals("Cardapio Teste", servico.recuperaCardapioCorrente().getCabecalhoCardapio().titulo());
    }

    @Test
    void recuperaCorrenteAusenteLancaIllegalState() {
        assertThrows(IllegalStateException.class, () -> servicoCom(1L).recuperaCardapioCorrente());
    }
}
