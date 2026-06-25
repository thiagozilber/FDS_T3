package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses;

// Uma entrada do historico de status (status + instante em ISO-8601).
public record TransicaoStatusResponse(String status, String dataHora) {
}
