package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Seguranca;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Portas.RepositorioSessao;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Portas.Sessao;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Papel;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Valida o token Bearer e aplica a autorizacao por papel.
// 401 quando o token falta ou e desconhecido; 403 quando a rota e de admin e o papel nao e ADMIN.
// Escreve o status diretamente na resposta (preHandle roda antes do @RestControllerAdvice).
@Component
public class AutenticacaoInterceptor implements HandlerInterceptor {

    private final RepositorioSessao repositorioSessao;

    public AutenticacaoInterceptor(RepositorioSessao repositorioSessao) {
        this.repositorioSessao = repositorioSessao;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            return true; // deixa o preflight de CORS passar
        }

        String header = req.getHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;

        Sessao sessao = repositorioSessao.validar(token);
        if (sessao == null) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Autenticacao requerida.");
            return false;
        }

        if (exigeAdmin(req.getRequestURI()) && sessao.papel() != Papel.ADMIN) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "Acesso restrito ao administrador.");
            return false;
        }

        req.setAttribute("sessao", sessao);
        return true;
    }

    // ADMIN: /descontos/** e /cardapio/** (exceto o GET /cardapio/corrente, que e do cliente - UC5).
    private boolean exigeAdmin(String path) {
        if (path.startsWith("/descontos")) {
            return true;
        }
        if (path.equals("/cardapio/corrente")) {
            return false; // UC5 (cliente autenticado)
        }
        return path.startsWith("/cardapio"); // UC1/UC2 + GET /cardapio/{id}
    }
}
