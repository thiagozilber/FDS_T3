package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Cliente;

// Resposta do POST /clientes (UC11). Por questões de segurança, a senha
// não é exposta na resposta da API.
public record CadastrarClienteResponse(
        String cpf,
        String nome,
        String celular,
        String endereco,
        String email) {

    // Converte a entidade Cliente para o DTO retornado pela aplicação.
    public static CadastrarClienteResponse de(Cliente cliente) {
        if (cliente == null) {
            throw new IllegalArgumentException("Cliente não pode ser nulo.");
        }

        return new CadastrarClienteResponse(
                cliente.getCpf(),
                cliente.getNome(),
                cliente.getCelular(),
                cliente.getEndereco(),
                cliente.getEmail());
    }
}