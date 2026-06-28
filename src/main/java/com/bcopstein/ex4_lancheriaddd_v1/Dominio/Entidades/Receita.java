package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

// Entidade JPA correspondente à tabela "receitas".
@Entity
@Table(name = "receitas")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Receita {

    @Id
    @Column(name = "id", nullable = false)
    private long id;

    @Column(name = "titulo", nullable = false, length = 255)
    private String titulo;

    // Relacionamento entre receitas e ingredientes através da
    // tabela receita_ingrediente.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "receita_ingrediente",
        joinColumns = @JoinColumn(name = "receita_id"),
        inverseJoinColumns = @JoinColumn(name = "ingrediente_id")
    )
    private List<Ingrediente> ingredientes;

    public Receita(long id, String titulo, List<Ingrediente> ingredientes) {
        this.id = id;
        this.titulo = titulo;
        this.ingredientes = ingredientes;
    }

    public long getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public List<Ingrediente> getIngredientes() {
        return ingredientes;
    }
}