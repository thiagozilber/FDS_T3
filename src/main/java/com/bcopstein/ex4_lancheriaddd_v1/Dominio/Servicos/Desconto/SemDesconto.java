package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto;

import org.springframework.stereotype.Component;

@Component
public class SemDesconto implements IEstrategiaCalculoDesconto {

    public static final String CODIGO = "SemDesconto";

    @Override
    public String getCodigo() {
        return CODIGO;
    }

    @Override
    public double calcular(double subtotalItens, ContextoDesconto contexto) {
        if (subtotalItens < 0) {
            throw new IllegalArgumentException("Subtotal nao pode ser negativo: " + subtotalItens);
        }
        return 0.0;
    }
}
