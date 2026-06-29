package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses;

// Resposta do login (UC12). Inclui o token Bearer e o papel do usuario.
// NUNCA expoe a senha. Para o admin (via config) o cpf vem nulo.
public record LoginResponse(
        String token,
        String cpf,
        String nome,
        String email,
        String papel) {
}
