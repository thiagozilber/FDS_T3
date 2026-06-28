package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Cliente;

// Resposta do login (UC12). Nao expor senha por seguranca.
public record LoginResponse(
		String cpf,
		String nome,
		String email) {

	public static LoginResponse de(Cliente cliente) {
		if (cliente == null) {
			throw new IllegalArgumentException("Cliente nao pode ser nulo.");
		}

		return new LoginResponse(
				cliente.getCpf(),
				cliente.getNome(),
				cliente.getEmail());
	}
}
