package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Imposto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class Lei5762de2026Test {

    private static final double DELTA = 1e-9;
    private final Lei5762de2026 lei = new Lei5762de2026();

    @Test
    void codigoDaLei() {
        assertEquals("5762/2026", lei.getCodigoLei());
    }

    @Test
    void vendaZeroIsenta() {
        assertEquals(0.0, lei.calcular(0.0), DELTA);
    }

    @Test
    void vendaIgualAoLimiteIsenta() {
        assertEquals(0.0, lei.calcular(50.0), DELTA);
    }

    @Test
    void vendaLogoAcimaDoLimiteTributaApenasOExcedente() {
        assertEquals(0.0015, lei.calcular(50.01), DELTA);
    }

    @Test
    void venda100PagaQuinzePorCentoDeCinquenta() {
        assertEquals(7.5, lei.calcular(100.0), DELTA);
    }

    @Test
    void venda1000PagaQuinzePorCentoDeNovecentosECinquenta() {
        assertEquals(142.5, lei.calcular(1000.0), DELTA);
    }

    @Test
    void vendaNegativaLancaExcecao() {
        assertThrows(IllegalArgumentException.class, () -> lei.calcular(-0.01));
    }
}
