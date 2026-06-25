# Implementation Report: P6 Test Drivers — Pessoa 1 slice

## Summary
Wired JaCoCo into the Maven build (the project had no coverage tooling) and closed coverage on
Pessoa 1's four order-cycle test drivers. The three named P6 targets (`ServicoPedido`,
`ServicoEstoque`, `SubmeterPedidoParaAprovacaoUC`) plus `PagarPedidoUC` now sit at **100% line and
100% branch** coverage. The course's "test cases as comments in the same file" requirement was
already satisfied for Pessoa 1's slice (drivers written during P2/UC10); this phase ratified that and
extended the comment blocks with the new cases.

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Small–Medium | Small (no `src/main` change, no IT needed) |
| Confidence | 9/10 | Held — single pass, no rework |
| Files Changed | 1 build + 2–3 test + PRD | 1 build (`pom.xml`) + 2 test + PRD |
| Coverage gap | `historico()` line-uncovered + aggregation | Targets already 100% line; real gaps were 4 null-branches |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 1 | Add JaCoCo to `pom.xml` | ✅ Complete | `jacoco-maven-plugin` 0.8.12, `prepare-agent` + `report`@`test` |
| 2 | Measure baseline | ✅ Complete | All targets already 100% line; only 4 branches missed |
| 3 | Fill `ServicoPedido` gaps | ✅ Complete (deviated) | Re-aimed at null-branches + direct `historico()` case |
| 4 | Fill UC aggregation gap | ✅ Complete | + 2 null-guard branch cases |
| 5 | Conditional JDBC IT | ⏭️ Skipped | Coverage already ≫80% — IT unnecessary |
| 6 | Ratify §6.8 + update PRD | ✅ Complete | Note ratified, P6 row 🟡, Progress Log appended |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | ✅ Pass | `./mvnw compile`/`test` clean under JDK 21 (no separate Java linter configured) |
| Unit Tests | ✅ Pass | 90 total (84→90), 6 new; 0 failures/errors |
| Build | ✅ Pass | BUILD SUCCESS |
| Integration | ✅ Pass | `PedidoRepositoryEntreguesTest` (`@SpringBootTest`, real H2) ran in-suite, green |
| Edge Cases | ✅ Pass | null itens/endereço/request, duplicate-product aggregation, history readback |

## Coverage (targets) — before → after

| Class | Line | Branch |
|---|---|---|
| `ServicoPedido` | 100% → 100% | 91.7% (22/24) → **100% (24/24)** |
| `SubmeterPedidoParaAprovacaoUC` | 100% → 100% | 87.5% (14/16) → **100% (16/16)** |
| `ServicoEstoque` | 100% → 100% | 100% → 100% |
| `PagarPedidoUC` | 100% → 100% | 100% → 100% |

## Files Changed

| File | Action | Lines |
|---|---|---|
| `pom.xml` | UPDATED | +17 (JaCoCo plugin) |
| `src/test/.../Dominio/Servicos/ServicoPedidoTest.java` | UPDATED | +27 (3 cases + comment block) |
| `src/test/.../Aplicacao/SubmeterPedidoParaAprovacaoUCTest.java` | UPDATED | +24 (3 cases + comment block) |
| `.claude/PRPs/tele-pizza-backend.prd.md` | UPDATED | §6.8 ratified, §9 P6 🟡, Progress Log entry |

## Deviations from Plan

1. **Targets were already 100% line at baseline.** The plan assumed `ServicoPedido.historico()` was
   line-uncovered; JaCoCo showed it executed transitively through `PagarPedidoUC.run()` (which
   `PagarPedidoUCTest` exercises). **Why it matters:** the genuine residual gaps were *branch*, not
   *line* — specifically four defensive null-guards.
2. **Task 3/4 re-aimed at the real gaps.** Instead of only a `historico()` happy/404 pair, added
   null-guard cases (`submeter` null itens/endereço; UC null request / null itens) that close the 4
   missed branches, plus the duplicate-product aggregation case and a direct `historico()` read.
   Net: targets reach 100% branch.
3. **Task 5 (optional JDBC IT) skipped** — the ≥80% bar was already exceeded (100%), so an extra
   integration driver added no coverage value (YAGNI).

## Issues Encountered
None. JaCoCo 0.8.12 resolved and ran on first try (network available); JDK 21 build clean.

## Tests Written

| Test File | New Tests | Coverage Added |
|---|---|---|
| `ServicoPedidoTest` | 3 (`historicoRetornaTransicoesDoPedido`, `submeterItensNulosLanca`, `submeterEnderecoNuloLanca`) | `submeter` null branches → 100% branch |
| `SubmeterPedidoParaAprovacaoUCTest` | 3 (`itensDuplicadosSaoAgregados`, `requestNuloLanca`, `itensNulosLanca`) | `run` null branches + aggregation → 100% branch |

## Next Steps
- [ ] Code review via `/code-review`
- [ ] Commit via `/prp-commit` and PR via `/prp-pr`
- [ ] **P3 JPA migration (Pessoa 1)** — remains gated on Seam #3 (Pessoa 2's framework decision); provisional plan ready at `plans/jpa-migration-pessoa1-p3.plan.md`
