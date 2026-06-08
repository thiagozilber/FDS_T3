package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Imposto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Config.ImpostoProperties;

class FabricaEstrategiaImpostoTest {

    private final Lei0412de2022 lei0412 = new Lei0412de2022();
    private final Lei5762de2026 lei5762 = new Lei5762de2026();

    private FabricaEstrategiaImposto fabricaCom(String leiVigente) {
        ImpostoProperties props = new ImpostoProperties();
        props.setLeiVigente(leiVigente);
        return new FabricaEstrategiaImposto(List.of(lei0412, lei5762), props);
    }

    @Test
    void retornaLei0412QuandoVigente() {
        assertSame(lei0412, fabricaCom("0412/2022").criar());
    }

    @Test
    void retornaLei5762QuandoVigente() {
        assertSame(lei5762, fabricaCom("5762/2026").criar());
    }

    @Test
    void criarComCodigoExplicitoIgnoraConfig() {
        FabricaEstrategiaImposto fabrica = fabricaCom("0412/2022");
        assertSame(lei5762, fabrica.criar("5762/2026"));
    }

    @Test
    void leiNulaLancaIllegalState() {
        FabricaEstrategiaImposto fabrica = fabricaCom(null);
        assertThrows(IllegalStateException.class, fabrica::criar);
    }

    @Test
    void leiEmBrancoLancaIllegalState() {
        FabricaEstrategiaImposto fabrica = fabricaCom("   ");
        assertThrows(IllegalStateException.class, fabrica::criar);
    }

    @Test
    void leiDesconhecidaLancaIllegalArgument() {
        FabricaEstrategiaImposto fabrica = fabricaCom("9999/9999");
        assertThrows(IllegalArgumentException.class, fabrica::criar);
    }

    @Test
    void calcularViaEstrategiaResolvida() {
        FabricaEstrategiaImposto fabrica = fabricaCom("0412/2022");
        assertEquals(10.0, fabrica.criar().calcular(100.0), 1e-9);
    }
}
