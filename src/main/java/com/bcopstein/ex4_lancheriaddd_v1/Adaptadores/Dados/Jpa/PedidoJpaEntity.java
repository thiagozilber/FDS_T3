package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados.Jpa;

import java.time.LocalDateTime;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Modelo de persistencia JPA da tabela "pedidos" (Approach B / Seam #3 — separado da entidade de
// dominio Pedido, que permanece um POJO puro). As associacoes sao mapeadas por id (cliente_cpf,
// e os itens por pedido_id em ItemPedidoJpaEntity) para NAO arrastar as entidades da Pessoa 2
// (Produto/Receita/Ingrediente) para o JPA. O adaptador faz o mapeamento entidade<->dominio.
@Entity
@Table(name = "pedidos")
@Getter
@Setter
@NoArgsConstructor
public class PedidoJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // pedidos.id e auto_increment no schema.sql
    private Long id;

    @Column(name = "cliente_cpf", nullable = false, length = 15)
    private String clienteCpf;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Pedido.Status status;

    @Column(name = "valor", nullable = false)
    private double valor;

    @Column(name = "impostos", nullable = false)
    private double impostos;

    @Column(name = "desconto", nullable = false)
    private double desconto;

    @Column(name = "valor_cobrado", nullable = false)
    private double valorCobrado;

    @Column(name = "data_hora_pagamento")
    private LocalDateTime dataHoraPagamento;

    @Column(name = "endereco_entrega", nullable = false, length = 255)
    private String enderecoEntrega;
}
