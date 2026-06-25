# Code Review: `078d770` — feat: order cycle UC6-UC9 (Pessoa 1, P2)

**Reviewed:** 2026-06-24
**Author:** thiagozilber
**Scope:** unpushed commit `078d770` vs `origin/main` (36 files, +1701/−11)
**Decision:** REQUEST CHANGES — solid, working slice; two real concurrency/data-integrity bugs to address before push

## Summary

A well-structured, working order cycle (UC6 submit / UC7 status / UC8 cancel / UC9 pay) on a DDD/hexagonal Spring Boot base. The hexagonal boundary is respected (ports in `Dominio/Dados`, JDBC adapters in `Adaptadores/Dados`), `ServicoPedido` is the sole writer of `historico_status` (Seam #2), and SQL is fully parameterized. Reviewed by two specialized agents (java-reviewer, security-reviewer) plus an independent line-by-line pass; findings below are de-duplicated, re-graded for this project's context, and adversarially verified. The headline issue — a stock check-then-decrement race — was independently flagged by both agents and confirmed by manual read.

## Validation

| Check | Result | Notes |
|---|---|---|
| Compile | Skipped | pom requires **JDK 21**; this env only has **JDK 17** |
| Tests | Skipped (env) | Surefire fork failed loading class-file v65 (Java 21) under JDK 17. Prior session reports **75/75 green on JDK 21**. Not a code defect. |
| Lint | n/a | none configured |
| SQL injection scan | Pass | all JDBC queries parameterized (`?` placeholders) |

## Findings

### CRITICAL
None.

### HIGH

**H1 — Stock oversell race (TOCTOU) — non-atomic check-then-decrement**
`Dominio/Servicos/ServicoPedido.java:67-89`, `Dominio/Servicos/ServicoEstoque.java:65-78`, `Adaptadores/Dados/ItensEstoqueRepositoryJDBC.java:34-41`
`haDisponibilidade()` (read) and `baixaEstoque()` (write) are separate round-trips, and `defineQuantidade` writes an app-computed **absolute** value (`SET quantidade = ?`) rather than an atomic decrement. `@Transactional` on `submeter` runs at READ_COMMITTED, which does not serialize this. Two concurrent submits for the last unit both pass the check and both decrement → negative stock / oversell. Confirmed by both agents + manual read.
*Fix:* atomic guarded decrement `UPDATE itensEstoque SET quantidade = quantidade - ? WHERE ingrediente_id = ? AND quantidade >= ?` and reject when 0 rows affected; or `SELECT ... FOR UPDATE` on the stock rows inside the transaction.

**H2 — Status transitions are not atomic; cancel/pay lack a transaction boundary**
`Dominio/Servicos/ServicoPedido.java:107-125`, `Aplicacao/PagarPedidoUC.java:29-45`
`registrarTransicao` does two writes (`atualizaStatus` + `historico.registrar`) with no `@Transactional`; `cancelar` is read-then-write; `PagarPedidoUC.run` fires three transitions + a kitchen handoff with no outer transaction. A crash between writes leaves `pedidos.status` and `historico_status` inconsistent. The `APROVADO` guard in `pagar` is also racy (two concurrent `pagar` calls can both pass it → double pay).
*Fix:* `@Transactional(propagation = REQUIRED)` on `registrarTransicao`; `@Transactional` on `cancelar` and on the `pagar` orchestration.

### MEDIUM

**M1 — Cancellation does not restore decremented stock**
`Dominio/Servicos/ServicoPedido.java:107-114`
`submeter` decrements stock at approval; `cancelar` records `CANCELADO` but never replenishes. Every cancelled order permanently consumes ingredients. NOTE: UC8 spec does **not** require restock, so this is a design gap to confirm with the team, not a spec violation.
*Fix (if intended):* add `devolveEstoque()` (mirror of `baixaEstoque`) called from `cancelar` in-transaction.

**M2 — Payment failure → HTTP 500 and leaks internal message**
`Adaptadores/Apresentacao/RestExceptionHandler.java:41-43`, `Aplicacao/PagarPedidoUC.java:35-37`
A failed `processarPagamento` throws `IllegalStateException` → mapped to 500 with `ex.getMessage()` (includes order id / internal state). A declined payment is a business condition (402/409), not a server error. (PagamentoFake always returns `true` today, so latent until a real gateway lands.)
*Fix:* dedicated exception → 402/409; generic 500 body, log details server-side.

**M3 — N+1 queries in `recuperaItens` RowMapper**
`Adaptadores/Dados/PedidoRepositoryJDBC.java:92-101`
Per item row, `produtosRepository.recuperaProdutoPorid` fires further queries; `recuperaPorId` is called 2–3× per `pagar`. Pre-existing pattern, amplified here.
*Fix:* single JOIN + `ResultSetExtractor`.

**M4 — No bean validation; `clienteCpf` null/blank/length unchecked**
`Aplicacao/Requests/SubmeterPedidoRequest.java:6`, `Aplicacao/SubmeterPedidoParaAprovacaoUC.java:58`, `Adaptadores/Apresentacao/PedidoController.java:42`
Quantities and product existence are validated, but `clienteCpf` is unchecked (null → NPE path; unknown → generic 400 via FK). No `@Valid`/`@NotBlank`/`@Size`.
*Fix:* add `spring-boot-starter-validation`, annotate DTOs, `@Valid` on the body.

**M5 — Unbounded `quantidade` / `itens` size (resource-exhaustion vector)**
`Aplicacao/Requests/ItemPedidoRequest.java:4`, `Aplicacao/Requests/SubmeterPedidoRequest.java:6`
`quantidade` is `int` (no upper cap) and `itens` is unbounded; large payloads inflate the stock-aggregation loop and transaction. Lower risk given no auth/rate-limit anyway, but cheap to bound.
*Fix:* `@Max` on quantity, `@Size(max=...)` on items.

**M6 — Unauthenticated state-changing endpoints (IDOR) introduced here**
`Adaptadores/Apresentacao/PedidoController.java:41-61`
All four routes are added without auth/ownership checks; sequential `auto_increment` ids are trivially enumerable (read/cancel/pay any order). Auth is explicitly deferred to phase **P5** by a documented decision, so acceptable for this slice — but the unauthenticated surface goes live now. Track it.
*Fix (P5):* authenticate, derive CPF server-side, verify ownership before status/cancel/pay.

### LOW

- **L1** `Adaptadores/Dados/PedidoRepositoryJDBC.java:56` — `keyHolder.getKey().longValue()` unguarded; latent NPE if no generated key. Add a null check. (Agent rated CRITICAL; downgraded — H2 auto_increment always returns a key.)
- **L2** `Dominio/Entidades/Pedido.java:57-71` — public `setDataHoraPagamento` (added here, **never called**) and `setStatus` (pre-existing) undermine the Seam #2 single-writer contract and the immutability rule. Remove the dead setter; route mutations through `registrarTransicao`.
- **L3** non-`final` constructor-injected fields across the new controllers/UCs/adapters (inconsistent with `ServicoPedido`). Add `final`. (Agent rated HIGH; downgraded — style only.)
- **L4** `PedidoController.java:41-46` — `POST /pedidos` returns 200, not 201 + `Location`.
- **L5** `Dominio/Servicos/ServicoEstoque.java:75` — `baixaEstoque` can write negative qty with no guard (tied to H1).
- **L6** `Dominio/Servicos/ServicoPedido.java:68-73` — RECUSADO row inserted as NOVO then updated; extra write, benign under tx isolation.
- **L7** `Adaptadores/Config/CozinhaServiceStub.java:15` — `System.out.println`; temporary, documented. Gate with `@Profile`/delete when Pessoa 2 lands.
- **L8** `src/main/resources/schema.sql:103` — missing trailing newline.
- **L9** Money as `double` (entity + schema) — accepted decision **D6** (rounds via `Math.round(v*100)/100.0`). Revisit with `BigDecimal`/`DECIMAL(10,2)` at the P3 JPA migration.

### Dismissed (verified, not issues)
- "UC injects `ProdutosRepository` directly = architecture leak" — application use cases depending on domain repository **ports** is standard hexagonal/clean architecture. Not a violation.

## Pre-existing config (NOT introduced by this commit — `application.yaml`/`CorsConfig` from initial commit `92bf0f6`; flagged for project-level attention)
- H2 console enabled at `/h2` with `sa` / blank password (`application.yaml`).
- `org.springframework.jdbc: DEBUG` logs all SQL + params (`application.yaml`).
- CORS `allowed-origins` defaults to `*` (`CorsConfig`).
- No rate limiting on endpoints.

→ Recommend a `dev`/`prod` profile split: disable H2 console, raise log level, set explicit CORS origins for non-dev.

## Verified clean
SQL injection (all parameterized) · payment-amount tampering (amount derived server-side; `pagar` takes only the path id) · hardcoded secrets · insecure deserialization · path traversal / SSRF · mass assignment · XXE.

## Top recommended fixes before push
1. **H1** atomic guarded stock decrement.
2. **H2** transactional status transitions (`registrarTransicao`, `cancelar`, `pagar`).
3. **M2** payment-decline status code + no internal leak.
4. **M4** bean validation on request DTOs.
5. **M1** decide & document cancel-restock behavior.
