# Code Review: `7583515` — feat: UC10 listar pedidos entregues entre datas (Pessoa 1)

**Reviewed:** 2026-06-25
**Author:** thiagozilber
**Scope:** commit `7583515` vs parent `1b665ef` (16 files, +421/−16)
**Decision:** APPROVE with comments — clean vertical, no CRITICAL/HIGH; only LOW-severity cleanups

## Summary

UC10 adds `GET /pedidos/entregues?ini=&fim=`, listing ENTREGUE orders whose delivery transition fell in a day-window. The order is filtered by its `ENTREGUE` timestamp in `historico_status` via a JOIN (decision D15) — no schema change and no mutation of the `Pedido` entity (a `PedidoEntregue` read-model carries the delivery instant). Layering mirrors the established UC6–UC9 slices: Presenter → Controller → UC → Service → `PedidoRepository` port → JDBC adapter. Window math is correct (`[ini 00:00, fim+1 00:00)`, so the whole `fim` day is included), and bad/missing input maps to 400 via the existing `RestExceptionHandler`. The shared `mapeiaPedido` row-mapper was cleanly extracted from `recuperaPorId`.

## Validation

| Check | Result | Notes |
|---|---|---|
| Compile (JDK 21) | Pass | `./mvnw clean test` after installing `openjdk-21-jdk`; `JAVA_HOME` → java-21 |
| Tests | Pass | **84 run, 0 failures, 0 errors, 0 skipped** (16 classes). UC10: `PedidoRepositoryEntreguesTest` (3, real H2 JOIN) + 3 new `ServicoPedidoTest` cases |
| Lint | n/a | none configured |
| SQL injection scan | Pass | timestamp bounds parameterized; status filter is a compile-time enum constant (see L1) |

> Env note: `pom.xml` requires JDK 21; the box defaults to JDK 17 (Surefire fork fails loading class-file v65 under v61). Build with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`.

## Findings

### CRITICAL
None.

### HIGH
None.

### MEDIUM

**M1 — N+1 re-hydration in `entreguesEntre`**
`Adaptadores/Dados/PedidoRepositoryJDBC.java` (`entreguesEntre` / `mapeiaPedido` / `recuperaItens`)
Each joined order row triggers a `recuperaItens` query plus a `recuperaProdutoPorid` per item (N×M round-trips for a wide window). Explicitly documented in-code as acceptable at the assignment's scale (mirrors `recuperaPorId`). Flagged so it stays a conscious tradeoff; no fix required now.
*Fix (if scaled):* single JOIN over `itens_pedido` + `ResultSetExtractor`, or batch product lookups.

**M2 — Unbounded result set**
`Adaptadores/Apresentacao/PedidoController.java` (`entregues`), `PedidoRepositoryJDBC.entreguesEntre`
No `LIMIT`/pagination; a large date window loads every matching order into memory. Fine for coursework scale; note for production.

### LOW

- **L1** `PedidoRepositoryJDBC.java` (`entreguesEntre`) — status filter built by string concatenation: `"WHERE h.status = '" + Pedido.Status.ENTREGUE.name() + "'"`. Provably injection-safe (compile-time enum constant, never user input) but breaks the parameterized style used everywhere else. Prefer a `?` bind parameter for consistency. **(fixed in follow-up fix pass)**
- **L2** `Dominio/Servicos/ServicoPedidoTest.java` (UC10 doc header) — references `PedidoRepositoryEntreguesIT`, but the class is `PedidoRepositoryEntreguesTest`. Comment rot. **(fixed in follow-up fix pass)**
- **L3** `Aplicacao/ListarPedidosEntreguesUC.run` and `Dominio/Servicos/ServicoPedido.listarEntreguesEntre` duplicate identical null / `ini.isAfter(fim)` guards. Reads as intentional defense-in-depth (UC validates raw dates; service validates the expanded window); the service-layer branch can't fire from the UC path. Acceptable.
- **L4** `Adaptadores/Dados/PedidoRepositoryEntreguesTest.java` — cleans in `@BeforeEach` but no `@AfterEach`; ids 9101–9103 persist in the shared H2 context after the class runs. Harmless today (high ids; `data_hora_pagamento` null so loyalty counts unaffected). An `@AfterEach` would make isolation explicit.

### Notes (pre-existing, not introduced here)
- Money as `double` throughout (`valorCobrado`, etc.) — the codebase's existing convention (decision D6), out of scope for this commit.

## Verified clean
SQL injection (timestamp bounds parameterized; status is an enum constant) · payment-amount tampering (read-only endpoint) · hardcoded secrets · path traversal / SSRF · the half-open window correctly includes the full `fim` day · 400 mapping confirmed for bad range, missing param, and malformed date.

## Recommended follow-ups
1. **L1** bind-parameter the `ENTREGUE` status filter (consistency). — applied
2. **L2** fix the `PedidoRepositoryEntreguesIT` comment reference. — applied
3. **L4** (optional) add `@AfterEach` cleanup to the integration test.
