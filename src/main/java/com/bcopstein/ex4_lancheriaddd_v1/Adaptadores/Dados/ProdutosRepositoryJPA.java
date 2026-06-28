package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.ProdutosRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Produto;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

// Implementação JPA do repositório de produtos.
@Repository
public class ProdutosRepositoryJPA implements ProdutosRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Produto recuperaProdutoPorid(long id) {
        return entityManager.find(Produto.class, id);
    }

    @Override
    public List<Produto> recuperaProdutosCardapio(long cardapioId) {

    List<?> ids = entityManager.createNativeQuery("""
            SELECT produto_id
            FROM cardapio_produto
            WHERE cardapio_id = ?
            ORDER BY produto_id
            """)
        .setParameter(1, cardapioId)
        .getResultList();

    List<Produto> produtos = new ArrayList<>();

    for (Object obj : ids) {
        Number id = (Number) obj;
        Produto produto = entityManager.find(Produto.class, id.longValue());

        if (produto != null) {
            produtos.add(produto);
        }
    }

    return produtos;
    }
}