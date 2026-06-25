package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.PedidoRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.ProdutosRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Cliente;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.ItemPedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Produto;

@Repository
public class PedidoRepositoryJDBC implements PedidoRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ProdutosRepository produtosRepository;

    @Autowired
    public PedidoRepositoryJDBC(JdbcTemplate jdbcTemplate, ProdutosRepository produtosRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.produtosRepository = produtosRepository;
    }

    @Override
    public long salvar(Pedido pedido) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        this.jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO pedidos (cliente_cpf, status, valor, impostos, desconto, valor_cobrado, " +
                "data_hora_pagamento, endereco_entrega) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, pedido.getCliente().getCpf());
            ps.setString(2, pedido.getStatus().name());
            ps.setDouble(3, pedido.getValor());
            ps.setDouble(4, pedido.getImpostos());
            ps.setDouble(5, pedido.getDesconto());
            ps.setDouble(6, pedido.getValorCobrado());
            if (pedido.getDataHoraPagamento() == null) {
                ps.setNull(7, Types.TIMESTAMP);
            } else {
                ps.setTimestamp(7, Timestamp.valueOf(pedido.getDataHoraPagamento()));
            }
            ps.setString(8, pedido.getEnderecoEntrega());
            return ps;
        }, keyHolder);
        Number chaveGerada = keyHolder.getKey();
        if (chaveGerada == null) {
            throw new IllegalStateException("Falha ao obter o id gerado do pedido");
        }
        long pedidoId = chaveGerada.longValue();

        for (ItemPedido item : pedido.getItens()) {
            this.jdbcTemplate.update(
                "INSERT INTO itens_pedido (pedido_id, produto_id, quantidade) VALUES (?, ?, ?)",
                ps -> {
                    ps.setLong(1, pedidoId);
                    ps.setLong(2, item.getItem().getId());
                    ps.setInt(3, item.getQuantidade());
                });
        }
        return pedidoId;
    }

    @Override
    public Pedido recuperaPorId(long id) {
        String sql = "SELECT id, cliente_cpf, status, valor, impostos, desconto, valor_cobrado, " +
                     "data_hora_pagamento, endereco_entrega FROM pedidos WHERE id = ?";
        List<Pedido> pedidos = this.jdbcTemplate.query(
            sql,
            ps -> ps.setLong(1, id),
            (rs, rowNum) -> {
                Cliente cliente = new Cliente(rs.getString("cliente_cpf"), null, null, null, null);
                Timestamp pago = rs.getTimestamp("data_hora_pagamento");
                LocalDateTime dataHoraPagamento = (pago == null) ? null : pago.toLocalDateTime();
                List<ItemPedido> itens = recuperaItens(rs.getLong("id"));
                return new Pedido(
                    rs.getLong("id"), cliente, dataHoraPagamento, itens,
                    Pedido.Status.valueOf(rs.getString("status")),
                    rs.getDouble("valor"), rs.getDouble("impostos"),
                    rs.getDouble("desconto"), rs.getDouble("valor_cobrado"),
                    rs.getString("endereco_entrega"));
            });
        return pedidos.isEmpty() ? null : pedidos.getFirst();
    }

    private List<ItemPedido> recuperaItens(long pedidoId) {
        String sql = "SELECT produto_id, quantidade FROM itens_pedido WHERE pedido_id = ?";
        return this.jdbcTemplate.query(
            sql,
            ps -> ps.setLong(1, pedidoId),
            (rs, rowNum) -> {
                Produto produto = produtosRepository.recuperaProdutoPorid(rs.getLong("produto_id"));
                return new ItemPedido(produto, rs.getInt("quantidade"));
            });
    }

    @Override
    public void atualizaStatus(long id, Pedido.Status novo) {
        this.jdbcTemplate.update(
            "UPDATE pedidos SET status = ? WHERE id = ?",
            ps -> {
                ps.setString(1, novo.name());
                ps.setLong(2, id);
            });
    }

    @Override
    public void atualizaDataHoraPagamento(long id, LocalDateTime quando) {
        this.jdbcTemplate.update(
            "UPDATE pedidos SET data_hora_pagamento = ? WHERE id = ?",
            ps -> {
                ps.setTimestamp(1, Timestamp.valueOf(quando));
                ps.setLong(2, id);
            });
    }

    @Override
    public int contarPedidosPagosCliente(String cpf, LocalDateTime desde) {
        String sql = "SELECT COUNT(*) FROM pedidos WHERE cliente_cpf = ? " +
                     "AND data_hora_pagamento IS NOT NULL AND data_hora_pagamento >= ?";
        Integer count = this.jdbcTemplate.queryForObject(
            sql, Integer.class, cpf, Timestamp.valueOf(desde));
        return count == null ? 0 : count;
    }
}
