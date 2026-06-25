package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.PedidoRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.PedidoEntregue;

/*
 * Driver de integracao -- PedidoRepositoryJDBC.entreguesEntre (UC10, exercita o SQL/JOIN real em H2):
 *  1. retornaEntreguesNoIntervalo : pedido com ENTREGUE em [ini, fim) -> retornado com dataHoraEntrega correta
 *  2. excluiForaDoIntervalo       : pedido com ENTREGUE apos fim -> NAO retornado
 *  3. excluiSemEntregue           : pedido apenas PAGO (sem linha ENTREGUE) -> NAO retornado
 *
 * Usa cliente semeado '9001' (data.sql) e ids de pedido altos (9101-9103) para nao colidir com
 * seeds nem com pedidos criados em runtime. @BeforeEach limpa e re-insere para isolar cada teste.
 */
@SpringBootTest
class PedidoRepositoryEntreguesTest {
    private static final long ID_NA_JANELA = 9101L;
    private static final long ID_FORA_JANELA = 9102L;
    private static final long ID_SO_PAGO = 9103L;
    private static final LocalDateTime ENTREGA_NA_JANELA = LocalDateTime.of(2026, 6, 15, 19, 42, 11);
    private static final LocalDateTime ENTREGA_FORA = LocalDateTime.of(2026, 7, 10, 12, 0, 0);
    private static final LocalDateTime INI = LocalDateTime.of(2026, 6, 1, 0, 0);
    private static final LocalDateTime FIM = LocalDateTime.of(2026, 7, 1, 0, 0); // exclusivo

    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void preparaDados() {
        jdbc.update("DELETE FROM historico_status WHERE pedido_id IN (?,?,?)",
            ID_NA_JANELA, ID_FORA_JANELA, ID_SO_PAGO);
        jdbc.update("DELETE FROM pedidos WHERE id IN (?,?,?)",
            ID_NA_JANELA, ID_FORA_JANELA, ID_SO_PAGO);

        inserePedido(ID_NA_JANELA, Pedido.Status.ENTREGUE);
        insereHistorico(ID_NA_JANELA, Pedido.Status.ENTREGUE, ENTREGA_NA_JANELA);

        inserePedido(ID_FORA_JANELA, Pedido.Status.ENTREGUE);
        insereHistorico(ID_FORA_JANELA, Pedido.Status.ENTREGUE, ENTREGA_FORA);

        inserePedido(ID_SO_PAGO, Pedido.Status.PAGO);
        insereHistorico(ID_SO_PAGO, Pedido.Status.PAGO, ENTREGA_NA_JANELA); // mesmo instante, mas status != ENTREGUE
    }

    private void inserePedido(long id, Pedido.Status status) {
        jdbc.update("INSERT INTO pedidos (id, cliente_cpf, status, valor, impostos, desconto, " +
                    "valor_cobrado, data_hora_pagamento, endereco_entrega) VALUES (?,?,?,?,?,?,?,?,?)",
            id, "9001", status.name(), 135.0, 13.5, 0.0, 148.5, null, "Rua X, 10");
    }

    private void insereHistorico(long pedidoId, Pedido.Status status, LocalDateTime quando) {
        jdbc.update("INSERT INTO historico_status (pedido_id, status, data_hora) VALUES (?,?,?)",
            pedidoId, status.name(), Timestamp.valueOf(quando));
    }

    private boolean contemPedido(List<PedidoEntregue> lista, long id) {
        return lista.stream().anyMatch(pe -> pe.pedido().getId() == id);
    }

    @Test
    void retornaEntreguesNoIntervalo() {
        List<PedidoEntregue> resultado = pedidoRepository.entreguesEntre(INI, FIM);
        assertTrue(contemPedido(resultado, ID_NA_JANELA), "pedido entregue na janela deve ser retornado");
        PedidoEntregue alvo = resultado.stream()
            .filter(pe -> pe.pedido().getId() == ID_NA_JANELA).findFirst().orElseThrow();
        assertEquals(ENTREGA_NA_JANELA, alvo.dataHoraEntrega());
        assertEquals(Pedido.Status.ENTREGUE, alvo.pedido().getStatus());
    }

    @Test
    void excluiForaDoIntervalo() {
        List<PedidoEntregue> resultado = pedidoRepository.entreguesEntre(INI, FIM);
        assertFalse(contemPedido(resultado, ID_FORA_JANELA), "entrega apos 'fim' nao deve ser retornada");
    }

    @Test
    void excluiSemEntregue() {
        List<PedidoEntregue> resultado = pedidoRepository.entreguesEntre(INI, FIM);
        assertFalse(contemPedido(resultado, ID_SO_PAGO), "pedido sem transicao ENTREGUE nao deve ser retornado");
    }
}
