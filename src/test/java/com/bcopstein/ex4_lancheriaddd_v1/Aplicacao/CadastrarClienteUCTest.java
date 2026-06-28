package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Requests.CadastrarClienteRequest;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses.CadastrarClienteResponse;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.ClienteRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Cliente;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.ServicoCliente;

/*
 * Casos de teste -- CadastrarClienteUC:
 *  1. cadastraClienteValidoRetornaResponse
 *  2. requisicaoNulaLancaIllegalArgumentException
 */
class CadastrarClienteUCTest {

    private static class FakeClienteRepository implements ClienteRepository {

        Cliente ultimoCliente;

        @Override
        public void salvar(Cliente cliente) {
            ultimoCliente = cliente;
        }

        @Override
        public Cliente recuperaPorCpf(String cpf) {
            return null;
        }

        @Override
        public Cliente recuperaPorEmail(String email) {
            return null;
        }
    }

    @Test
    void cadastraClienteValidoRetornaResponse() {

        FakeClienteRepository repository = new FakeClienteRepository();

        ServicoCliente servico = new ServicoCliente(repository);

        CadastrarClienteUC uc = new CadastrarClienteUC(servico);

        CadastrarClienteRequest request =
            new CadastrarClienteRequest(
                "12345678900",
                "João",
                "51999999999",
                "Rua A",
                "joao@email.com",
                "123456"
            );

        CadastrarClienteResponse response = uc.run(request);

        assertNotNull(response);

        assertEquals("12345678900", response.cpf());
        assertEquals("João", response.nome());
        assertEquals("51999999999", response.celular());
        assertEquals("Rua A", response.endereco());
        assertEquals("joao@email.com", response.email());

        assertNotNull(repository.ultimoCliente);
        assertEquals("12345678900", repository.ultimoCliente.getCpf());
    }

    @Test
    void requisicaoNulaLancaIllegalArgumentException() {

        FakeClienteRepository repository = new FakeClienteRepository();

        ServicoCliente servico = new ServicoCliente(repository);

        CadastrarClienteUC uc = new CadastrarClienteUC(servico);

        assertThrows(
            IllegalArgumentException.class,
            () -> uc.run(null)
        );
    }
}