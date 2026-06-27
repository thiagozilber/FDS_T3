package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// UC11: corpo da requisição de cadastro de cliente.
// Contém os dados obrigatórios para criação de um novo cliente,
// incluindo as credenciais de autenticação.
public record CadastrarClienteRequest(

        @NotBlank
        @Size(max = 15)
        String cpf,

        @NotBlank
        @Size(max = 100)
        String nome,

        @NotBlank
        @Size(max = 20)
        String celular,

        @NotBlank
        @Size(max = 255)
        String endereco,

        @NotBlank
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 6, max = 255)
        String senha
) {
}