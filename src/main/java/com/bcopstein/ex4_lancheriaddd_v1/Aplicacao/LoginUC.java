package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Requests.LoginRequest;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses.LoginResponse;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Cliente;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.ServicoCliente;

@Component
public class LoginUC {

	private final ServicoCliente servicoCliente;

	@Autowired
	public LoginUC(ServicoCliente servicoCliente) {
		this.servicoCliente = servicoCliente;
	}

	public LoginResponse run(LoginRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("Requisicao de login nao pode ser nula.");
		}

		Cliente cliente = servicoCliente.autenticar(request.email(), request.senha());
		return LoginResponse.de(cliente);
	}
}
