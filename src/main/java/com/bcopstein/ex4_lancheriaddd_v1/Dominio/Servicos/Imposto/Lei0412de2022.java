package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Imposto;

import org.springframework.stereotype.Component;

// Lei 0412/2022: imposto unico de 10% sobre o valor total da venda.
@Component
public class Lei0412de2022 implements IEstrategiaCalculoImposto {

    public static final String CODIGO = "0412/2022";
    private static final double ALIQUOTA = 0.10;

    @Override
    public String getCodigoLei() {
        return CODIGO;
    }

    @Override
    public double calcular(double valorVenda) {
        if (valorVenda < 0) {
            throw new IllegalArgumentException("Valor da venda nao pode ser negativo: " + valorVenda);
        }
        return valorVenda * ALIQUOTA;
    }
}
