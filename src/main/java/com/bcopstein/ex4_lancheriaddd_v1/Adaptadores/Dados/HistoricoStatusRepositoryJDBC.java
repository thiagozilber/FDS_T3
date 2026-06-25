package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.HistoricoStatusRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.TransicaoStatus;

@Repository
public class HistoricoStatusRepositoryJDBC implements HistoricoStatusRepository {
    private JdbcTemplate jdbcTemplate;

    @Autowired
    public HistoricoStatusRepositoryJDBC(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void registrar(long pedidoId, Pedido.Status status, LocalDateTime quando) {
        String sql = "INSERT INTO historico_status (pedido_id, status, data_hora) VALUES (?, ?, ?)";
        this.jdbcTemplate.update(sql, ps -> {
            ps.setLong(1, pedidoId);
            ps.setString(2, status.name());
            ps.setTimestamp(3, Timestamp.valueOf(quando));
        });
    }

    @Override
    public List<TransicaoStatus> historico(long pedidoId) {
        // Ordena por id como desempate para manter a ordem de insercao em transicoes no mesmo instante.
        String sql = "SELECT status, data_hora FROM historico_status " +
                     "WHERE pedido_id = ? ORDER BY data_hora ASC, id ASC";
        return this.jdbcTemplate.query(
            sql,
            ps -> ps.setLong(1, pedidoId),
            (rs, rowNum) -> new TransicaoStatus(
                Pedido.Status.valueOf(rs.getString("status")),
                rs.getTimestamp("data_hora").toLocalDateTime()));
    }
}
