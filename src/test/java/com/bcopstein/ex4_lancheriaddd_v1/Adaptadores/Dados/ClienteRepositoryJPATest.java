package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.ClienteRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Cliente;

/*
 * Casos de teste -- ClienteRepositoryJPA:
 *  1. salvaCliente
 *  2. recuperaPorCpfExistente
 *  3. recuperaPorCpfInexistenteRetornaNull
 *  4. recuperaPorEmailExistente
 *  5. recuperaPorEmailInexistenteRetornaNull
 */
@SpringBootTest
@Transactional
class ClienteRepositoryJPATest {

    @Autowired
    private ClienteRepository clienteRepository;

    private Cliente novoCliente() {
        return new Cliente(
                "12345678900",
                "Joao Silva",
                "51999999999",
                "Rua A",
                "joao@email.com",
                "123456");
    }

    @Test
    void salvaCliente() {

        Cliente cliente = novoCliente();

        clienteRepository.salvar(cliente);

        Cliente recuperado =
                clienteRepository.recuperaPorCpf(cliente.getCpf());

        assertNotNull(recuperado);
        assertEquals(cliente.getCpf(), recuperado.getCpf());
    }

    @Test
    void recuperaPorCpfExistente() {

        Cliente cliente = novoCliente();

        clienteRepository.salvar(cliente);

        Cliente recuperado =
                clienteRepository.recuperaPorCpf("12345678900");

        assertNotNull(recuperado);
        assertEquals("Joao Silva", recuperado.getNome());
    }

    @Test
    void recuperaPorCpfInexistenteRetornaNull() {

        Cliente recuperado =
                clienteRepository.recuperaPorCpf("00000000000");

        assertNull(recuperado);
    }

    @Test
    void recuperaPorEmailExistente() {

        Cliente cliente = novoCliente();

        clienteRepository.salvar(cliente);

        Cliente recuperado =
                clienteRepository.recuperaPorEmail("joao@email.com");

        assertNotNull(recuperado);
        assertEquals(cliente.getCpf(), recuperado.getCpf());
    }

    @Test
    void recuperaPorEmailInexistenteRetornaNull() {

        Cliente recuperado =
                clienteRepository.recuperaPorEmail("naoexiste@email.com");

        assertNull(recuperado);
    }
}