# Plan: P6 Test Drivers — Pessoa 1 slice (Pedido / Estoque / SubmeterPedido)

## Summary
P6 requires unit/integration test drivers — **with each test case written as a comment in the same
file** (course spec) — plus ≥80% coverage for Pessoa 1's targets: `SubmeterPedidoParaAprovacaoUC`,
`ServicoPedido`, `ServicoEstoque`. **Most of this already exists** (the drivers were written during
P2 and already carry comment-form specs). This phase closes the remaining gap: add a coverage tool
(JaCoCo — none is configured), measure against the targets, fill the few uncovered branches, and
ratify the now-stale §6.8 PRD note that claims the comment-form specs are missing.

## User Story
As **Pessoa 1 (order-cycle owner)**, I want measured, comment-documented test drivers for my order
services so that the P6 deliverable is provably ≥80%-covered and the spec's "test cases as comments"
rule is satisfied for my slice.

## Problem → Solution
Drivers exist with comment-form specs but coverage is **unmeasured** (no JaCoCo) and a couple of
branches are untested → add JaCoCo, measure, fill gaps, and flip P6's Pessoa-1 slice to complete.

## Metadata
- **Complexity**: Small–Medium (1 build edit + ~2 gap test methods + optional 1 integration driver)
- **Source PRD**: `.claude/PRPs/tele-pizza-backend.prd.md`
- **PRD Phase**: P6 (29/06/2026) — Pessoa 1 slice (§9, §16 "Testes (P6)")
- **Estimated Files**: 1 edited (`pom.xml`) + 2–3 edited/new test files + PRD update

---

## UX Design
Internal change — no user-facing UX transformation. (Build/test tooling only.)

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| `./mvnw test` | Runs the suite, no coverage report | Same tests + JaCoCo report at `target/site/jacoco/index.html` | New `jacoco-maven-plugin` |
| P6 status (§9) | ⬜ pending | Pessoa-1 slice ✅ (Pessoa 2's discount/imposto/auth drivers still pending) | Partial flip |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `src/test/.../Dominio/Servicos/ServicoPedidoTest.java` | 36–56, all | Target driver — already has the 16-case comment block; pattern to mirror for gap tests |
| P0 | `src/test/.../Dominio/Servicos/ServicoEstoqueTest.java` | 23–31, all | Target driver — 6-case comment block; fully covered (reference) |
| P0 | `src/test/.../Aplicacao/SubmeterPedidoParaAprovacaoUCTest.java` | 42–49, all | Target driver — 5-case comment block; needs 1 aggregation gap test |
| P1 | `src/test/.../Aplicacao/PagarPedidoUCTest.java` | 45–50 | UC9 trigger driver — also Pessoa 1; already comment-specced |
| P1 | `src/test/.../Adaptadores/Dados/PedidoRepositoryEntreguesTest.java` | 21–69 | `@SpringBootTest`+H2 integration pattern (mirror if a JDBC driver is needed for coverage) |
| P0 | `src/main/.../Dominio/Servicos/ServicoPedido.java` | 94–105 | `recuperaPorId`/`historico` — `historico()` is the main uncovered method |
| P0 | `src/main/.../Aplicacao/SubmeterPedidoParaAprovacaoUC.java` | 40–47 | Duplicate-product aggregation branch (`merge`) — untested |
| P1 | `pom.xml` | 69–96 | `<build><plugins>` block — where JaCoCo goes |

## External Documentation
| Topic | Source | Key Takeaway |
|---|---|---|
| JaCoCo Maven plugin | https://www.jacoco.org/jacoco/trunk/doc/maven.html | `prepare-agent` (binds to `initialize`) + `report` (bind to `test`); optional `check` rule for a coverage gate |
| Surefire vs Failsafe | Spring Boot parent default | Project runs **only Surefire** (`*Test`) — integration drivers are named `*Test` (not `*IT`), confirmed by D15 deviation; keep that convention |

---

## Patterns to Mirror

### COMMENT_FORM_TEST_SPEC  (the spec's "casos de teste como comentário" — ALREADY in use)
```java
// SOURCE: src/test/.../Dominio/Servicos/ServicoEstoqueTest.java:23-31
/*
 * Casos de teste -- ServicoEstoque (disponibilidade DERIVADA do estoque + baixa):
 *  1. pedidoComEstoqueSuficienteDisponivel : ... -> haDisponibilidade true, indisponiveis vazio
 *  2. pedidoSemEstoqueIndisponivel         : 1 ingrediente zerado -> produto em itensIndisponiveis
 *  ...
 */
class ServicoEstoqueTest { ... }
```
Rule: a block comment **at the top of the same driver file** enumerating numbered cases, each line
`nomeDoMetodo : entrada -> resultado esperado`. New gap tests must be **added to this list**, not
just appended as methods.

### POJO_FAKES_NO_MOCKITO  (the project tests with hand-written in-memory fakes, not Mockito)
```java
// SOURCE: src/test/.../Dominio/Servicos/ServicoPedidoTest.java:61-87
private static class FakePedidoRepository implements PedidoRepository {
    private final Map<Long, Pedido> store = new HashMap<>();
    private long seq = 0;
    @Override public long salvar(Pedido pedido) { long id = ++seq; store.put(id, pedido); /*...*/ return id; }
    @Override public Pedido recuperaPorId(long id) { /*...*/ }
    // every port method implemented in-memory
}
```
Rule: no Mockito anywhere in the suite — implement each `Dominio.Dados` port as a private static
in-memory `Fake*` class. A `montar(...)` builder wires the graph (see `ServicoPedidoTest.montar`).

### PURE_UNIT_STRUCTURE  (DELTA for doubles, descriptive void names, assertThrows)
```java
// SOURCE: src/test/.../Dominio/Servicos/ServicoPedidoTest.java:58, 179-188, 272-275
private static final double DELTA = 1e-9;
@Test void submeterAprovadoSemDesconto() {
    ServicoPedido servico = montar("SemDesconto", 0, estoqueCheio());
    Pedido pedido = servico.submeter(cliente, "Rua X, 10", cestaPadrao());
    assertEquals(148.5, pedido.getValorCobrado(), DELTA);   // AAA, DELTA for money
}
@Test void cancelarInexistenteLanca() {
    assertThrows(RecursoNaoEncontradoException.class, () -> servico.cancelar(999L));
}
```

### INTEGRATION_DRIVER  (only if a JDBC adapter must be covered)
```java
// SOURCE: src/test/.../Adaptadores/Dados/PedidoRepositoryEntreguesTest.java:30-69
@SpringBootTest
class PedidoRepositoryEntreguesTest {
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private JdbcTemplate jdbc;
    @BeforeEach void preparaDados() {
        jdbc.update("DELETE FROM historico_status WHERE pedido_id IN (?,?,?)", /*high ids*/);  // clean + reseed
        // use HIGH ids (9101+) and seeded cpf '9001' to avoid colliding with data.sql / runtime rows
    }
}
```
Rule: `@SpringBootTest`, autowire the real port + a raw `JdbcTemplate`, isolate with `@BeforeEach`
delete/reseed using high non-colliding ids and the data.sql-seeded client `9001`.

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `pom.xml` | UPDATE | Add `jacoco-maven-plugin` (`prepare-agent` + `report`) — no coverage tool exists today |
| `src/test/.../Dominio/Servicos/ServicoPedidoTest.java` | UPDATE | Add `historico()` happy + 404 cases; extend the comment block |
| `src/test/.../Aplicacao/SubmeterPedidoParaAprovacaoUCTest.java` | UPDATE | Add duplicate-product aggregation case; extend the comment block |
| `src/test/.../Adaptadores/Dados/PedidoRepositoryJDBCTest.java` | CREATE *(only if coverage < 80% after the above)* | Integration cover `salvar`/`recuperaPorId`/`atualizaStatus`/`atualizaDataHoraPagamento`/`contarPedidosPagosCliente` |
| `.claude/PRPs/tele-pizza-backend.prd.md` | UPDATE | Ratify stale §6.8 note; flip P6 Pessoa-1 slice; Progress Log entry |

## NOT Building
- **Pessoa 2's P6 targets** — discount strategies, `ServicoDesconto`, `ServicoImposto`, auth drivers (§16). Out of scope.
- **New production behavior** — this phase only adds tests/tooling; no `src/main` logic changes.
- **A coverage `check` gate that fails the build** — add JaCoCo `report` only; a hard `check` rule risks breaking `./mvnw test` for the whole team (and would also flag Pessoa 2's not-yet-tested classes). Measure, don't enforce, this phase.
- **Mockito** — the suite is deliberately Mockito-free; keep using POJO fakes.
- **Renaming integration drivers to `*IT`** — project has no Failsafe; `*Test` is intentional (D15).

---

## Step-by-Step Tasks

### Task 1: Add JaCoCo to the build
- **ACTION**: Add `org.jacoco:jacoco-maven-plugin` to `pom.xml` `<build><plugins>`.
- **IMPLEMENT**:
  ```xml
  <plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
      <execution><id>prepare-agent</id><goals><goal>prepare-agent</goal></goals></execution>
      <execution><id>report</id><phase>test</phase><goals><goal>report</goal></goals></execution>
    </executions>
  </plugin>
  ```
- **MIRROR**: existing `<plugin>` entries in `pom.xml:71-94` (same `<build><plugins>` block).
- **IMPORTS**: n/a (XML).
- **GOTCHA**: Spring Boot's parent does **not** manage the JaCoCo version — pin `<version>` explicitly (0.8.12 supports Java 21). Bind `report` to the `test` phase so `./mvnw test` produces the HTML report.
- **VALIDATE**: `JAVA_HOME=<jdk21> ./mvnw -q test` → build green + `target/site/jacoco/index.html` exists.

### Task 2: Measure the baseline against the three targets
- **ACTION**: Run the suite, open the JaCoCo report, record line/branch coverage for `ServicoPedido`, `ServicoEstoque`, `SubmeterPedidoParaAprovacaoUC` (and note `PagarPedidoUC`).
- **IMPLEMENT**: no code — read `target/site/jacoco/.../index.html` (or `jacoco.csv`) per class.
- **MIRROR**: n/a.
- **GOTCHA**: Coverage is **per the named targets**, not the whole module. The JDBC adapters and Presenters will read low — that's expected and out of P6's Pessoa-1 scope; do not chase 80% on the whole module by testing other people's classes.
- **VALIDATE**: A recorded number per target. `ServicoEstoque` should already be ~100%; `ServicoPedido` will miss `historico()`; the UC will miss the aggregation branch.

### Task 3: Fill the `ServicoPedido.historico()` gap
- **ACTION**: Add two cases to `ServicoPedidoTest`: history returned for an existing order, and 404 for a missing one.
- **IMPLEMENT**:
  ```java
  @Test
  void historicoRetornaTransicoesDoPedido() {
      ServicoPedido servico = montar("SemDesconto", 0, estoqueCheio());
      Pedido pedido = servico.submeter(cliente, "Rua X, 10", cestaPadrao());
      List<TransicaoStatus> h = servico.historico(pedido.getId());
      assertEquals(2, h.size());
      assertEquals(Pedido.Status.NOVO, h.get(0).status());
      assertEquals(Pedido.Status.APROVADO, h.get(1).status());
  }
  @Test
  void historicoInexistenteLanca() {
      ServicoPedido servico = montar("SemDesconto", 0, estoqueCheio());
      assertThrows(RecursoNaoEncontradoException.class, () -> servico.historico(999L));
  }
  ```
- **MIRROR**: `PURE_UNIT_STRUCTURE` + `historicoRegistraNovoEAprovado` (ServicoPedidoTest:252-260); `RecursoNaoEncontradoException` is already imported there.
- **IMPORTS**: none new (`TransicaoStatus`, `RecursoNaoEncontradoException` already imported in the file).
- **GOTCHA**: `servico.historico(id)` first calls `recuperaPorId(id)` for the 404 — the existing `FakePedidoRepository.recuperaPorId` returns `null` for unknown ids, so the throw path works without changes.
- **VALIDATE**: Extend the file's top comment block to cases 17/18; `./mvnw -q test` green; `historico()` now covered.

### Task 4: Fill the UC duplicate-product aggregation gap
- **ACTION**: Add one case to `SubmeterPedidoParaAprovacaoUCTest` proving two requests for the same `produtoId` are summed (the `LinkedHashMap.merge` branch at `SubmeterPedidoParaAprovacaoUC.java:46`).
- **IMPLEMENT**:
  ```java
  @Test
  void itensDuplicadosSaoAgregados() {
      SubmeterPedidoParaAprovacaoUC uc = montar(estoqueCheio());
      SubmeterPedidoRequest req = new SubmeterPedidoRequest("9001", "Rua X, 10",
          List.of(new ItemPedidoRequest(1L, 1), new ItemPedidoRequest(1L, 2))); // mesmo produto, 1+2
      SubmeterPedidoResponse resp = uc.run(req);
      assertEquals("APROVADO", resp.status());          // 3 x calabresa(55) = 165
      assertEquals(165.0, resp.valor(), DELTA);
  }
  ```
- **MIRROR**: `submeterAprovadoRetornaTotais` (SubmeterPedidoParaAprovacaoUCTest:152-162).
- **IMPORTS**: none new.
- **GOTCHA**: Calabresa(id 1) recipe in the fake uses ingredientes 1,2,3 with `estoqueCheio()`=100 each, so 3 units stays in stock — APROVADO, not RECUSADO. Subtotal = 3×55.00 = 165.00 (no discount, `SemDesconto`).
- **VALIDATE**: Add case 6 to the top comment block; `./mvnw -q test` green; the `merge` aggregation branch covered.

### Task 5: (Conditional) integration driver for `PedidoRepositoryJDBC`
- **ACTION**: ONLY if, after Tasks 3–4, a target still reads < 80% (it won't be the services — this is for the JDBC adapter if P6 scope is read strictly as "exercise the persistence too"), add `PedidoRepositoryJDBCTest`.
- **IMPLEMENT**: `@SpringBootTest` driver that `salvar`s a `Pedido` (client `9001`, real `produtos` ids 1/3), asserts `recuperaPorId` round-trips status+money+itens, `atualizaStatus`→PAGO + `atualizaDataHoraPagamento` reflected, and `contarPedidosPagosCliente` counts a paid order within the window.
- **MIRROR**: `INTEGRATION_DRIVER` (PedidoRepositoryEntreguesTest) — high non-colliding ids, `@BeforeEach` clean/reseed, autowire `PedidoRepository` + `JdbcTemplate`.
- **IMPORTS**: `@SpringBootTest`, `@Autowired`, `JdbcTemplate`, `@BeforeEach`, the entities.
- **GOTCHA**: `pedidos.id` is `auto_increment` — let `salvar` generate it (don't force an id) and capture the returned key. `cliente_cpf` must reference a seeded client (`9001`/`9002`) or the FK rejects the insert. Clean up inserted rows in `@BeforeEach`.
- **VALIDATE**: Driver carries its own comment-form case list; `./mvnw -q test` green; adapter coverage rises.

### Task 6: Ratify the stale §6.8 note and update the PRD
- **ACTION**: In `tele-pizza-backend.prd.md`: (a) annotate the §6.8 "📌 Spec requirement not yet met" note — Pessoa 1's drivers **do** carry comment-form specs (since P2); (b) flip the P6 row (§9) to mark the **Pessoa-1 slice ✅** (Pessoa 2's slice still ⬜); (c) append a Progress Log entry; (d) tick nothing in §7/§8 (no UC/service status change).
- **MIRROR**: existing Progress Log entries (§14) — dated, newest first, "what changed / phase / test status / follow-ups".
- **GOTCHA**: Do not flip the whole P6 to ✅ — Pessoa 2's discount/imposto/auth drivers are still pending. Mark the slice only.
- **VALIDATE**: PRD reads consistently; the §6.8 note no longer contradicts the actual test files.

---

## Testing Strategy

### Unit Tests (the targets — current state)
| Driver | Cases today | Gap added here |
|---|---|---|
| `ServicoEstoqueTest` | 6 (full method coverage) | none |
| `ServicoPedidoTest` | 16 | +2 (`historico` happy + 404) |
| `SubmeterPedidoParaAprovacaoUCTest` | 5 | +1 (duplicate-product aggregation) |
| `PagarPedidoUCTest` | 3 | none |
| `PedidoRepositoryEntreguesTest` (IT) | 3 | none |

### Edge Cases Checklist (target methods)
- [x] Empty cart / blank address (`submeterCarrinhoVazioLanca`, `submeterEnderecoVazioLanca`)
- [x] Out-of-stock → RECUSADO, no stock decrement (`submeterSemEstoqueRecusa`)
- [x] Loyalty boundary: count 3 (no discount) vs 4 (7%) (`fidelidadeNoLimite*`/`fidelidadeAcima*`)
- [x] Cancel only-APROVADO; cancel missing → 404 (`cancelarApenasAprovado`, `cancelarInexistenteLanca`)
- [x] PAGO stamps `dataHoraPagamento` (`pagoCarimbaDataHoraPagamento`)
- [x] UC10 null/`ini>fim` → IllegalArgument; delegation (`listarEntregues*`)
- [ ] **`historico()` happy + 404** (Task 3 — NEW)
- [ ] **Duplicate-product aggregation** (Task 4 — NEW)
- [x] Stock under-balance on baixa → EstoqueInsuficiente (`baixaSemSaldoLancaInsuficiente`)

---

## Validation Commands

### Static Analysis / Compile
```bash
export JAVA_HOME=/snap/android-studio/232/jbr   # JDK 21 (system default is 17 — build fails on 17)
./mvnw -q compile
```
EXPECT: build success.

### Unit + Integration Tests (with coverage)
```bash
./mvnw -q test
```
EXPECT: all tests pass (existing suite + 3 new); `target/site/jacoco/index.html` generated.

### Coverage check (manual read)
```bash
# Inspect per-class coverage for the three targets
grep -E "ServicoPedido|ServicoEstoque|SubmeterPedidoParaAprovacaoUC" target/site/jacoco/jacoco.csv
```
EXPECT: each target ≥ 80% line coverage.

### Manual Validation
- [ ] Open `target/site/jacoco/index.html`, drill into `Dominio.Servicos` and `Aplicacao` — confirm the three targets ≥ 80%.
- [ ] Confirm each target driver's top-of-file comment block lists every `@Test` method (incl. the new ones).

---

## Acceptance Criteria
- [ ] JaCoCo wired; `./mvnw test` emits an HTML coverage report.
- [ ] `ServicoPedido`, `ServicoEstoque`, `SubmeterPedidoParaAprovacaoUC` each ≥ 80% line coverage.
- [ ] Every Pessoa-1 target driver carries an accurate comment-form case list (spec requirement).
- [ ] New gap tests pass; no regression in the existing suite.
- [ ] PRD §6.8 note ratified; P6 Pessoa-1 slice flipped; Progress Log appended.

## Completion Checklist
- [ ] Tests follow POJO-fake + DELTA + descriptive-name conventions (no Mockito introduced).
- [ ] No `src/main` behavior changed.
- [ ] Comment blocks updated in lockstep with new `@Test` methods.
- [ ] Coverage scoped to the named targets (not gamed by testing other owners' classes).
- [ ] Built/verified under JDK 21.

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| JaCoCo version unmanaged by Spring parent → build error | Medium | Low | Pin `<version>0.8.12</version>` (Java-21-capable) |
| Whole-module coverage looks <80% (adapters/presenters drag it down) | High | Low | Scope the 80% to the **named targets** per PRD §16; optional Task 5 lifts adapter coverage if scope is read strictly |
| Offline build failure (no network for first dep fetch) | Medium | Low | Run online once to fetch the JaCoCo plugin; thereafter `-o` works (matches prior phases' note) |
| Marking all of P6 done | Low | Medium | Explicitly flip **only** the Pessoa-1 slice; Pessoa 2's drivers remain ⬜ |

## Notes
- **Why this plan is small:** the four order-cycle drivers (`ServicoPedidoTest`, `ServicoEstoqueTest`,
  `SubmeterPedidoParaAprovacaoUCTest`, `PagarPedidoUCTest`) plus the UC10 integration driver were all
  authored during P2/UC10 **already including the comment-form `Casos de teste` blocks**. The §6.8 PRD
  note ("Existing tests do not include the comment-form test-case specs") predates them and is stale
  for Pessoa 1's slice — Task 6 records that.
- **JDK 21 is mandatory** for build/test (memory `build-requires-jdk21`); the system default 17 fails.
- This phase is **independent of Seam #3** — it does not touch persistence framework choice, so it can
  run now regardless of the JPA decision. It is the recommended next Pessoa-1 task to execute.
