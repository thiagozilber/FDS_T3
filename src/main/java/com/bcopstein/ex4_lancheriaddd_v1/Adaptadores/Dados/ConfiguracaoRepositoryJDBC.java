package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// Helper de infraestrutura (camada de adaptadores) para o estado corrente chave/valor.
// Unico ponto com escrita JDBC no projeto: MERGE = upsert no H2 (usa a PK 'chave').
@Repository
public class ConfiguracaoRepositoryJDBC {
    private JdbcTemplate jdbcTemplate;

    @Autowired
    public ConfiguracaoRepositoryJDBC(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String valor(String chave) {
        String sql = "SELECT valor FROM configuracao WHERE chave = ?";
        List<String> valores = this.jdbcTemplate.query(
            sql,
            ps -> ps.setString(1, chave),
            (rs, rowNum) -> rs.getString("valor")
        );
        return valores.isEmpty() ? null : valores.getFirst();
    }

    public void define(String chave, String valor) {
        // H2 MERGE usa a PK (chave) como chave de conflito: insere ou atualiza.
        String sql = "MERGE INTO configuracao (chave, valor) VALUES (?, ?)";
        this.jdbcTemplate.update(sql, ps -> {
            ps.setString(1, chave);
            ps.setString(2, valor);
        });
    }
}
