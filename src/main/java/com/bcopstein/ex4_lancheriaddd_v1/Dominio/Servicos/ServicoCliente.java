package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.ClienteRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Cliente;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Excecoes.RecursoNaoEncontradoException;

@Service
public class ServicoCliente {

    private final ClienteRepository clienteRepository;

    @Autowired
    public ServicoCliente(ClienteRepository clienteRepository) {
        this.clienteRepository = clienteRepository;
    }

    // UC11: cadastra um novo cliente validando os campos obrigatórios e
    // garantindo a unicidade do CPF e do e-mail antes da persistência.
    public void cadastrar(Cliente cliente) {
        validarCliente(cliente);

        if (clienteRepository.recuperaPorCpf(cliente.getCpf()) != null) {
            throw new IllegalArgumentException(
                "Cliente com este CPF já cadastrado: " + cliente.getCpf());
        }

        if (clienteRepository.recuperaPorEmail(cliente.getEmail()) != null) {
            throw new IllegalArgumentException(
                "Cliente com este email já cadastrado: " + cliente.getEmail());
        }

        clienteRepository.salvar(cliente);
    }

    // Recupera um cliente pelo CPF. Lança exceção caso o cliente não exista.
    public Cliente recuperaPorCpf(String cpf) {
        validarTextoObrigatorio(cpf, "CPF");

        Cliente cliente = clienteRepository.recuperaPorCpf(cpf);

        if (cliente == null) {
            throw new RecursoNaoEncontradoException(
                "Cliente inexistente: " + cpf);
        }

        return cliente;
    }

    // Recupera um cliente pelo e-mail. Utilizado principalmente pelos casos
    // de uso relacionados à autenticação.
    public Cliente recuperaPorEmail(String email) {
        validarTextoObrigatorio(email, "email");

        Cliente cliente = clienteRepository.recuperaPorEmail(email);

        if (cliente == null) {
            throw new RecursoNaoEncontradoException(
                "Cliente inexistente: " + email);
        }

        return cliente;
    }

    // UC12: autentica um cliente utilizando e-mail e senha. Por segurança,
    // retorna sempre a mesma mensagem para e-mail inexistente ou senha incorreta.
    public Cliente autenticar(String email, String senha) {
        validarTextoObrigatorio(email, "email");
        validarTextoObrigatorio(senha, "senha");

        Cliente cliente = clienteRepository.recuperaPorEmail(email);

        if (cliente == null || !senha.equals(cliente.getSenha())) {
            throw new IllegalArgumentException("Credenciais inválidas.");
        }

        return cliente;
    }

    // Valida se todos os campos obrigatórios do cadastro foram informados.
    private void validarCliente(Cliente cliente) {
        if (cliente == null) {
            throw new IllegalArgumentException("Cliente não pode ser nulo.");
        }

        validarTextoObrigatorio(cliente.getCpf(), "CPF");
        validarTextoObrigatorio(cliente.getNome(), "nome");
        validarTextoObrigatorio(cliente.getCelular(), "celular");
        validarTextoObrigatorio(cliente.getEndereco(), "endereco");
        validarTextoObrigatorio(cliente.getEmail(), "email");
        validarTextoObrigatorio(cliente.getSenha(), "senha");
    }

    // Validação comum para campos textuais obrigatórios.
    private void validarTextoObrigatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                campo + " não pode ser vazio.");
        }
    }
}