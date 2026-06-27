package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Cliente;

public interface ClienteRepository {

    void salvar(Cliente cliente);

    Cliente recuperaPorCpf(String cpf);

    Cliente recuperaPorEmail(String email);

}