package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Imposto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

// Driver de teste da estrategia Lei0412de2022 (P6 / Pessoa 2). Imposto fixo de 10% sobre a venda.
//
// ───────────────────────────────────────────────────────────────────────────────
// Casos de teste:
//  1. codigoDaLei              - getCodigoLei() == "0412/2022"
//  2. vendaZeroImpostoZero     - calcular(0) == 0
//  3. venda100ImpostoDe10      - calcular(100) == 10 (10%)
//  4. venda1000ImpostoDe100    - calcular(1000) == 100 (10%)
//  5. vendaNegativaLancaExcecao- venda negativa -> IllegalArgumentException
// ───────────────────────────────────────────────────────────────────────────────
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
