package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

// Linha do agregado Pedido (tabela "itens_pedido"), Approach A. PK composta (pedido_id, produto_id)
// modelada como identidade derivada das associacoes @ManyToOne (pedido, item) via @IdClass.
// Produto e entidade JPA (Pessoa 2), entao a referencia ao item e uma associacao real — algo que
// o Approach B (modelo separado) nao permitia.
@Entity
@Table(name = "itens_pedido")
@IdClass(ItemPedidoId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemPedido {

    @Id
    @ManyToOne
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Id
    @ManyToOne
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto item;

    @Column(name = "quantidade", nullable = false)
    private int quantidade;

    public ItemPedido(Produto item, int quantidade) {
        this.item = item;
        this.quantidade = quantidade;
    }

    public Produto getItem() {
        return item;
    }

    public int getQuantidade() {
        return quantidade;
    }

    // Ligacao bidirecional definida pelo raiz do agregado (Pedido) — pacote-privado de proposito.
    void setPedido(Pedido pedido) {
        this.pedido = pedido;
    }
}
