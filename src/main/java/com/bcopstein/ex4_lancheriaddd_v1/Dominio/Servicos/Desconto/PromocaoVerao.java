package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto;

import org.springframework.stereotype.Component;

@Component
public class PromocaoVerao implements IEstrategiaCalculoDesconto {

    public static final String CODIGO = "PromocaoVerao";
    private static final double PERCENTUAL = 0.05;

    @Override
    public String getCodigo() {
        return CODIGO;
    }

    @Override
    public double calcular(double subtotalItens, ContextoDesconto contexto) {
        if (subtotalItens < 0) {
            throw new IllegalArgumentException("Subtotal nao pode ser negativo: " + subtotalItens);
        }
        return subtotalItens * PERCENTUAL;
    }
}
