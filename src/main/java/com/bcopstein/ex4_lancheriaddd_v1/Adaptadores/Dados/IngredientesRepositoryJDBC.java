package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.IngredientesRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Ingrediente;

@Repository
public class IngredientesRepositoryJDBC implements IngredientesRepository{
    private JdbcTemplate jdbcTemplate;

    @Autowired
    public IngredientesRepositoryJDBC(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Ingrediente> recuperaTodos() {
        String sql = "SELECT id, descricao FROM ingredientes";
        List<Ingrediente> ingredientes = this.jdbcTemplate.query(
                sql,
                ps -> {
                }, // Sem parâmetros
                (rs, rowNum) -> new Ingrediente(rs.getLong("id"), rs.getString("descricao")));
        return ingredientes;
    }

    @Override
    public List<Ingrediente> recuperaIngredientesReceita(long id) {
        String sql = "SELECT i.id, i.descricao FROM ingredientes i " +
                "JOIN receita_ingrediente ri ON i.id = ri.ingrediente_id " +
                "WHERE ri.receita_id = ?";
        return jdbcTemplate.query(
                sql,
                ps -> ps.setLong(1, id),
                (rs, rowNum) -> new Ingrediente(rs.getLong("id"), rs.getString("descricao")));
    }
}
