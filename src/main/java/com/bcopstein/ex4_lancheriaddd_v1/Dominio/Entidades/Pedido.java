package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

// Entidade JPA correspondente a tabela "pedidos" (Approach A — Seam #3/OQ#2 G1: a entidade de
// dominio E a entidade de persistencia, sem modelo separado). cliente e itens sao associacoes
// gerenciadas pelo ORM; o agregado (cabecalho + itens_pedido) e persistido em cascata.
@Entity
@Table(name = "pedidos")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pedido {
    public enum Status {
        NOVO,
        APROVADO,
        PAGO,
        AGUARDANDO,
        PREPARACAO,
        PRONTO,
        TRANSPORTE,
        ENTREGUE,
        CANCELADO,
        RECUSADO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // pedidos.id e auto_increment no schema.sql
    @Column(name = "id", nullable = false)
    private long id;

    @ManyToOne
    @JoinColumn(name = "cliente_cpf", nullable = false)
    private Cliente cliente;

    @Column(name = "data_hora_pagamento")
    private LocalDateTime dataHoraPagamento;

    // Itens do agregado (D14): itens_pedido referencia pedido_id; persistido/removido com o pedido.
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.EAGER)
    private List<ItemPedido> itens;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "valor", nullable = false)
    private double valor;

    @Column(name = "impostos", nullable = false)
    private double impostos;

    @Column(name = "desconto", nullable = false)
    private double desconto;

    @Column(name = "valor_cobrado", nullable = false)
    private double valorCobrado;

    @Column(name = "endereco_entrega", nullable = false, length = 255)
    private String enderecoEntrega;

    public Pedido(long id, Cliente cliente, LocalDateTime dataHoraPagamento, List<ItemPedido> itens,
            Pedido.Status status, double valor, double impostos, double desconto, double valorCobrado,
            String enderecoEntrega) {
        this.id = id;
        this.cliente = cliente;
        this.dataHoraPagamento = dataHoraPagamento;
        this.itens = itens;
        this.status = status;
        this.valor = valor;
        this.impostos = impostos;
        this.desconto = desconto;
        this.valorCobrado = valorCobrado;
        this.enderecoEntrega = enderecoEntrega;
        // Mantem a ligacao bidirecional: itens_pedido.pedido_id (parte da PK composta) vem do raiz.
        if (itens != null) {
            for (ItemPedido item : itens) {
                item.setPedido(this);
            }
        }
    }

    public long getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public LocalDateTime getDataHoraPagamento() {
        return dataHoraPagamento;
    }

    public void setDataHoraPagamento(LocalDateTime dataHoraPagamento) {
        this.dataHoraPagamento = dataHoraPagamento;
    }

    public List<ItemPedido> getItens() {
        return itens;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status){
        this.status = status;
    }

    public double getValor() {
        return valor;
    }

    public double getImpostos() {
        return impostos;
    }

    public double getDesconto() {
        return desconto;
    }

    public double getValorCobrado() {
        return valorCobrado;
    }

    public String getEnderecoEntrega() {
        return enderecoEntrega;
    }
}
