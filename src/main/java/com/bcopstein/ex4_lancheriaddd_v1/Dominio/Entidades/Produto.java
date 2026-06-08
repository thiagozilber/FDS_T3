package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades;

public class Produto {
    private long id;
    private String descricao;
    private Receita receita;
    private int preco;

    public Produto(long id,String descricao, Receita receita, int preco) {
        if (!Produto.precoValido(preco))
            throw new IllegalArgumentException("Preco invalido: " + preco);
        if (descricao == null || descricao.length() == 0)
            throw new IllegalArgumentException("Descricao invalida");
        if (receita == null)
            throw new IllegalArgumentException("Receita invalida");
        this.id = id;
        this.descricao = descricao;
        this.receita = receita;
        this.preco = preco;
    }

    public long getId(){
        return id;
    }

    public String getDescricao() {
        return descricao;
    }

    public Receita getReceita() {
        return receita;
    }

    public int getPreco() {
        return preco;
    }

    public void setPreco(int preco) {
        if (!Produto.precoValido(preco))
            throw new IllegalArgumentException("Preco invalido: " + preco);
        this.preco = preco;
    }

    // Valida um preco (preco em centavos)
    public static boolean precoValido(int preco) {
        return preco > 0;
    }

    @Override
    public String toString() {
        return "Produto [id=" + id + ", descricao=" + descricao + ", receita=" + receita + ", preco=" + preco + "]";
    }
    
}
