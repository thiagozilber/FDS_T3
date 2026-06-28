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

// Modelo de persistencia JPA da tabela "historico_status" (Seam #2: escrita exclusiva de ServicoPedido).
// Mapeia o pedido por id (pedido_id), sem associacao a PedidoJpaEntity, mantendo o registro simples.
@Entity
@Table(name = "historico_status")
@Getter
@Setter
@NoArgsConstructor
public class HistoricoStatusJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // historico_status.id e auto_increment
    private Long id;

    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Pedido.Status status;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    public HistoricoStatusJpaEntity(Long pedidoId, Pedido.Status status, LocalDateTime dataHora) {
        this.pedidoId = pedidoId;
        this.status = status;
        this.dataHora = dataHora;
    }
}
