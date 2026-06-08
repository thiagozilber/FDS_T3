package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades;

import java.util.List;

public class Cardapio {
    private CabecalhoCardapio cabecalhoCardapio;
    private List<Produto> produtos;

    public Cardapio(CabecalhoCardapio cabecalhoCardapio, List<Produto> produtos) {
        this.cabecalhoCardapio = cabecalhoCardapio;
        this.produtos = produtos;
    }
    public CabecalhoCardapio getCabecalhoCardapio(){ return cabecalhoCardapio; }
    public List<Produto> getProdutos() { return produtos; }
    public void setProdutos(List<Produto> produtos){this.produtos = produtos;}
}
