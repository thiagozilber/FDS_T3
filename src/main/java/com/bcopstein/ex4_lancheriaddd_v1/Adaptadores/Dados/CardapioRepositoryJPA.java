package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.CardapioRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.ProdutosRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.CabecalhoCardapio;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Cardapio;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Produto;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Repository
public class CardapioRepositoryJPA implements CardapioRepository {

    public static final String CHAVE_CORRENTE = "cardapio.corrente";

    @PersistenceContext
    private EntityManager entityManager;

    private final ProdutosRepository produtosRepository;
    private final ConfiguracaoRepositoryJDBC configuracaoRepository;

    @Autowired
    public CardapioRepositoryJPA(
            ProdutosRepository produtosRepository,
            ConfiguracaoRepositoryJDBC configuracaoRepository) {

        this.produtosRepository = produtosRepository;
        this.configuracaoRepository = configuracaoRepository;
    }

    @Override
    public Cardapio recuperaPorId(long id) {

        Object[] linha = (Object[]) entityManager.createNativeQuery("""
                SELECT id, titulo
                FROM cardapios
                WHERE id = ?
                """)
            .setParameter(1, id)
            .getResultStream()
            .findFirst()
            .orElse(null);

        if (linha == null) {
            return null;
        }

        long cardapioId = ((Number) linha[0]).longValue();
        String titulo = (String) linha[1];

        Cardapio cardapio = new Cardapio(
                new CabecalhoCardapio(cardapioId, titulo),
                null);

        cardapio.setProdutos(produtosRepository.recuperaProdutosCardapio(cardapioId));

        return cardapio;
    }

    @Override
    public List<Produto> indicacoesDoChef() {
        Produto sugestao = produtosRepository.recuperaProdutoPorid(2L);
        return sugestao == null ? List.of() : List.of(sugestao);
    }

    @Override
    public List<CabecalhoCardapio> cardapiosDisponiveis() {

        @SuppressWarnings("unchecked")
        List<Object[]> linhas = entityManager.createNativeQuery("""
                SELECT id, titulo
                FROM cardapios
                ORDER BY id
                """)
            .getResultList();

        return linhas.stream()
            .map(l -> new CabecalhoCardapio(
                    ((Number) l[0]).longValue(),
                    (String) l[1]))
            .toList();
    }

    @Override
    public void defineCorrente(long id) {
        configuracaoRepository.define(CHAVE_CORRENTE, String.valueOf(id));
    }

    @Override
    public Cardapio recuperaCorrente() {

        String valor = configuracaoRepository.valor(CHAVE_CORRENTE);

        if (valor == null) {
            return null;
        }

        try {
            return recuperaPorId(Long.parseLong(valor));
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Valor invalido para cardapio corrente na configuracao", e);
        }
    }
}