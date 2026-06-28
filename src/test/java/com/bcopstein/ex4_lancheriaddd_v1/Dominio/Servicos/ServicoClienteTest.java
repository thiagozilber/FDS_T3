package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.ClienteRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Cliente;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Excecoes.CredenciaisInvalidasException;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Excecoes.RecursoNaoEncontradoException;

/*
 * Casos de teste -- ServicoCliente:
 *  1. cadastraClienteValido
 *  2. cadastroCpfDuplicadoLanca
 *  3. cadastroEmailDuplicadoLanca
 *  4. cadastroCampoObrigatorioVazioLanca
 *  5. recuperaClienteExistentePorCpf
 *  6. recuperaClienteInexistentePorCpfLanca404
 *  7. recuperaClienteExistentePorEmail
 *  8. recuperaClienteInexistentePorEmailLanca404
 *  9. autenticaComCredenciaisValidas
 * 10. autenticaSenhaIncorretaLanca
 * 11. autenticaEmailInexistenteLanca
 */

class ServicoClienteTest {

    private static class FakeClienteRepository implements ClienteRepository {

        private final Map<String, Cliente> clientesCpf = new HashMap<>();
        private final Map<String, Cliente> clientesEmail = new HashMap<>();

        @Override
        public void salvar(Cliente cliente) {
            clientesCpf.put(cliente.getCpf(), cliente);
            clientesEmail.put(cliente.getEmail(), cliente);
        }

        @Override
        public Cliente recuperaPorCpf(String cpf) {
            return clientesCpf.get(cpf);
        }

        @Override
        public Cliente recuperaPorEmail(String email) {
            return clientesEmail.get(email);
        }
    }

    private Cliente cliente() {
        return new Cliente(
                "12345678900",
                "João",
                "51999999999",
                "Rua A",
                "joao@email.com",
                "123456");
    }

    private ServicoCliente novoServico() {
        return new ServicoCliente(new FakeClienteRepository());
    }

    @Test
    void cadastraClienteValido() {
        ServicoCliente servico = novoServico();

        Cliente cliente = cliente();

        servico.cadastrar(cliente);

        assertEquals(cliente, servico.recuperaPorCpf(cliente.getCpf()));
    }

    @Test
    void cadastroCpfDuplicadoLanca() {
        ServicoCliente servico = novoServico();

        servico.cadastrar(cliente());

        Cliente outro = new Cliente(
                "12345678900",
                "Maria",
                "51888888888",
                "Rua B",
                "maria@email.com",
                "654321");

        assertThrows(IllegalArgumentException.class,
                () -> servico.cadastrar(outro));
    }

    @Test
    void cadastroEmailDuplicadoLanca() {
        ServicoCliente servico = novoServico();

        servico.cadastrar(cliente());

        Cliente outro = new Cliente(
                "99999999999",
                "Maria",
                "51888888888",
                "Rua B",
                "joao@email.com",
                "654321");

        assertThrows(IllegalArgumentException.class,
                () -> servico.cadastrar(outro));
    }

    @Test
    void cadastroCampoObrigatorioVazioLanca() {
        ServicoCliente servico = novoServico();

        Cliente cliente = new Cliente(
                "",
                "João",
                "51999999999",
                "Rua A",
                "joao@email.com",
                "123456");

        assertThrows(IllegalArgumentException.class,
                () -> servico.cadastrar(cliente));
    }

    @Test
    void recuperaClienteExistentePorCpf() {
        ServicoCliente servico = novoServico();

        Cliente cliente = cliente();

        servico.cadastrar(cliente);

        assertEquals(cliente,
                servico.recuperaPorCpf("12345678900"));
    }

    @Test
    void recuperaClienteInexistentePorCpfLanca404() {
        ServicoCliente servico = novoServico();

        assertThrows(RecursoNaoEncontradoException.class,
                () -> servico.recuperaPorCpf("000"));
    }

    @Test
    void recuperaClienteExistentePorEmail() {
        ServicoCliente servico = novoServico();

        Cliente cliente = cliente();

        servico.cadastrar(cliente);

        assertEquals(cliente,
                servico.recuperaPorEmail("joao@email.com"));
    }

    @Test
    void recuperaClienteInexistentePorEmailLanca404() {
        ServicoCliente servico = novoServico();

        assertThrows(RecursoNaoEncontradoException.class,
                () -> servico.recuperaPorEmail("teste@email.com"));
    }

    @Test
    void autenticaComCredenciaisValidas() {
        ServicoCliente servico = novoServico();

        Cliente cliente = cliente();

        servico.cadastrar(cliente);

        assertEquals(cliente,
                servico.autenticar("joao@email.com", "123456"));
    }

    @Test
    void autenticaSenhaIncorretaLanca() {
        ServicoCliente servico = novoServico();

        servico.cadastrar(cliente());

        assertThrows(CredenciaisInvalidasException.class,
                () -> servico.autenticar("joao@email.com", "errada"));
    }

    @Test
    void autenticaEmailInexistenteLanca() {
        ServicoCliente servico = novoServico();

        assertThrows(CredenciaisInvalidasException.class,
                () -> servico.autenticar("naoexiste@email.com", "123456"));
    }
}