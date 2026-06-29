package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Config.ImpostoProperties;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Imposto.FabricaEstrategiaImposto;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Imposto.Lei0412de2022;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Imposto.Lei5762de2026;

// Driver de teste do ServicoImposto (P6 / Pessoa 2). Teste de unidade puro (sem Spring),
// montando a fabrica com as duas estrategias reais e a lei vigente controlada por ImpostoProperties.
//
// ───────────────────────────────────────────────────────────────────────────────
// Casos de teste:
//  1. aplicaLei0412QuandoVigente - lei "0412/2022" vigente: calcularImposto(100)=10 (10%) e getCodigoLeiVigente()="0412/2022"
//  2. aplicaLei5762QuandoVigente - lei "5762/2026" vigente: calcularImposto(100)=7.5 e calcularImposto(50)=0 (isento ate 50); codigo="5762/2026"
// ───────────────────────────────────────────────────────────────────────────────
class ServicoImpostoTest {

    private static final double DELTA = 1e-9;

    private ServicoImposto servicoCom(String leiVigente) {
        ImpostoProperties props = new ImpostoProperties();
        props.setLeiVigente(leiVigente);
        FabricaEstrategiaImposto fabrica = new FabricaEstrategiaImposto(
                List.of(new Lei0412de2022(), new Lei5762de2026()), props);
        return new ServicoImposto(fabrica);
    }

    @Test
    void aplicaLei0412QuandoVigente() {
        ServicoImposto servico = servicoCom("0412/2022");
        assertEquals(10.0, servico.calcularImposto(100.0), DELTA);
        assertEquals("0412/2022", servico.getCodigoLeiVigente());
    }

    @Test
    void aplicaLei5762QuandoVigente() {
        ServicoImposto servico = servicoCom("5762/2026");
        assertEquals(7.5, servico.calcularImposto(100.0), DELTA);
        assertEquals(0.0, servico.calcularImposto(50.0), DELTA);
        assertEquals("5762/2026", servico.getCodigoLeiVigente());
    }
}
