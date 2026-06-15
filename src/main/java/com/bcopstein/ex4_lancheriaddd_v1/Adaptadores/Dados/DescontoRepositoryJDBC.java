package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Dados;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.DescontoRepository;

@Component
public class DescontoRepositoryJDBC implements DescontoRepository {
    public static final String CHAVE_CORRENTE = "desconto.corrente";

    private ConfiguracaoRepositoryJDBC configuracaoRepository;

    @Autowired
    public DescontoRepositoryJDBC(ConfiguracaoRepositoryJDBC configuracaoRepository) {
        this.configuracaoRepository = configuracaoRepository;
    }

    @Override
    public String politicaCorrente() {
        return configuracaoRepository.valor(CHAVE_CORRENTE);
    }

    @Override
    public void definePolitica(String codigo) {
        configuracaoRepository.define(CHAVE_CORRENTE, codigo);
    }
}
