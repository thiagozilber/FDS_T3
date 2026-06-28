package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados.Jpa;

import java.io.Serializable;
import java.util.Objects;

// Chave composta de itens_pedido (pedido_id, produto_id) — usada via @IdClass em ItemPedidoJpaEntity.
// Os nomes dos campos devem casar com os campos @Id da entidade.
public class ItemPedidoId implements Serializable {

    private Long pedidoId;
    private Long produtoId;

    public ItemPedidoId() {
    }

    public ItemPedidoId(Long pedidoId, Long produtoId) {
        this.pedidoId = pedidoId;
        this.produtoId = produtoId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemPedidoId)) {
            return false;
        }
        ItemPedidoId that = (ItemPedidoId) o;
        return Objects.equals(pedidoId, that.pedidoId)
            && Objects.equals(produtoId, that.produtoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pedidoId, produtoId);
    }
}
