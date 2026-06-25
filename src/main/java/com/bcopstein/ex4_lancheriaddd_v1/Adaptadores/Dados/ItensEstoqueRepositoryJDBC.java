package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.ItensEstoqueRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Ingrediente;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.ItemEstoque;

@Repository
public class ItensEstoqueRepositoryJDBC implements ItensEstoqueRepository {
    private JdbcTemplate jdbcTemplate;

    @Autowired
    public ItensEstoqueRepositoryJDBC(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ItemEstoque> recuperaTodos() {
        String sql = "SELECT e.ingrediente_id, e.quantidade, i.descricao " +
                     "FROM itensEstoque e JOIN ingredientes i ON e.ingrediente_id = i.id";
        return this.jdbcTemplate.query(
            sql,
            ps -> { },
            (rs, rowNum) -> new ItemEstoque(
                new Ingrediente(rs.getLong("ingrediente_id"), rs.getString("descricao")),
                rs.getInt("quantidade")));
    }

    @Override
    public void defineQuantidade(long ingredienteId, int novaQuantidade) {
        String sql = "UPDATE itensEstoque SET quantidade = ? WHERE ingrediente_id = ?";
        this.jdbcTemplate.update(sql, ps -> {
            ps.setInt(1, novaQuantidade);
            ps.setLong(2, ingredienteId);
        });
    }
}
