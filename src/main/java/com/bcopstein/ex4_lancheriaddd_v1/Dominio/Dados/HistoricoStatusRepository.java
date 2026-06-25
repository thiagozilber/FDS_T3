package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados;

import java.time.LocalDateTime;
import java.util.List;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.TransicaoStatus;

// Porta do historico de status do pedido. Escrita feita exclusivamente por ServicoPedido (Seam #2).
public interface HistoricoStatusRepository {
    void registrar(long pedidoId, Pedido.Status status, LocalDateTime quando);
    List<TransicaoStatus> historico(long pedidoId); // ordenado por data_hora asc
}
