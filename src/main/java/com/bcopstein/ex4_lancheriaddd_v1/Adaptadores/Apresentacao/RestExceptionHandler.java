package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Apresentacao;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Excecoes.RecursoNaoEncontradoException;

// Mapeamento global de excecoes -> HTTP.
// Recurso inexistente => 404; argumento invalido / codigo desconhecido / parametro mal formado => 400;
// erro de configuracao => 500.
@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<String> handleNaoEncontrado(RecursoNaoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<String> handleTipoInvalido(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Parametro invalido: " + ex.getName());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> handleIntegridade(DataIntegrityViolationException ex) {
        // Violacao de constraint causada por entrada do cliente (cliente_cpf inexistente, item duplicado,
        // campo acima do limite) => 400. Mensagem generica para nao vazar detalhes do banco.
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body("Requisicao invalida: dados do pedido inconsistentes (cliente inexistente ou valores invalidos)");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
    }
}
