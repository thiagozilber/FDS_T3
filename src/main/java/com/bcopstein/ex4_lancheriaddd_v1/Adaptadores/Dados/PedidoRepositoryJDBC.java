package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.PedidoEntregue;

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
            (rs, rowNum) -> mapeiaPedido(rs));
        return pedidos.isEmpty() ? null : pedidos.getFirst();
    }

    // Mapeia uma linha de pedidos -> Pedido (re-hidratando os itens). Compartilhado por
    // recuperaPorId e entreguesEntre; a query precisa expor as colunas usadas aqui.
    private Pedido mapeiaPedido(ResultSet rs) throws SQLException {
        Cliente cliente = new Cliente(rs.getString("cliente_cpf"), null, null, null, null, null);
        Timestamp pago = rs.getTimestamp("data_hora_pagamento");
        LocalDateTime dataHoraPagamento = (pago == null) ? null : pago.toLocalDateTime();
        List<ItemPedido> itens = recuperaItens(rs.getLong("id"));
        return new Pedido(
            rs.getLong("id"), cliente, dataHoraPagamento, itens,
            Pedido.Status.valueOf(rs.getString("status")),
            rs.getDouble("valor"), rs.getDouble("impostos"),
            rs.getDouble("desconto"), rs.getDouble("valor_cobrado"),
            rs.getString("endereco_entrega"));
    }

    // UC10 (Seam #4 / D15): pedidos cuja transicao ENTREGUE caiu em [ini, fim) -- filtra pelo
    // carimbo em historico_status (pedidos nao tem coluna de data de entrega). Um pedido tem no
    // maximo uma linha ENTREGUE (estado terminal, escritor unico Seam #2), logo nao precisa DISTINCT.
    // A re-hidratacao de itens por linha e N+1, aceitavel na escala do trabalho (espelha recuperaPorId).
    @Override
    public List<PedidoEntregue> entreguesEntre(LocalDateTime ini, LocalDateTime fim) {
        String sql = "SELECT p.id, p.cliente_cpf, p.status, p.valor, p.impostos, p.desconto, " +
                     "p.valor_cobrado, p.data_hora_pagamento, p.endereco_entrega, " +
                     "h.data_hora AS data_hora_entrega " +
                     "FROM pedidos p JOIN historico_status h ON h.pedido_id = p.id " +
                     "WHERE h.status = ? " +
                     "AND h.data_hora >= ? AND h.data_hora < ? " +
                     "ORDER BY h.data_hora ASC, p.id ASC";
        return this.jdbcTemplate.query(
            sql,
            ps -> {
                ps.setString(1, Pedido.Status.ENTREGUE.name());
                ps.setTimestamp(2, Timestamp.valueOf(ini));
                ps.setTimestamp(3, Timestamp.valueOf(fim));
            },
            (rs, rowNum) -> new PedidoEntregue(
                mapeiaPedido(rs),
                rs.getTimestamp("data_hora_entrega").toLocalDateTime()));
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
