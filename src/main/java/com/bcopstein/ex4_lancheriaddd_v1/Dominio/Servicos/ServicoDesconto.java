package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos;

import java.util.Set;

import org.springframework.stereotype.Service;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.DescontoRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto.ContextoDesconto;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto.FabricaEstrategiaDesconto;

@Service
public class ServicoDesconto {
    private final FabricaEstrategiaDesconto fabrica;
    private final DescontoRepository descontoRepository;

    public ServicoDesconto(FabricaEstrategiaDesconto fabrica, DescontoRepository descontoRepository) {
        this.fabrica = fabrica;
        this.descontoRepository = descontoRepository;
    }

    public double calcularDesconto(double subtotalItens, ContextoDesconto contexto) {
        return fabrica.criar(descontoRepository.politicaCorrente()).calcular(subtotalItens, contexto);
    }

    public Set<String> listarPoliticas() {
        return fabrica.codigosDisponiveis();
    }

    public String getPoliticaCorrente() {
        return descontoRepository.politicaCorrente();
    }

    public void definirPolitica(String codigo) {
        fabrica.criar(codigo); // valida: lanca IllegalArgumentException (-> HTTP 400) se desconhecido
        descontoRepository.definePolitica(codigo);
    }
}
