package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/*
 * Casos de teste -- PromocaoVerao (CODIGO "PromocaoVerao", 5% fixo):
 *  1. codigoEhPromocaoVerao        : getCodigo() == "PromocaoVerao"
 *  2. desconto5PorCento            : calcular(100, ctx) == 5.0
 *  3. subtotalNegativoLancaExcecao : calcular(-1, ctx) -> IllegalArgumentException
 */
class PromocaoVeraoTest {
    private static final double DELTA = 1e-9;
    private final PromocaoVerao estrategia = new PromocaoVerao();

    @Test
    void codigoEhPromocaoVerao() {
        assertEquals("PromocaoVerao", estrategia.getCodigo());
    }

    @Test
    void desconto5PorCento() {
        assertEquals(5.0, estrategia.calcular(100.0, new ContextoDesconto(0)), DELTA);
    }

    @Test
    void subtotalNegativoLancaExcecao() {
        assertThrows(IllegalArgumentException.class, () -> estrategia.calcular(-1.0, new ContextoDesconto(0)));
    }
}
