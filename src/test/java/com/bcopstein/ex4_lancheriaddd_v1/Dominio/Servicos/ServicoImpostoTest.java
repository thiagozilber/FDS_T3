package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Config.ImpostoProperties;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Imposto.FabricaEstrategiaImposto;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Imposto.Lei0412de2022;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Imposto.Lei5762de2026;

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
