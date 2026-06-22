package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Desconto;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

// Fabrica das estrategias de desconto. Espelha FabricaEstrategiaImposto, MAS sem
// bean de configuracao: a politica corrente vem do DescontoRepository em tempo de
// execucao (UC4 troca em runtime), por isso aqui so existe criar(String codigo).
@Component
public class FabricaEstrategiaDesconto {

    private final Map<String, IEstrategiaCalculoDesconto> porCodigo;

    public FabricaEstrategiaDesconto(List<IEstrategiaCalculoDesconto> estrategias) {
        this.porCodigo = estrategias.stream()
                .collect(Collectors.toUnmodifiableMap(
                        IEstrategiaCalculoDesconto::getCodigo,
                        Function.identity()));
    }

    public IEstrategiaCalculoDesconto criar(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalStateException(
                "Politica de desconto corrente nao configurada");
        }
        IEstrategiaCalculoDesconto estrategia = porCodigo.get(codigo);
        if (estrategia == null) {
            throw new IllegalArgumentException(
                "Nenhuma estrategia registrada para a politica: " + codigo
                + ". Politicas disponiveis: " + porCodigo.keySet());
        }
        return estrategia;
    }

    public Set<String> codigosDisponiveis() {
        return porCodigo.keySet();
    }
}
