package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Apresentacao;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.CadastrarClienteUC;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Requests.CadastrarClienteRequest;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses.CadastrarClienteResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/clientes")
public class ClienteController {

    private final CadastrarClienteUC cadastrarClienteUC;

    public ClienteController(CadastrarClienteUC cadastrarClienteUC) {
        this.cadastrarClienteUC = cadastrarClienteUC;
    }

    // UC11: cadastra um novo cliente.
    // O corpo da requisição é validado automaticamente pelas anotações
    // presentes em CadastrarClienteRequest (@NotBlank, @Size, etc.).
    // As regras de negócio são delegadas ao caso de uso.8
    
    @PostMapping("")
    public CadastrarClienteResponse cadastrar(
            @Valid @RequestBody CadastrarClienteRequest request) {

        return cadastrarClienteUC.run(request);
    }
}