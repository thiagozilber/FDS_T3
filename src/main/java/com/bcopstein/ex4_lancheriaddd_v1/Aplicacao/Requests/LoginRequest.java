package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// UC12: corpo da requisicao de login. O usuario e o e-mail.
public record LoginRequest(
	@NotBlank @Size(max = 255) String email,
	@NotBlank @Size(min = 6, max = 255) String senha) {
}
