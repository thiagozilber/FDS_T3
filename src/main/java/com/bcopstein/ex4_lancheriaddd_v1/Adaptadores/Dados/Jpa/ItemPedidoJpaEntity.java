package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados.Jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Modelo de persistencia JPA da tabela "itens_pedido" (linha do agregado Pedido — D14).
// Mapeia o produto por id (produto_id), sem associacao @ManyToOne a Produto, para manter as
// entidades da Pessoa 2 fora do JPA (Approach B). PK composta (pedido_id, produto_id).
@Entity
@Table(name = "itens_pedido")
@IdClass(ItemPedidoId.class)
@Getter
@Setter
@NoArgsConstructor
public class ItemPedidoJpaEntity {

    @Id
    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;

    @Id
    @Column(name = "produto_id", nullable = false)
    private Long produtoId;

    @Column(name = "quantidade", nullable = false)
    private int quantidade;

    public ItemPedidoJpaEntity(Long pedidoId, Long produtoId, int quantidade) {
        this.pedidoId = pedidoId;
        this.produtoId = produtoId;
        this.quantidade = quantidade;
    }
}
