package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;

// Seam #1 (Pessoa 1): contrato de pagamento. Implementacao fake retorna sempre sucesso.
// Sincrono (boolean) e nao fire-and-forget: o UC9 precisa da resposta imediata para decidir PAGO.
public interface IPagamentoService {
    boolean processarPagamento(Pedido pedido);
}
