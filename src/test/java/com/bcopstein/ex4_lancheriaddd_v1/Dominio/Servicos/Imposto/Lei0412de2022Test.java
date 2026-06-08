package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Imposto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class Lei0412de2022Test {

    private static final double DELTA = 1e-9;
    private final Lei0412de2022 lei = new Lei0412de2022();

    @Test
    void codigoDaLei() {
        assertEquals("0412/2022", lei.getCodigoLei());
    }

    @Test
    void vendaZeroImpostoZero() {
        assertEquals(0.0, lei.calcular(0.0), DELTA);
    }

    @Test
    void venda100ImpostoDe10() {
        assertEquals(10.0, lei.calcular(100.0), DELTA);
    }

    @Test
    void venda1000ImpostoDe100() {
        assertEquals(100.0, lei.calcular(1000.0), DELTA);
    }

    @Test
    void vendaNegativaLancaExcecao() {
        assertThrows(IllegalArgumentException.class, () -> lei.calcular(-1.0));
    }
}
