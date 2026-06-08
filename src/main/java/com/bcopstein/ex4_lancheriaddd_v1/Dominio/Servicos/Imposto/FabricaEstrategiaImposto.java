package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Imposto;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Config.ImpostoProperties;

// Factory: resolve a estrategia de calculo de imposto associada a lei vigente.
// Recebe TODAS as estrategias registradas como @Component e indexa por codigo da lei.
// Para adicionar uma nova lei basta criar uma nova implementacao de
// IEstrategiaCalculoImposto - nenhuma alteracao aqui (OCP).
@Component
public class FabricaEstrategiaImposto {

    private final Map<String, IEstrategiaCalculoImposto> porCodigo;
    private final ImpostoProperties properties;

    public FabricaEstrategiaImposto(List<IEstrategiaCalculoImposto> estrategias,
                                    ImpostoProperties properties) {
        this.porCodigo = estrategias.stream()
                .collect(Collectors.toUnmodifiableMap(
                        IEstrategiaCalculoImposto::getCodigoLei,
                        Function.identity()));
        this.properties = properties;
    }

    public IEstrategiaCalculoImposto criar() {
        return criar(properties.getLeiVigente());
    }

    public IEstrategiaCalculoImposto criar(String codigoLei) {
        if (codigoLei == null || codigoLei.isBlank()) {
            throw new IllegalStateException(
                "Lei vigente nao configurada (imposto.lei-vigente em application.yaml)");
        }
        IEstrategiaCalculoImposto estrategia = porCodigo.get(codigoLei);
        if (estrategia == null) {
            throw new IllegalArgumentException(
                "Nenhuma estrategia registrada para a lei: " + codigoLei
                + ". Leis disponiveis: " + porCodigo.keySet());
        }
        return estrategia;
    }
}
