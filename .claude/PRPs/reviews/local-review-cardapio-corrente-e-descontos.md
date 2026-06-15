# Local Code Review — P1 Cardápio corrente + Descontos (uncommitted)

**Reviewed:** 2026-06-09 · **Branch:** `feat/cardapio-corrente-e-descontos` · **Mode:** local (uncommitted changes)
**Decision:** ✅ **APPROVE with comments** — 0 CRITICAL, 0 HIGH; only MEDIUM/LOW (pre-existing or proportionate); validation green.

## Summary
The P1 changeset (18 new + 5 test + 6 edited source files) is correct, idiomatic, and well-tested. SQL is fully
parameterized, clean-architecture dependency rules hold, the discount factory correctly avoids the config-coupling the
Imposto baseline has, and the unknown-code → HTTP 400 contract works. No blocking issues. Remaining findings are either
pre-existing baseline behavior surfaced by changed files (deferred to PRD §13 #6) or proportionate trade-offs for a
localhost course project.

> ⚠️ **Scope note:** the uncommitted tree also contains 3 **pre-existing** `Imposto/*` edits (2 ins / 5 del, trivial
> cleanups) from the P0 work — **not part of P1**. Decide separately whether they belong in the P1 commit.

## Findings

### CRITICAL
None.

### HIGH
None.

### MEDIUM
- **M1 — Wildcard CORS on new controller** · `Adaptadores/Apresentacao/DescontoController.java` (`@CrossOrigin("*")` on both methods).
  Mirrors the existing convention (`CardapioController`, `Controller` all use `@CrossOrigin("*")`), so it's consistent — but `*` allows any origin. **Proportionate to a localhost course project; do not fix piecemeal.** Address project-wide (an allow-list / `CorsConfigurationSource`) when auth lands in **P5**.
- **M2 — Unknown `GET /cardapio/{id}` → HTTP 500, not 404** · `CardapioController.java:montarPresenter` + `CardapioRepositoryJDBC.recuperaPorId` (returns `null`).
  **Pre-existing baseline behavior**: `recuperaPorId`/`recuperaProdutoPorid` return `null` for missing ids; `montarPresenter` then NPEs → 500. P1 only refactored the presenter-building (behavior byte-identical). Logged in **PRD §13 #6**; fix belongs to a future phase (introduce a `NotFoundException` → 404), not this changeset.

### LOW
- **L1 — `{codigo}` path var unbounded before the domain** · `DescontoController.definirPolitica` → `ServicoDesconto.definirPolitica`.
  **Mitigated by design:** `definirPolitica` calls `fabrica.criar(codigo)` first, which rejects any non-registered code with `IllegalArgumentException` → 400 **before** any DB write, so no oversized/invalid value can persist (column is `VARCHAR(100)`). Residual is only a long string echoed in the 400 body. Accepted for P1.
- **L2 — Response/Presenter field-name drift** · `Aplicacao/Responses/PoliticasDescontoResponse.java` (`codigos`) vs `Adaptadores/.../PoliticasDescontoPresenter.java` (`politicas`), bridged positionally in `DescontoController`. Cosmetic; align names to prevent a future reorder bug.
- **L3 — `preco` int (centavos) vs `double` discount math** · `Produto.preco` is `int`; `IEstrategiaCalculoDesconto.calcular` is `double`. No order applies the discount yet (UC6/P2), so no live precision issue. Round / `BigDecimal` when wiring the cost formula in **P2**. (PRD §13 #6c)
- **L4 — `@Component` vs `@Repository` stereotype mix** · new `ConfiguracaoRepositoryJDBC`/`DescontoRepositoryJDBC` use `@Component`, matching the adjacent `CardapioRepositoryJDBC`/`ProdutosRepositoryJDBC`. Baseline is already mixed; normalize the whole `Adaptadores.Dados` package in one sweep rather than touching only new files.
- **L5 — Pre-existing unused `import java.util.Map;`** in `CardapioRepositoryJDBC.java` / `CardapioService.java` (baseline, not added by P1). Harmless; remove in a cleanup pass.

## Positives (verified)
- **Security:** all SQL parameterized (`ConfiguracaoRepositoryJDBC` SELECT + `MERGE` via `?`/`setString`); no secrets, no `System.out`/`printStackTrace`, no `TODO`/`FIXME` in new code.
- **Clean architecture:** no `Dominio`/`Aplicacao` class imports an `Adaptadores` type (grep-verified); `FabricaEstrategiaDesconto` reads the active policy from the `DescontoRepository` port at runtime (no config bean) — strictly cleaner than the Imposto reference.
- **Correctness:** validate-before-persist (UC4); loyalty boundary strictly `> 3` (3→0%, 4→7%); `calcular` returns the discount **amount** for `custoFinal = (Σ itens − desconto) + imposto`; post-review hardening already applied (null-guard, safe `parseLong`, deterministic order, `ContextoDesconto` non-negative).
- **Maintainability:** all functions < 50 lines, files small, nesting ≤ 2, no magic numbers (named `CODIGO`/`PERCENTUAL`/`CHAVE_CORRENTE`).
- **Tests:** 23 new methods across 5 drivers with comment-form case specs; hand-rolled fake repo (matches the project's no-Mockito POJO culture).

## Validation Results
| Check | Result |
|---|---|
| Type check / Compile (JDK 21) | ✅ Pass |
| Lint | ⚠️ Skipped — no linter configured; manual smell scan clean |
| Tests | ✅ Pass — 45/45 (0 failures/errors) |
| Build | ✅ Pass — BUILD SUCCESS |

## Files Reviewed
- **Added (23):** `Dominio/Servicos/Desconto/{IEstrategiaCalculoDesconto,ContextoDesconto,SemDesconto,FidelidadeFrequente,PromocaoVerao,FabricaEstrategiaDesconto}.java`, `Dominio/Servicos/ServicoDesconto.java`, `Dominio/Dados/DescontoRepository.java`, `Adaptadores/Dados/{ConfiguracaoRepositoryJDBC,DescontoRepositoryJDBC}.java`, `Aplicacao/{DefinirCardapioCorrenteUC,RecuperarCardapioCorrenteUC,ListarPoliticasDescontoUC,DefinirPoliticaDescontoUC}.java`, `Aplicacao/Responses/PoliticasDescontoResponse.java`, `Adaptadores/Apresentacao/Presenters/PoliticasDescontoPresenter.java`, `Adaptadores/Apresentacao/{DescontoController,RestExceptionHandler}.java`, 5 test files.
- **Modified (6, P1):** `Dominio/Dados/CardapioRepository.java`, `Adaptadores/Dados/CardapioRepositoryJDBC.java`, `Dominio/Servicos/CardapioService.java`, `Adaptadores/Apresentacao/CardapioController.java`, `resources/schema.sql`, `resources/data.sql`.
- **Modified (pre-existing, NOT P1):** `Imposto/FabricaEstrategiaImposto.java`, `Imposto/IEstrategiaCalculoImposto.java`, `ServicoImposto.java` (trivial; review separately).
- **Docs:** `tele-pizza-backend.prd.md` (tracker update).
