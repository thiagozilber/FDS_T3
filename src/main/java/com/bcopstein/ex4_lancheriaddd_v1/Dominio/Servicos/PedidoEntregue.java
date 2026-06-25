package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos;

import java.time.LocalDateTime;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;

// Read-model de dominio (UC10): um pedido entregue + o instante da transicao ENTREGUE
// (lido de historico_status, D15). Carrega a data de entrega sem mutar a entidade Pedido.
public record PedidoEntregue(Pedido pedido, LocalDateTime dataHoraEntrega) {
}
