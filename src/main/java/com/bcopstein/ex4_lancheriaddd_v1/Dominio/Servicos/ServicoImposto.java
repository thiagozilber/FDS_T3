package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos;

import org.springframework.stereotype.Service;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Imposto.FabricaEstrategiaImposto;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Imposto.IEstrategiaCalculoImposto;

// Ponto de entrada para calculo de imposto.
@Service
public class ServicoImposto {

    private final FabricaEstrategiaImposto fabrica;

    public ServicoImposto(FabricaEstrategiaImposto fabrica) {
        this.fabrica = fabrica;
    }

    public double calcularImposto(double valorVenda) {
        IEstrategiaCalculoImposto estrategia = fabrica.criar();
        return estrategia.calcular(valorVenda);
    }

    public String getCodigoLeiVigente() {
        return fabrica.criar().getCodigoLei();
    }
}
