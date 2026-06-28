package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

// Entidade JPA correspondente a tabela "itensEstoque" (Approach A). A tabela tem chave primaria
// surrogate "id" (semeada em data.sql, sem auto_increment), entao a entidade carrega um @Id "id"
// usado apenas pela persistencia (sem getter de dominio). O ingrediente e uma associacao @ManyToOne.
@Entity
@Table(name = "itensEstoque")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemEstoque {

    @Id
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ingrediente_id")
    private Ingrediente ingrediente;

    @Column(name = "quantidade")
    private int quantidade;

    public ItemEstoque(Ingrediente ingrediente, int quantidade) {
        this.ingrediente = ingrediente;
        this.quantidade = quantidade;
    }

    public Ingrediente getIngrediente() { return ingrediente; }
    public int getQuantidade() { return quantidade; }
    public void setQuantidade(int quantidade) { this.quantidade = quantidade; }
}
