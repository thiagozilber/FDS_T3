package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto;

// Estrategia de calculo de desconto (padrao Strategy, espelha IEstrategiaCalculoImposto).
// getCodigo() e a chave de registro na fabrica; calcular() devolve o VALOR do desconto.
public interface IEstrategiaCalculoDesconto {
    String getCodigo();
    double calcular(double subtotalItens, ContextoDesconto contexto);
}
