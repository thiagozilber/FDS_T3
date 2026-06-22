package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/*
 * Casos de teste -- FidelidadeFrequente (CODIGO "Fidelidade7", 7% se > 3 pedidos/20 dias):
 *  1. codigoEhFidelidade7          : getCodigo() == "Fidelidade7"
 *  2. acimaDoLimiteAplica7PorCento : ctx(4) -> calcular(100) == 7.0
 *  3. exatamenteNoLimiteNaoAplica  : ctx(3) -> calcular(100) == 0.0 (regra e "> 3")
 *  4. semPedidosNaoAplica          : ctx(0) -> calcular(100) == 0.0
 *  5. subtotalNegativoLancaExcecao : calcular(-1, ctx) -> IllegalArgumentException
 */
class FidelidadeFrequenteTest {
    private static final double DELTA = 1e-9;
    private final FidelidadeFrequente estrategia = new FidelidadeFrequente();

    @Test
    void codigoEhFidelidade7() {
        assertEquals("Fidelidade7", estrategia.getCodigo());
    }

    @Test
    void acimaDoLimiteAplica7PorCento() {
        assertEquals(7.0, estrategia.calcular(100.0, new ContextoDesconto(4)), DELTA);
    }

    @Test
    void exatamenteNoLimiteNaoAplica() {
        assertEquals(0.0, estrategia.calcular(100.0, new ContextoDesconto(3)), DELTA);
    }

    @Test
    void semPedidosNaoAplica() {
        assertEquals(0.0, estrategia.calcular(100.0, new ContextoDesconto(0)), DELTA);
    }

    @Test
    void subtotalNegativoLancaExcecao() {
        assertThrows(IllegalArgumentException.class, () -> estrategia.calcular(-1.0, new ContextoDesconto(5)));
    }

    @Test
    void contextoComPedidosNegativosLancaExcecao() {
        assertThrows(IllegalArgumentException.class, () -> new ContextoDesconto(-1));
    }
}
