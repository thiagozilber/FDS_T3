package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

// Entidade JPA correspondente à tabela "clientes".
// Representa um cliente cadastrado no sistema e é utilizada
// tanto pelo domínio quanto pela camada de persistência.
@Entity
@Table(name = "clientes")
@NoArgsConstructor
public class Cliente {

    @Id
    @Column(name = "cpf", nullable = false, length = 15)
    private String cpf;

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Column(name = "celular", nullable = false, length = 20)
    private String celular;

    @Column(name = "endereco", nullable = false, length = 255)
    private String endereco;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "senha", nullable = false, length = 255)
    private String senha;

    // Construtor utilizado pelo domínio para criação de novos clientes.
    public Cliente(String cpf,
                   String nome,
                   String celular,
                   String endereco,
                   String email,
                   String senha) {
        this.cpf = cpf;
        this.nome = nome;
        this.celular = celular;
        this.endereco = endereco;
        this.email = email;
        this.senha = senha;
    }

    public String getCpf() {
        return cpf;
    }

    public String getNome() {
        return nome;
    }

    public String getCelular() {
        return celular;
    }

    public String getEndereco() {
        return endereco;
    }

    public String getEmail() {
        return email;
    }

    public String getSenha() {
        return senha;
    }
}