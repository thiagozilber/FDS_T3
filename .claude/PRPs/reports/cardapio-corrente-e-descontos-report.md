# Implementation Report: P1 — Cardápio corrente + Serviço de Descontos (UC1–UC5)

- **Date:** 2026-06-09
- **Branch:** `feat/cardapio-corrente-e-descontos`
- **Plan:** `.claude/PRPs/plans/completed/cardapio-corrente-e-descontos.plan.md`
- **Source PRD:** `.claude/PRPs/tele-pizza-backend.prd.md` (phase P1)

## Summary
Implemented PRD phase P1: a persisted **current menu** (UC2 `PUT /cardapio/corrente/{id}`, UC5 `GET /cardapio/corrente`)
and a runtime-switchable, persisted **discount-policy service** (UC3 `GET /descontos/politicas`,
UC4 `PUT /descontos/corrente/{codigo}`) mirroring the Imposto strategy pattern. The three points with **no codebase
precedent** were built deliberately: a JDBC **write path** (H2 `MERGE` upsert in `ConfiguracaoRepositoryJDBC`), an
HTTP **400-on-unknown-code** contract (`@RestControllerAdvice`), and a factory whose active strategy is read from a
**repository at runtime** (not startup config). Discount amount feeds the UC6 cost formula `custoFinal = (Σ itens − desconto) + imposto`.

## Assessment vs Reality
| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Medium | Medium (as predicted) |
| Confidence | 9/10 | Single-pass succeeded; only env friction + review hardening |
| Files changed | ~16 new + 6 edited | 18 new main + 5 new test + 6 edited = **29** |
| New tests | 5 drivers | 5 drivers, **23 test methods** (45 total in suite, up from 22) |

## Tasks Completed
| # | Task | Status | Notes |
|---|---|---|---|
| 1 | `configuracao` table + seed defaults (schema.sql, data.sql) | ✅ | |
| 2 | `ConfiguracaoRepositoryJDBC` (H2 MERGE write path) | ✅ | sole JDBC write helper (DRY) |
| 3 | `CardapioRepository` corrente methods | ✅ | |
| 4 | `CardapioService` corrente methods | ✅ | + null-guard (review fix A) |
| 5 | UC2 `DefinirCardapioCorrenteUC` + UC5 `RecuperarCardapioCorrenteUC` | ✅ | |
| 6 | `CardapioController` `PUT/GET /corrente` + `montarPresenter` helper | ✅ | GET `/{id}` behavior preserved |
| 7 | Descontos strategy package (interface, context, 3 strategies) | ✅ | SemDesconto, Fidelidade7, PromocaoVerao |
| 8 | `FabricaEstrategiaDesconto` (no config bean — runtime) | ✅ | config-coupling trap avoided |
| 9 | `DescontoRepository` port + JDBC + `ServicoDesconto` | ✅ | validate-before-persist |
| 10 | UC3/UC4 + Response + Presenter + `DescontoController` + `RestExceptionHandler` | ✅ | 400 contract via advice |
| 11 | Unit tests (5 drivers, comment-form specs) | ✅ | + ContextoDesconto guard test |

## Validation Results
| Level | Status | Notes |
|---|---|---|
| Static (compile) | ✅ Pass | `./mvnw compile` — JDK 21 |
| Unit + integration tests | ✅ Pass | **45/45**, 0 failures/errors (incl. `@SpringBootTest contextLoads` exercising the new beans + `configuracao` DDL/DML) |
| Build | ✅ Pass | BUILD SUCCESS |
| Integration (live HTTP) | ✅ Pass | App booted; all 9 UC curl checks correct incl. both **400**s |
| Edge cases | ✅ Pass | unknown code→400, unknown id→400, loyalty `>3` boundary (3→0%, 4→7%), negative subtotal→400, route `/corrente` not shadowed by `/{id}`, default re-seed on boot |

### Live HTTP evidence
```
GET  /cardapio/lista              → [{id:1,Agosto},{id:2,Setembro}]
GET  /cardapio/corrente (default) → Cardapio de Agosto
PUT  /cardapio/corrente/2         → Cardapio de Setembro (MERGE write)
GET  /cardapio/corrente           → Cardapio de Setembro (persisted)
PUT  /cardapio/corrente/999       → http=400
GET  /descontos/politicas         → {["Fidelidade7","PromocaoVerao","SemDesconto"],"SemDesconto"}
PUT  /descontos/corrente/PromocaoVerao → corrente switched (runtime)
PUT  /descontos/corrente/NaoExiste     → http=400 + message
```

## Files Changed
| File | Action |
|---|---|
| `Dominio/Servicos/Desconto/IEstrategiaCalculoDesconto.java` | CREATED |
| `Dominio/Servicos/Desconto/ContextoDesconto.java` | CREATED (+ non-negative guard) |
| `Dominio/Servicos/Desconto/SemDesconto.java` | CREATED |
| `Dominio/Servicos/Desconto/FidelidadeFrequente.java` | CREATED |
| `Dominio/Servicos/Desconto/PromocaoVerao.java` | CREATED |
| `Dominio/Servicos/Desconto/FabricaEstrategiaDesconto.java` | CREATED |
| `Dominio/Servicos/ServicoDesconto.java` | CREATED |
| `Dominio/Dados/DescontoRepository.java` | CREATED |
| `Adaptadores/Dados/ConfiguracaoRepositoryJDBC.java` | CREATED |
| `Adaptadores/Dados/DescontoRepositoryJDBC.java` | CREATED |
| `Aplicacao/DefinirCardapioCorrenteUC.java` | CREATED |
| `Aplicacao/RecuperarCardapioCorrenteUC.java` | CREATED |
| `Aplicacao/ListarPoliticasDescontoUC.java` | CREATED (+ deterministic order) |
| `Aplicacao/DefinirPoliticaDescontoUC.java` | CREATED |
| `Aplicacao/Responses/PoliticasDescontoResponse.java` | CREATED |
| `Adaptadores/Apresentacao/Presenters/PoliticasDescontoPresenter.java` | CREATED |
| `Adaptadores/Apresentacao/DescontoController.java` | CREATED |
| `Adaptadores/Apresentacao/RestExceptionHandler.java` | CREATED |
| `Dominio/Dados/CardapioRepository.java` | UPDATED (+2 port methods) |
| `Adaptadores/Dados/CardapioRepositoryJDBC.java` | UPDATED (+config dep, +2 methods, safe parse) |
| `Dominio/Servicos/CardapioService.java` | UPDATED (+2 methods, null-guard) |
| `Adaptadores/Apresentacao/CardapioController.java` | UPDATED (+corrente endpoints, montarPresenter) |
| `src/main/resources/schema.sql` | UPDATED (+`configuracao` table) |
| `src/main/resources/data.sql` | UPDATED (+2 seed rows) |
| `src/test/.../Desconto/{SemDesconto,PromocaoVerao,FidelidadeFrequente,FabricaEstrategiaDesconto}Test.java` | CREATED |
| `src/test/.../ServicoDescontoTest.java` | CREATED |

## Deviations from Plan
- **D4 (planned):** UC4 uses `PUT /descontos/corrente/{codigo}` path variable, not a JSON body — no `@RequestBody` precedent; codes are path-safe. (Recorded in the plan/PRD.)
- **Post-review hardening (4 fixes, in new P1 code):** (A) `CardapioService.recuperaCardapioCorrente` throws `IllegalStateException` instead of returning null → clean 500 not NPE; (B) `CardapioRepositoryJDBC.recuperaCorrente` wraps `Long.parseLong` (corrupt value → mapped `IllegalStateException`, no value echo); (C) `ListarPoliticasDescontoUC` returns codes in stable sorted order; (D) `ContextoDesconto` rejects negative `pedidosUltimos20Dias`.

## Issues Encountered
- **JDK 21 not on default path** (system default = JDK 17; project requires 21 — baseline uses `List.getFirst()`). Resolved by pointing Maven at a JDK 21 already on the machine via `JAVA_HOME` (no install). **Note for future sessions:** set `JAVA_HOME=/home/thiago/Documents/curseforge/minecraft/Install/runtime/java-runtime-delta/linux/java-runtime-delta` for `./mvnw`.
- **`./mvnw -o spring-boot:run` fails offline** (needs `spring-boot-loader-tools`/buildpack/shade not cached). `compile`/`test` work offline; the live boot needs network (drop `-o`).
- **Fact-Forcing Gate** required per-file justification on every Write/Edit; handled individually.

## Tests Written
| Test File | Methods | Coverage |
|---|---|---|
| `Desconto/SemDescontoTest` | 3 | 0% strategy + negative guard |
| `Desconto/PromocaoVeraoTest` | 3 | flat 5% + negative guard |
| `Desconto/FidelidadeFrequenteTest` | 6 | loyalty `>3` boundary, negative subtotal, negative context |
| `Desconto/FabricaEstrategiaDescontoTest` | 5 | lookup (assertSame), unknown→IllegalArgument, blank/null→IllegalState |
| `ServicoDescontoTest` | 6 | per-policy calc, runtime switch persistence, unknown→reject-without-persist (hand-rolled fake repo, no Mockito) |

## Code Review (adversarial workflow: 4 reviewers + skeptic verification)
- **27 findings; 0 confirmed HIGH/CRITICAL** (both CRITICAL/HIGH downgraded to MEDIUM by skeptics).
- **Applied (4 MEDIUM/LOW in new code):** the hardening fixes A–D above.
- **Confirmed correct:** SQL fully parameterized; factory correctly drops config coupling (cleaner than the Imposto reference); loyalty `>3` boundary; `calcular` returns the discount amount; validate-before-persist.
- **Deferred — pre-existing / out of P1 scope** (recorded in PRD §13 for future phases): `GET /cardapio/{id}` unknown→500 (baseline null-return), duplicate-products JOIN, `preco` int-vs-double precision (P2/P3), `@Component`/`@Repository` stereotype mix (whole-package sweep), H2 console + wildcard CORS + DEBUG logging (security — P5 auth).

## Next Steps
- [ ] `/code-review` (optional second pass) then `/prp-commit` / `/prp-pr` — **not yet committed** (awaiting user).
- [ ] P2 (15/06): order cycle UC6–UC9 — wire `ContextoDesconto.pedidosUltimos20Dias` from a real `PedidoRepository` count.
