package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Apresentacao;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.LoginUC;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Requests.LoginRequest;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses.LoginResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final LoginUC loginUC;

    public AuthController(LoginUC loginUC) {
        this.loginUC = loginUC;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return loginUC.run(request);
    }
}