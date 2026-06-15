package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados;

// Porta (Dominio.Dados) para a politica de desconto corrente, persistida e trocavel em runtime (UC4).
public interface DescontoRepository {
    String politicaCorrente();
    void definePolitica(String codigo);
}
