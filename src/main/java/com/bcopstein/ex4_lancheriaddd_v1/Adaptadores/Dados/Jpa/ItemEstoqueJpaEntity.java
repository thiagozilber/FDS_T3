package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados.Jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Modelo de persistencia JPA da tabela "itensEstoque". O id NAO e auto_increment no schema
// (e semeado em data.sql), portanto sem @GeneratedValue. O ingrediente e referenciado por id
// (ingrediente_id), sem associacao a Ingrediente (entidade da Pessoa 2, fora do JPA — Approach B).
@Entity
@Table(name = "itensEstoque")
@Getter
@Setter
@NoArgsConstructor
public class ItemEstoqueJpaEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "quantidade")
    private int quantidade;

    @Column(name = "ingrediente_id")
    private Long ingredienteId;
}
