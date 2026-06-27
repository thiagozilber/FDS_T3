package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.ClienteRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Cliente;

@Repository
public class ClienteRepositoryJDBC implements ClienteRepository {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public ClienteRepositoryJDBC(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Insere um novo cliente na base. CPF e e-mail devem ser únicos; qualquer
    // violação de integridade é traduzida para IllegalArgumentException para
    // manter o mesmo tratamento usado pelos casos de uso da aplicação.
    @Override
    public void salvar(Cliente cliente) {
        String sql = "INSERT INTO clientes (cpf, nome, celular, endereco, email, senha) VALUES (?, ?, ?, ?, ?, ?)";

        try {
            this.jdbcTemplate.update(
                sql,
                ps -> {
                    ps.setString(1, cliente.getCpf());
                    ps.setString(2, cliente.getNome());
                    ps.setString(3, cliente.getCelular());
                    ps.setString(4, cliente.getEndereco());
                    ps.setString(5, cliente.getEmail());
                    ps.setString(6, cliente.getSenha());
                });
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Cliente com este CPF ou email já existe", e);
        }
    }

    // Recupera um cliente pelo CPF. Retorna null caso não exista cadastro para
    // o CPF informado (mesmo contrato usado por PedidoRepository.recuperaPorId).
    @Override
    public Cliente recuperaPorCpf(String cpf) {
        String sql = "SELECT cpf, nome, celular, endereco, email, senha FROM clientes WHERE cpf = ?";

        List<Cliente> clientes = this.jdbcTemplate.query(
            sql,
            ps -> ps.setString(1, cpf),
            (rs, rowNum) -> mapeiaCliente(rs));

        return clientes.isEmpty() ? null : clientes.getFirst();
    }

    // Recupera um cliente pelo e-mail. Este método é utilizado pelo processo de
    // autenticação (UC12). Retorna null caso o e-mail não esteja cadastrado.
    @Override
    public Cliente recuperaPorEmail(String email) {
        String sql = "SELECT cpf, nome, celular, endereco, email, senha FROM clientes WHERE email = ?";

        List<Cliente> clientes = this.jdbcTemplate.query(
            sql,
            ps -> ps.setString(1, email),
            (rs, rowNum) -> mapeiaCliente(rs));

        return clientes.isEmpty() ? null : clientes.getFirst();
    }

    // Mapeia uma linha da tabela clientes para a entidade Cliente. Compartilhado
    // pelos métodos de recuperação para evitar duplicação de código.
    private Cliente mapeiaCliente(ResultSet rs) throws SQLException {
        return new Cliente(
            rs.getString("cpf"),
            rs.getString("nome"),
            rs.getString("celular"),
            rs.getString("endereco"),
            rs.getString("email"),
            rs.getString("senha"));
    }
}