# Implementation Report: P2 — Ciclo do Pedido (Pessoa 1) — UC6–UC9

- **Date:** 2026-06-24
- **Branch:** main
- **Plan:** [`plans/completed/ciclo-do-pedido-pessoa1.plan.md`](../plans/completed/ciclo-do-pedido-pessoa1.plan.md)
- **PRD:** [`tele-pizza-backend.prd.md`](../tele-pizza-backend.prd.md) — phase **P2**, owner **Pessoa 1**

## Summary
Implemented **Pessoa 1's slice of P2**: the order cycle — submit (UC6), status (UC7), cancel (UC8), pay (UC9). Added the `Pedido` aggregate persistence (`pedidos` / `itens_pedido` / `historico_status`), `ServicoPedido` (sole writer of `historico_status` — Seam #2), `ServicoEstoque` (stock-derived availability + decrement), a fake `IPagamentoService`, the four use cases, `PedidoController`, and a temporary `CozinhaServiceStub` standing in for Pessoa 2's kitchen sim (Seam #1). The cost formula `custoFinal = (subtotal − desconto) + imposto` is wired through the existing P1 `ServicoImposto`/`ServicoDesconto`. **Single-pass: compiles on JDK 21, 75/75 tests green, full live HTTP smoke passed.**

## Assessment vs Reality
| Metric | Predicted (plan) | Actual |
|---|---|---|
| Complexity | Large | Large |
| Confidence | 8/10 | Single-pass succeeded (no rework) |
| New main files | ~23 | 26 |
| New test files | 3 | 4 (+`PagarPedidoUCTest`) |
| Edited files | 3 | 3 (`Pedido.java`, `schema.sql`, `RestExceptionHandler.java`) |
| New tests | ~10 | 24 (suite 51 → 75) |

## Tasks Completed
| # | Task | Status | Notes |
|---|---|---|---|
| 1 | Schema: `pedidos` / `itens_pedido` / `historico_status` | ✅ | money `double`, `auto_increment` PKs |
| 2 | `Pedido` entity: +`CANCELADO`/`RECUSADO`, `enderecoEntrega`, `setDataHoraPagamento` | ✅ | |
| 3 | Seam interfaces `IPagamentoService` / `IRegistradorStatus` / `TransicaoStatus` | ✅ | |
| 4 | `PagamentoFake` (`@Service`) | ✅ | always approves |
| 5 | Ports `PedidoRepository` / `HistoricoStatusRepository` / `ItensEstoqueRepository` | ✅ | |
| 6–8 | JDBC adapters (Estoque, Historico, Pedido) | ✅ | N1 generated-key INSERT, N2 UPDATE |
| 9 | `ServicoEstoque` | ✅ | availability derived from stock (D11) |
| 10 | `ServicoPedido` (core, `implements IRegistradorStatus`) | ✅ | sole writer of history (Seam #2) |
| 11 | UCs UC6–UC9 + Request/Response DTOs | ✅ | first `@PostMapping`/`@RequestBody` (D10) |
| 12 | `PedidoController` + Presenters | ✅ | no `@CrossOrigin` (CorsConfig global) |
| 13 | Tests (Estoque, Pedido, SubmeterUC, +PagarUC) | ✅ | hand-rolled fakes, comment-spec headers |
| — | Temp `CozinhaServiceStub` (Seam #1 unblock) | ✅ | **TEMPORARY** — delete on Pessoa 2 integration |

## Validation Results
| Level | Status | Notes |
|---|---|---|
| Static / compile (JDK 21) | ✅ Pass | `mvnw clean compile` EXIT 0 |
| Unit tests | ✅ Pass | 24 new, 75/75 total |
| Build | ✅ Pass | `BUILD SUCCESS` |
| Integration (live HTTP + real H2/JDBC) | ✅ Pass | see evidence below |
| Edge cases | ✅ Pass | empty cart 400, unknown id 404, RECUSADO, invalid transition 400, bad cpf 400 |

**Live HTTP evidence (app on :8090):**
```
POST /pedidos (1x calabresa + 2x margherita) -> {"id":1,"status":"APROVADO","valor":135.0,"desconto":0.0,"impostos":13.5,"valorCobrado":148.5,"itensIndisponiveis":[]}  HTTP 200
GET  /pedidos/1/status -> APROVADO, historico [NOVO, APROVADO] (timestamped)               HTTP 200
POST /pedidos/1/cancelar -> CANCELADO, historico [NOVO, APROVADO, CANCELADO]               HTTP 200
POST /pedidos/1/pagar -> "Pedido nao esta apto a pagamento; status atual: CANCELADO"       HTTP 400
POST /pedidos (pedido 2) + /pedidos/2/pagar -> AGUARDANDO, historico [NOVO,APROVADO,PAGO,AGUARDANDO]  HTTP 200
POST /pedidos (31x calabresa) -> RECUSADO, itensIndisponiveis ["Pizza calabresa"]          HTTP 200
GET  /pedidos/999/status -> "Pedido inexistente: 999"                                       HTTP 404
POST /pedidos (itens []) -> "Pedido sem itens"                                              HTTP 400
POST /pedidos (clienteCpf "0000") -> "Requisicao invalida: dados do pedido inconsistentes" HTTP 400  (post-fix)
```

## Files Changed
- **Created (26 main):** `Dominio/Servicos/{IPagamentoService,IRegistradorStatus,TransicaoStatus,ServicoEstoque,ServicoPedido}`, `Dominio/Servicos/Pagamento/PagamentoFake`, `Dominio/Dados/{PedidoRepository,HistoricoStatusRepository,ItensEstoqueRepository}`, `Adaptadores/Dados/{PedidoRepositoryJDBC,HistoricoStatusRepositoryJDBC,ItensEstoqueRepositoryJDBC}`, `Adaptadores/Config/CozinhaServiceStub`, `Aplicacao/{SubmeterPedidoParaAprovacaoUC,ConsultarStatusPedidoUC,CancelarPedidoUC,PagarPedidoUC}`, `Aplicacao/Requests/{ItemPedidoRequest,SubmeterPedidoRequest}`, `Aplicacao/Responses/{SubmeterPedidoResponse,TransicaoStatusResponse,PedidoStatusResponse}`, `Adaptadores/Apresentacao/PedidoController`, `Adaptadores/Apresentacao/Presenters/{SubmeterPedidoPresenter,TransicaoStatusPresenter,PedidoStatusPresenter}`.
- **Created (4 test):** `ServicoEstoqueTest`, `ServicoPedidoTest`, `Aplicacao/SubmeterPedidoParaAprovacaoUCTest`, `Aplicacao/PagarPedidoUCTest`.
- **Edited (3):** `Dominio/Entidades/Pedido.java`, `src/main/resources/schema.sql`, `Adaptadores/Apresentacao/RestExceptionHandler.java`.

## Deviations from Plan
- **+`PagarPedidoUCTest`** (4th test file, not in the plan's 3) — UC9 orchestration was only smoke-covered; added unit coverage.
- **Temp `CozinhaServiceStub`** placed in `Adaptadores/Config` (the plan left the location open) so the context loads before Pessoa 2 delivers the real `@Service CozinhaService`.
- **`PedidoStatusResponse.de(...)` static factory** added (DRY across UC7/UC8/UC9) — a small bit of logic on a Response record, justified to avoid 3× duplicated mapping.

## Issues Encountered
- **No JDK 21 on PATH** (system default 17; the plan's bundled path was gone). Found one in Android Studio's JBR: `/snap/android-studio/232/jbr`. Build/test/run all use `JAVA_HOME=/snap/android-studio/232/jbr`.
- **Port 8080 occupied** by an unrelated "FreqUI" service → smoke ran on `SERVER_PORT=8090` (verified the welcome banner before trusting results).
- **Offline build fails** (local `~/.m2` lacks the Spring parent POM); the sandbox has network, so the online build works.

## Code Review
Ran an adversarial 4-dimension review (correctness / persistence / clean-arch / security) with a verification stage. **13 findings raised; the verification stage failed mid-run on a session limit**, so findings were triaged manually:
- **Fixed (2):**
  - **Bad client input → 500.** Nonexistent/null `clienteCpf`, over-width fields, or duplicate items raised `DataIntegrityViolationException` → unmapped **500**. Added a handler mapping it to **400** (generic message, no DB-detail leak). *Live-verified.*
  - **Non-atomic order creation.** Added `@Transactional` to `ServicoPedido.submeter` so header + items + history + stock-decrement roll back together. *Live-verified (failed insert left no row).*
- **Dismissed (11):** nested-query-in-RowMapper (the established `ProdutosRepositoryJDBC` pattern, live-proven); produto-multi-receita dup (pre-existing, deferred P3, and `recuperaProdutoPorid` returns one row); null-produto-on-reload (FK prevents it); temp-stub double-bean (loudly documented as temporary); `IRegistradorStatus` unused (it's the frozen Seam #2 contract *for Pessoa 2*); stub `println` (throwaway, matches the real sim's style); H2 console open (pre-existing, deferred to P5 per PRD §13); broad catch-all handler (deliberately not added — would mask bugs; the specific DataIntegrity case is handled); column-width & PagarPedidoUC-reload (low, acceptable for a course backend).

## Next Steps
- [ ] Commit (awaiting user) — not yet committed.
- [ ] **Pessoa 2:** deliver the real Cozinha/Entrega sims (`@Service`), wire status persistence via `IRegistradorStatus` (Seam #2), and **delete `CozinhaServiceStub`**.
- [ ] P3 (JPA), P4 (UC10 + users), P5 (auth — lock down H2 console), P6 (more test drivers).
