package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos;

import java.time.LocalDateTime;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;

// Valor de dominio: uma transicao de status carimbada (usada por UC7 e pelo historico).
public record TransicaoStatus(Pedido.Status status, LocalDateTime dataHora) {
}
