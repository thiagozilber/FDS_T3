package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/*
 * Casos de teste -- SemDesconto (CODIGO "SemDesconto"):
 *  1. codigoEhSemDesconto          : getCodigo() == "SemDesconto"
 *  2. descontoSempreZero           : calcular(100, ctx) == 0.0 independente do contexto
 *  3. subtotalNegativoLancaExcecao : calcular(-1, ctx) -> IllegalArgumentException
 */
class SemDescontoTest {
    private static final double DELTA = 1e-9;
    private final SemDesconto estrategia = new SemDesconto();

    @Test
    void codigoEhSemDesconto() {
        assertEquals("SemDesconto", estrategia.getCodigo());
    }

    @Test
    void descontoSempreZero() {
        assertEquals(0.0, estrategia.calcular(100.0, new ContextoDesconto(9)), DELTA);
        assertEquals(0.0, estrategia.calcular(0.0, new ContextoDesconto(0)), DELTA);
    }

    @Test
    void subtotalNegativoLancaExcecao() {
        assertThrows(IllegalArgumentException.class, () -> estrategia.calcular(-1.0, new ContextoDesconto(0)));
    }
}
