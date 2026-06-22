package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// CORS centralizado e configuravel (substitui o @CrossOrigin("*") repetido nos controllers).
// Origens permitidas via app.cors.allowed-origins (CSV); default "*" para o ambiente de dev/demo.
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    private final String[] origensPermitidas;

    public CorsConfig(@Value("${app.cors.allowed-origins:*}") String[] origensPermitidas) {
        this.origensPermitidas = origensPermitidas;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(origensPermitidas)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}
