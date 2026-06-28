package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

// Entidade JPA correspondente à tabela "ingredientes".
@Entity
@Table(name = "ingredientes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ingrediente {

    @Id
    @Column(name = "id", nullable = false)
    private long id;

    @Column(name = "descricao", nullable = false, length = 255)
    private String descricao;

    public Ingrediente(long id, String descricao) {
        if (descricao == null || descricao.isBlank()) {
            throw new IllegalArgumentException("Descricao invalida");
        }

        this.id = id;
        this.descricao = descricao;
    }

    public long getId() {
        return id;
    }

    public String getDescricao() {
        return descricao;
    }
}