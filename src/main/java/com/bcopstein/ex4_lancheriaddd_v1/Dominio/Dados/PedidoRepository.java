package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados;

import java.time.LocalDateTime;
import java.util.List;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.PedidoEntregue;

// Porta de persistencia do agregado Pedido (cabecalho + itens). Os itens_pedido sao persistidos
// junto do raiz do agregado (nao tem identidade propria) -- decisao D14 do plano.
public interface PedidoRepository {
    long salvar(Pedido pedido);                       // INSERT pedidos + itens_pedido; retorna o id gerado
    Pedido recuperaPorId(long id);                    // cabecalho + itens (re-hidrata Produto); null se ausente
    void atualizaStatus(long id, Pedido.Status novo);
    void atualizaDataHoraPagamento(long id, LocalDateTime quando);
    int contarPedidosPagosCliente(String cpf, LocalDateTime desde); // contagem de fidelidade (D12)
    // UC10 (Seam #4): pedidos com transicao ENTREGUE no intervalo [ini, fim) -- ini inclusivo, fim exclusivo.
    List<PedidoEntregue> entreguesEntre(LocalDateTime ini, LocalDateTime fim);
}
