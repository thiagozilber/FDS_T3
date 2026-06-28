package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.ClienteRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Cliente;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

// Implementação JPA da porta ClienteRepository.
// Responsável pela persistência e recuperação de clientes utilizando EntityManager,
// substituindo a implementação baseada em JdbcTemplate durante a migração para JPA.
@Repository
public class ClienteRepositoryJPA implements ClienteRepository {

	@PersistenceContext
	private EntityManager entityManager;

	// Persiste um novo cliente no banco de dados.
	// A unicidade de CPF e e-mail é garantida pelas restrições da tabela.
	@Override
	@Transactional
	public void salvar(Cliente cliente) {
		try {
			this.entityManager.persist(cliente);
			this.entityManager.flush();
		} catch (PersistenceException | DataIntegrityViolationException e) {
			throw new IllegalArgumentException("Cliente com este CPF ou email já existe", e);
		}
	}

	// Recupera um cliente utilizando o CPF como chave primária.
	// Retorna null caso o cliente não exista.
	@Override
	public Cliente recuperaPorCpf(String cpf) {
		return this.entityManager.find(Cliente.class, cpf);
	}

	// Recupera um cliente a partir do e-mail utilizando JPQL.
	// Retorna null quando nenhum cliente é encontrado.
	@Override
	public Cliente recuperaPorEmail(String email) {
		try {
			return this.entityManager
				.createQuery(
					"select c from Cliente c where c.email = :email",
					Cliente.class)
				.setParameter("email", email)
				.getSingleResult();
		} catch (NoResultException e) {
			return null;
		}
	}
}