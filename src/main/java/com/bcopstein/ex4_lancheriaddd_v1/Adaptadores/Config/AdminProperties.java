package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// Liga o prefixo "app.admin" do application.yaml a este bean.
// Admin unico definido por configuracao (NAO e um cliente do banco).
// Exemplo:
//   app:
//     admin:
//       email: "admin@pizzaria.com"
//       senha: "admin123"
@Component
@ConfigurationProperties(prefix = "app.admin")
public class AdminProperties {
    private String email;
    private String senha;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }
}
