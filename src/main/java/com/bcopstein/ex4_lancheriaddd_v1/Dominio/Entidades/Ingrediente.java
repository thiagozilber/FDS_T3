package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades;

public class Ingrediente {
    private long id;
    private String descricao;

    public Ingrediente(long id, String descricao) {
        this.id = id;
        this.descricao = descricao;
    }

    public long getId() { return id; }
    public String getDescricao() { return descricao; }
}
