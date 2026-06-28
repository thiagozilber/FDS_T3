package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/*
 * Casos de teste -- Cliente:
 *  1. criaClienteComDadosValidos : verifica se o construtor inicializa corretamente os atributos.
 */
class ClienteTest {

    @Test
    void criaClienteComDadosValidos() {
        Cliente cliente = new Cliente(
                "12345678900",
                "Joao Silva",
                "51999999999",
                "Rua A",
                "joao@email.com",
                "123456");

        assertEquals("12345678900", cliente.getCpf());
        assertEquals("Joao Silva", cliente.getNome());
        assertEquals("51999999999", cliente.getCelular());
        assertEquals("Rua A", cliente.getEndereco());
        assertEquals("joao@email.com", cliente.getEmail());
        assertEquals("123456", cliente.getSenha());
    }
}