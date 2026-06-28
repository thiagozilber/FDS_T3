package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Requests.CadastrarClienteRequest;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses.CadastrarClienteResponse;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Cliente;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.ServicoCliente;

@Component
public class CadastrarClienteUC {

    private final ServicoCliente servicoCliente;

    @Autowired
    public CadastrarClienteUC(ServicoCliente servicoCliente) {
        this.servicoCliente = servicoCliente;
    }

    // UC11: cadastra um novo cliente a partir dos dados enviados pela API.
    // As regras de negócio (validação, unicidade de CPF/e-mail etc.) são
    // delegadas ao ServicoCliente.
    public CadastrarClienteResponse run(CadastrarClienteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Requisição de cadastro não pode ser nula.");
        }

        Cliente cliente = criaCliente(request);

        servicoCliente.cadastrar(cliente);

        return CadastrarClienteResponse.de(cliente);
    }

    // Converte o DTO recebido pela aplicação para a entidade de domínio.
    private Cliente criaCliente(CadastrarClienteRequest request) {
        return new Cliente(
            request.cpf(),
            request.nome(),
            request.celular(),
            request.endereco(),
            request.email(),
            request.senha()
        );
    }
}