# Implementation Report: UC10 — Listar pedidos entregues entre 2 datas (Pessoa 1)

## Summary
Implemented UC10: `GET /pedidos/entregues?ini=&fim=` returns the orders whose **ENTREGUE** transition
falls within a `[ini, fim]` day-window. Filters by the `ENTREGUE` timestamp in `historico_status` via a
JOIN (decision **D15**) — no schema change, no entity mutation. Full vertical added (read-model record →
repository port + JDBC query → service method → use case → Response DTOs → controller endpoint + presenter)
mirroring the existing order-cycle stack. Unblocked, fully Pessoa-1-owned (Seam #4).

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Small–Medium | Small–Medium (as predicted) |
| Confidence | 9/10 | 9/10 — single-pass, no rework |
| Files Changed | 7 new, 2 edited | 6 new, 7 edited |

> Files delta: the plan counted the IT as the 7th "new" and 2 production edits; reality added edits to
> **two extra test fakes** (`PagarPedidoUCTest`, `SubmeterPedidoParaAprovacaoUCTest`) that implement the
> `PedidoRepository` port and had to gain the new method — anticipated as a deviation during execution.

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 1 | `PedidoEntregue` read-model record | ✅ Complete | In `Dominio.Servicos` (mirrors `TransicaoStatus`) |
| 2 | `PedidoRepository.entreguesEntre` port | ✅ Complete | Half-open `[ini, fim)` documented |
| 3 | JDBC JOIN query | ✅ Complete | Extracted shared `mapeiaPedido` row-mapper; reused by `recuperaPorId` |
| 4 | `ServicoPedido.listarEntreguesEntre` | ✅ Complete | null + `ini>fim` guards; read-only |
| 5 | Response DTOs | ✅ Complete | `PedidoEntregueResponse` + `PedidosEntreguesResponse.de(...)` |
| 6 | `ListarPedidosEntreguesUC` | ✅ Complete | Owns `LocalDate`→window expansion |
| 7 | Presenter + `GET /entregues` | ✅ Complete | Literal route, no collision with `/{id}/...` |
| 8 | Unit + integration tests | ✅ Complete | 3 service unit cases + 3 integration cases |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis / Compile | ✅ Pass | `./mvnw -o compile` clean, JDK 21 |
| Unit Tests | ✅ Pass | 84 total, 0 failures, 0 errors (was 78) |
| Build | ✅ Pass | offline build succeeds (cached deps) |
| Integration | ✅ Pass | `PedidoRepositoryEntreguesTest` (real H2 JOIN) + live HTTP smoke |
| Edge Cases | ✅ Pass | empty window `[]`; bad range 400; missing param 400; no `/status` regression (404) |

### Live smoke (port 8090, JDK 21)
```
GET /pedidos/entregues?ini=2026-06-01&fim=2026-06-30   -> []        (200)
GET /pedidos/entregues?ini=2026-06-30&fim=2026-06-01   -> 400
GET /pedidos/entregues?ini=2026-06-01                   -> 400
GET /pedidos/999999/status                              -> 404      (no regression)
```

## Files Changed

| File | Action | Notes |
|---|---|---|
| `Dominio/Servicos/PedidoEntregue.java` | CREATED | read-model record |
| `Aplicacao/Responses/PedidoEntregueResponse.java` | CREATED | per-order DTO |
| `Aplicacao/Responses/PedidosEntreguesResponse.java` | CREATED | list DTO + `de` factory |
| `Aplicacao/ListarPedidosEntreguesUC.java` | CREATED | `@Component` UC |
| `Adaptadores/Apresentacao/Presenters/PedidoEntreguePresenter.java` | CREATED | view-model |
| `src/test/.../Adaptadores/Dados/PedidoRepositoryEntreguesTest.java` | CREATED | `@SpringBootTest` IT |
| `Dominio/Dados/PedidoRepository.java` | UPDATED | + `entreguesEntre` port method |
| `Adaptadores/Dados/PedidoRepositoryJDBC.java` | UPDATED | + JOIN query, + `mapeiaPedido` helper |
| `Dominio/Servicos/ServicoPedido.java` | UPDATED | + `listarEntreguesEntre` |
| `Adaptadores/Apresentacao/PedidoController.java` | UPDATED | + ctor dep + `GET /entregues` |
| `src/test/.../Dominio/Servicos/ServicoPedidoTest.java` | UPDATED | + 3 UC10 cases, fake `entreguesEntre`, `repoPedido` field |
| `src/test/.../Aplicacao/PagarPedidoUCTest.java` | UPDATED | fake `entreguesEntre` override (compile) |
| `src/test/.../Aplicacao/SubmeterPedidoParaAprovacaoUCTest.java` | UPDATED | fake `entreguesEntre` override (compile) |

## Deviations from Plan

1. **Integration test named `...Test`, not `...IT`.** This project runs only Surefire (`*Test`/`*Tests`);
   `*IT` requires the Failsafe plugin, which is not configured — an `IT`-named class would be silently
   skipped by `./mvnw test`. Renamed to `PedidoRepositoryEntreguesTest` (mirrors `Ex4LancheriadddV1ApplicationTests`).
2. **Two extra test fakes edited.** Adding `entreguesEntre` to the `PedidoRepository` interface broke the
   fake implementations in `PagarPedidoUCTest` and `SubmeterPedidoParaAprovacaoUCTest`; each gained a
   one-line `return List.of()` override. Not in the plan's file list but necessary for compilation.
3. **Window boundary semantics verified in the IT, not the service unit test.** The fake's `entreguesEntre`
   does pure delegation (returns a seeded list) rather than re-implementing the SQL filter — so the
   `[ini, fim)` boundary/JOIN behavior is asserted against real H2 in `PedidoRepositoryEntreguesTest`,
   while the service unit tests cover validation + delegation. This is more honest than testing a fake's
   re-implementation of the filter.
4. **Row-mapper refactor.** Extracted the shared `Pedido` mapping from `recuperaPorId` into a private
   `mapeiaPedido(ResultSet)` helper, reused by both `recuperaPorId` and `entreguesEntre` (DRY).

## Issues Encountered
None blocking. The `-q` flag suppresses the Surefire summary line — confirmed pass counts by aggregating
`target/surefire-reports/*.xml`. Offline build (`-o`) works (deps cached); no network needed this run.

## Tests Written

| Test File | Tests | Coverage |
|---|---|---|
| `ServicoPedidoTest` (extended) | +3 | null/`ini>fim` validation, repo delegation |
| `PedidoRepositoryEntreguesTest` (new) | 3 | real SQL JOIN: in-window returned w/ timestamp, out-of-window excluded, no-ENTREGUE excluded |

## Next Steps
- [ ] Code review via `/code-review`
- [ ] Record **D15** + Seam #4 resolution in PRD §13 (done in this pass)
- [ ] Commit via `/prp-commit`; PR via `/prp-pr`
- [ ] Pessoa 1's remaining task: JPA-of-own-repos (P3) — still gated on Seam #3 (Pessoa 2's framework decision)
