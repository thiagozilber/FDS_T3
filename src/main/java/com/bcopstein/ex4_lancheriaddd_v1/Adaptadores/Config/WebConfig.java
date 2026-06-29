package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Seguranca.AutenticacaoInterceptor;

// Registra o interceptor de autenticacao (P5). Rotas publicas ficam de fora:
//   "/"            -> welcome
//   "/auth/login"  -> UC12 login (emite o token)
//   "/clientes"    -> UC11 cadastro anonimo
//   "/h2", "/h2/**"-> console H2 (demo)
//   "/error"       -> dispatch de erro do Spring
// Mantido separado do CorsConfig (cada WebMvcConfigurer com uma responsabilidade).
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AutenticacaoInterceptor autenticacaoInterceptor;

    public WebConfig(AutenticacaoInterceptor autenticacaoInterceptor) {
        this.autenticacaoInterceptor = autenticacaoInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(autenticacaoInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/", "/auth/login", "/clientes", "/h2", "/h2/**", "/error");
    }
}
