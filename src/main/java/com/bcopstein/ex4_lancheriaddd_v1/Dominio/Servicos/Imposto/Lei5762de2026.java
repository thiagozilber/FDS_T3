package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Imposto;

import org.springframework.stereotype.Component;

// Lei 5762/2026: isencao ate R$ 50,00; 15% sobre o que exceder esse valor.
@Component
public class Lei5762de2026 implements IEstrategiaCalculoImposto {

    public static final String CODIGO = "5762/2026";
    private static final double FAIXA_ISENTA = 50.00;
    private static final double ALIQUOTA = 0.15;

    @Override
    public String getCodigoLei() {
        return CODIGO;
    }

    @Override
    public double calcular(double valorVenda) {
        if (valorVenda < 0) {
            throw new IllegalArgumentException("Valor da venda nao pode ser negativo: " + valorVenda);
        }
        if (valorVenda <= FAIXA_ISENTA) {
            return 0.0;
        }
        return (valorVenda - FAIXA_ISENTA) * ALIQUOTA;
    }
}
