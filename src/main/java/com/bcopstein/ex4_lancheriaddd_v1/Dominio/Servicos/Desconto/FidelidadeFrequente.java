package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto;

import org.springframework.stereotype.Component;

@Component
public class FidelidadeFrequente implements IEstrategiaCalculoDesconto {

    public static final String CODIGO = "Fidelidade7";
    private static final double PERCENTUAL = 0.07;
    private static final int MINIMO_PEDIDOS = 3; // mais de 3 pedidos nos ultimos 20 dias

    @Override
    public String getCodigo() {
        return CODIGO;
    }

    @Override
    public double calcular(double subtotalItens, ContextoDesconto contexto) {
        if (subtotalItens < 0) {
            throw new IllegalArgumentException("Subtotal nao pode ser negativo: " + subtotalItens);
        }
        if (contexto != null && contexto.pedidosUltimos20Dias() > MINIMO_PEDIDOS) {
            return subtotalItens * PERCENTUAL;
        }
        return 0.0;
    }
}
