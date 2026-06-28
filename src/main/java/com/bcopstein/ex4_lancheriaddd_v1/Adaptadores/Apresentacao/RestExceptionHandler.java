package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Apresentacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Excecoes.CredenciaisInvalidasException;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Excecoes.EstoqueInsuficienteException;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Excecoes.PagamentoRecusadoException;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Excecoes.RecursoNaoEncontradoException;

// Mapeamento global de excecoes -> HTTP.
// Recurso inexistente => 404; argumento invalido / codigo desconhecido / parametro mal formado => 400;
// erro de configuracao => 500.
@RestControllerAdvice
public class RestExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<String> handleNaoEncontrado(RecursoNaoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<String> handleCredenciaisInvalidas(CredenciaisInvalidasException ex) {
        // Falha de autenticacao (UC12): e-mail inexistente ou senha incorreta => 401 Unauthorized.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
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

    @ExceptionHandler(EstoqueInsuficienteException.class)
    public ResponseEntity<String> handleEstoqueInsuficiente(EstoqueInsuficienteException ex) {
        // Corrida: o estoque esgotou entre a verificacao e a baixa atomica => 409 (o cliente pode repetir).
        return ResponseEntity.status(HttpStatus.CONFLICT).body("Estoque insuficiente; tente novamente.");
    }

    @ExceptionHandler(PagamentoRecusadoException.class)
    public ResponseEntity<String> handlePagamentoRecusado(PagamentoRecusadoException ex) {
        // Pagamento recusado: condicao de negocio (NAO erro do servidor) => 402 Payment Required.
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidacao(MethodArgumentNotValidException ex) {
        // Falha de Bean Validation (@Valid) no corpo da requisicao => 400 com a primeira violacao.
        String detalhe = ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .orElse("dados invalidos");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Requisicao invalida: " + detalhe);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalState(IllegalStateException ex) {
        // Erro interno inesperado: registra o detalhe no servidor e devolve mensagem generica (sem vazamento).
        log.error("Erro interno (IllegalState): {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("Erro interno. Por favor, tente novamente.");
    }
}
