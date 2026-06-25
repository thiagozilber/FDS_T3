package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;

// Seam #2 (Pessoa 1): callback estreito implementado por ServicoPedido (unico escritor de
// historico_status). As simulacoes de Cozinha/Entrega (Pessoa 2) dependem DESTE port, nao da
// classe concreta ServicoPedido, evitando ciclo de beans e mantendo o escritor unico.
public interface IRegistradorStatus {
    void registrarTransicao(long pedidoId, Pedido.Status novo);
}
