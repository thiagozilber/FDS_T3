package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

// Entidade JPA correspondente à tabela "produtos".
@Entity
@Table(name = "produtos")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Produto {

    @Id
    @Column(name = "id", nullable = false)
    private long id;

    @Column(name = "descricao", nullable = false, length = 255)
    private String descricao;

    // Relacionamento entre produto e receita através da tabela
    // produto_receita.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinTable(
        name = "produto_receita",
        joinColumns = @JoinColumn(name = "produto_id"),
        inverseJoinColumns = @JoinColumn(name = "receita_id")
    )
    private Receita receita;

    @Column(name = "preco", nullable = false)
    private int preco;

    public Produto(long id, String descricao, Receita receita, int preco) {
        if (!Produto.precoValido(preco))
            throw new IllegalArgumentException("Preco invalido: " + preco);

        if (descricao == null || descricao.isBlank())
            throw new IllegalArgumentException("Descricao invalida");

        if (receita == null)
            throw new IllegalArgumentException("Receita invalida");

        this.id = id;
        this.descricao = descricao;
        this.receita = receita;
        this.preco = preco;
    }

    public long getId() {
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

    // Valida um preco (preco em centavos).
    public static boolean precoValido(int preco) {
        return preco > 0;
    }

    @Override
    public String toString() {
        return "Produto [id=" + id
                + ", descricao=" + descricao
                + ", receita=" + receita
                + ", preco=" + preco + "]";
    }
}