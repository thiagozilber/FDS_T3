package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/*
 * Casos de teste -- FabricaEstrategiaDesconto:
 *  1. retornaEstrategiaPorCodigo           : criar("PromocaoVerao") devolve a MESMA instancia (assertSame)
 *  2. codigosDisponiveisContemAsTres       : codigosDisponiveis() contem os 3 codigos
 *  3. codigoDesconhecidoLancaIllegalArgument : criar("xpto") -> IllegalArgumentException
 *  4. codigoNuloLancaIllegalState          : criar(null) -> IllegalStateException
 *  5. codigoEmBrancoLancaIllegalState      : criar("   ") -> IllegalStateException
 */
class FabricaEstrategiaDescontoTest {
    private final SemDesconto semDesconto = new SemDesconto();
    private final FidelidadeFrequente fidelidade = new FidelidadeFrequente();
    private final PromocaoVerao promocaoVerao = new PromocaoVerao();

    private FabricaEstrategiaDesconto fabrica() {
        return new FabricaEstrategiaDesconto(List.of(semDesconto, fidelidade, promocaoVerao));
    }

    @Test
    void retornaEstrategiaPorCodigo() {
        assertSame(promocaoVerao, fabrica().criar("PromocaoVerao"));
        assertSame(semDesconto, fabrica().criar("SemDesconto"));
    }

    @Test
    void codigosDisponiveisContemAsTres() {
        var codigos = fabrica().codigosDisponiveis();
        assertEquals(3, codigos.size());
        assertTrue(codigos.contains("SemDesconto"));
        assertTrue(codigos.contains("Fidelidade7"));
        assertTrue(codigos.contains("PromocaoVerao"));
    }

    @Test
    void codigoDesconhecidoLancaIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> fabrica().criar("xpto"));
    }

    @Test
    void codigoNuloLancaIllegalState() {
        assertThrows(IllegalStateException.class, () -> fabrica().criar(null));
    }

    @Test
    void codigoEmBrancoLancaIllegalState() {
        assertThrows(IllegalStateException.class, () -> fabrica().criar("   "));
    }
}
