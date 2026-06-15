package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.DescontoRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto.ContextoDesconto;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto.FabricaEstrategiaDesconto;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto.FidelidadeFrequente;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto.PromocaoVerao;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto.SemDesconto;

/*
 * Casos de teste -- ServicoDesconto (fachada + politica corrente persistida):
 *  1. semDescontoCorrenteRetornaZero    : politica "SemDesconto" -> calcularDesconto == 0.0
 *  2. promocaoVeraoCorrenteAplica5      : politica "PromocaoVerao" -> calcularDesconto(100) == 5.0
 *  3. fidelidadeUsaContexto             : politica "Fidelidade7" -> ctx(4)=7.0, ctx(3)=0.0
 *  4. definirPoliticaPersisteEReflete   : definirPolitica troca a corrente e recalcula
 *  5. listarPoliticasRetornaTodos       : listarPoliticas() contem os 3 codigos
 *  6. definirPoliticaDesconhecidaLanca  : definirPolitica("xpto") -> IllegalArgumentException, sem persistir
 */
class ServicoDescontoTest {
    private static final double DELTA = 1e-9;

    // Fake in-memory do port DescontoRepository (sem Mockito; segue a cultura POJO do projeto).
    private static class FakeDescontoRepository implements DescontoRepository {
        private String corrente;
        FakeDescontoRepository(String inicial) { this.corrente = inicial; }
        @Override public String politicaCorrente() { return corrente; }
        @Override public void definePolitica(String codigo) { this.corrente = codigo; }
    }

    private ServicoDesconto servicoCom(String politicaInicial) {
        FabricaEstrategiaDesconto fabrica = new FabricaEstrategiaDesconto(
            List.of(new SemDesconto(), new FidelidadeFrequente(), new PromocaoVerao()));
        return new ServicoDesconto(fabrica, new FakeDescontoRepository(politicaInicial));
    }

    @Test
    void semDescontoCorrenteRetornaZero() {
        ServicoDesconto servico = servicoCom("SemDesconto");
        assertEquals(0.0, servico.calcularDesconto(100.0, new ContextoDesconto(9)), DELTA);
    }

    @Test
    void promocaoVeraoCorrenteAplica5() {
        ServicoDesconto servico = servicoCom("PromocaoVerao");
        assertEquals(5.0, servico.calcularDesconto(100.0, new ContextoDesconto(0)), DELTA);
    }

    @Test
    void fidelidadeUsaContexto() {
        ServicoDesconto servico = servicoCom("Fidelidade7");
        assertEquals(7.0, servico.calcularDesconto(100.0, new ContextoDesconto(4)), DELTA);
        assertEquals(0.0, servico.calcularDesconto(100.0, new ContextoDesconto(3)), DELTA);
    }

    @Test
    void definirPoliticaPersisteEReflete() {
        ServicoDesconto servico = servicoCom("SemDesconto");
        servico.definirPolitica("PromocaoVerao");
        assertEquals("PromocaoVerao", servico.getPoliticaCorrente());
        assertEquals(5.0, servico.calcularDesconto(100.0, new ContextoDesconto(0)), DELTA);
    }

    @Test
    void listarPoliticasRetornaTodos() {
        ServicoDesconto servico = servicoCom("SemDesconto");
        assertEquals(3, servico.listarPoliticas().size());
    }

    @Test
    void definirPoliticaDesconhecidaLanca() {
        ServicoDesconto servico = servicoCom("SemDesconto");
        assertThrows(IllegalArgumentException.class, () -> servico.definirPolitica("xpto"));
        assertEquals("SemDesconto", servico.getPoliticaCorrente()); // nao persistiu
    }
}
