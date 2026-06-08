package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// Liga o prefixo "imposto" do application.yaml a este bean.
// Exemplo:
//   imposto:
//     lei-vigente: "0412/2022"
@Component
@ConfigurationProperties(prefix = "imposto")
public class ImpostoProperties {
    private String leiVigente;

    public String getLeiVigente() {
        return leiVigente;
    }

    public void setLeiVigente(String leiVigente) {
        this.leiVigente = leiVigente;
    }
}
