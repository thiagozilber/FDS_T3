package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades;

import java.io.Serializable;
import java.util.Objects;

// Chave composta de itens_pedido para o @IdClass de ItemPedido. Em identidade derivada, os nomes
// dos campos casam com os atributos @Id da entidade (pedido, item) e os tipos casam com os tipos
// das chaves das entidades referenciadas (Pedido.id e Produto.id, ambos long).
public class ItemPedidoId implements Serializable {

    private long pedido;
    private long item;

    public ItemPedidoId() {
    }

    public ItemPedidoId(long pedido, long item) {
        this.pedido = pedido;
        this.item = item;
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
        return pedido == that.pedido && item == that.item;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pedido, item);
    }
}
