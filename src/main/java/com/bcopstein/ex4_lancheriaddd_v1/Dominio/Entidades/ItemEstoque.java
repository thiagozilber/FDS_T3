package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades;

public class ItemEstoque {
    private Ingrediente ingrediente;
    private int quantidade;

    public ItemEstoque(Ingrediente ingrediente, int quantidade) {
        this.ingrediente = ingrediente;
        this.quantidade = quantidade;
    }

    public Ingrediente getIngrediente() { return ingrediente; }
    public int getQuantidade() { return quantidade; }
    public void setQuantidade(int quantidade) { this.quantidade = quantidade; }
}
