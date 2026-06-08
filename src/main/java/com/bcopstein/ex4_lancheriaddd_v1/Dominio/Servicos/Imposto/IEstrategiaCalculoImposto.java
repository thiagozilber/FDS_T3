package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Imposto;

// Strategy: contrato de cálculo de imposto para uma lei específica.
// Cada lei = uma implementação. Novas leis entram sem alterar código existente (OCP).
public interface IEstrategiaCalculoImposto {
    String getCodigoLei();
    double calcular(double valorVenda);
}
