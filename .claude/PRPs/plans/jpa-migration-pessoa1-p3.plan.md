# Plan: P3 JPA Migration — Pessoa 1's own repositories (PROVISIONAL — gated on Seam #3)

> ⛔ **DO NOT IMPLEMENT YET.** This plan is **blocked on Seam #3** (PRD §13, OQ#2): **Pessoa 2 owns the
> JPA framework decision** — *annotate the domain entities with JPA* vs *introduce a separate
> persistence model* — plus the `ddl-auto` / `spring.sql.init` policy. That decision dictates this
> plan's central approach, so the plan cannot be single-pass until it is recorded in §13. The two
> approaches below are mutually exclusive; **delete the rejected one once Pessoa 2 decides**, then run
> `/prp-implement`. Everything else here (which repos, which queries, the type-mismatch fix) is
> decision-independent and ready.

## Summary
Migrate **Pessoa 1's** persistence adapters — `PedidoRepositoryJDBC`, `HistoricoStatusRepositoryJDBC`,
`ItensEstoqueRepositoryJDBC` — from `JdbcTemplate` to JPA/Spring Data, **keeping the existing
`Dominio.Dados` ports unchanged** so `ServicoPedido`/`ServicoEstoque` and all UCs are untouched.
Done on a branch, against Pessoa 2's agreed framework pattern, without simultaneous edits to shared
entities.

## User Story
As **Pessoa 1**, I want my order/stock repositories on JPA (matching Pessoa 2's framework choice) so
that persistence is unified across the project without changing the domain ports my services depend on.

## Problem → Solution
JDBC adapters with hand-written SQL → Spring Data JPA repositories behind the **same domain ports**,
preserving the order aggregate (Pedido + itens + historico) and the UC10 ENTREGUE-window query.

## Metadata
- **Complexity**: Large (touches entities, 3 adapters, schema/init policy, the UC10 JOIN query)
- **Source PRD**: `.claude/PRPs/tele-pizza-backend.prd.md`
- **PRD Phase**: P3 (17/06/2026) — Pessoa 1 migrates own repos (§9, §10 "▶ P3", §16, Seam #3)
- **Estimated Files**: ~8–12 (entities or persistence models, 3 JPA repos, adapter shims, schema/yaml)
- **Status**: ⛔ BLOCKED — gated on Seam #3 (Pessoa 2's framework decision, OQ#2)

---

## ⚠️ Gating Inputs (must be resolved BEFORE implementation)

| # | Decision (Pessoa 2 owns) | Why it blocks this plan |
|---|---|---|
| G1 | **Annotate entities** (`@Entity` on `Pedido`/`ItemPedido`/…) **vs separate persistence model** (`*JpaEntity` + mappers) | Determines whether `Dominio.Entidades` gains framework annotations (breaks the "pure POJO" rule, §4/§6.1) or stays pure with a mapping layer. The whole task structure differs. |
| G2 | **`ddl-auto`** (`none`/`validate`/`update`) **vs keep `spring.sql.init` (`schema.sql`+`data.sql`)** | Decides who owns table creation. Current init re-runs `data.sql` every boot (§3); JPA `ddl-auto=update` would conflict with `schema.sql`. |
| G3 | **`spring-boot-starter-data-jpa`** added by Pessoa 2 first | This plan assumes the starter + a configured `EntityManager`/dialect already exist. |

Record G1–G3 as a dated §13 decision (resolving OQ#2) before proceeding. Until then, this plan stays
provisional.

---

## Decision-INDEPENDENT facts (ready regardless of G1)

These hold under **either** framework approach and can be treated as fixed scope:

- **Ports stay byte-for-byte identical** — `PedidoRepository`, `HistoricoStatusRepository`,
  `ItensEstoqueRepository` in `Dominio.Dados` are the contract; services/UCs/tests depend on them.
  JPA changes only the `Adaptadores.Dados` implementations.
- **Pessoa 1's three adapters** to migrate:
  - `PedidoRepositoryJDBC` — `salvar` (auto_increment + `GeneratedKeyHolder`), `recuperaPorId`,
    `atualizaStatus`, `atualizaDataHoraPagamento`, `contarPedidosPagosCliente`, `entreguesEntre` (UC10 JOIN).
  - `HistoricoStatusRepositoryJDBC` — `registrar`, `historico` (ordered by `data_hora`, `id`).
  - `ItensEstoqueRepositoryJDBC` — `recuperaTodos`, **`baixaSeDisponivel` (atomic conditional `UPDATE … WHERE quantidade >= ?`)**, `devolve`.
- **Pessoa 2's repos are NOT in scope** — `Cliente`, cardápio, desconto repos (Seam #3 splits ownership).
- **`preco` type-mismatch fix lands here (PRD §13 OQ#6c, deferred to P3):** schema `produtos.preco bigint`
  but read as `int` in code — reconcile during the entity/persistence-model mapping.
- **Aggregate shape (D14):** `itens_pedido` has no identity of its own; it is persisted with the
  `Pedido` root. Under JPA this is a `@OneToMany(cascade=ALL, orphanRemoval=true)` (annotate approach)
  or composed in the mapper (separate-model approach).
- **Read-model `PedidoEntregue(Pedido, LocalDateTime dataHoraEntrega)`** is a domain record, not a
  table — the UC10 query must still project the `historico_status.data_hora` of the ENTREGUE row
  alongside the order. This is a **derived projection**, easiest as a JPQL/`@Query` constructor
  expression or a native query (the `JdbcTemplate` JOIN already proves the shape).

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `.claude/PRPs/tele-pizza-backend.prd.md` | §13 Seam #3 + OQ#2, §10 "▶ P3", §16 | The gating decision + ownership split |
| P0 | `src/main/.../Adaptadores/Dados/PedidoRepositoryJDBC.java` | all | The hardest migration — generated key, N+1 item rehydration, UC10 JOIN |
| P0 | `src/main/.../Adaptadores/Dados/ItensEstoqueRepositoryJDBC.java` | 34–46 | **`baixaSeDisponivel` atomic conditional update** — must survive the migration (concurrency invariant) |
| P0 | `src/main/.../Adaptadores/Dados/HistoricoStatusRepositoryJDBC.java` | all | Insert + ordered read |
| P0 | `src/main/.../Dominio/Dados/PedidoRepository.java` | all | The port to preserve verbatim |
| P0 | `src/main/.../Dominio/Entidades/Pedido.java` | 6–43 | Entity to annotate-or-map; enum `Status`, `double` money, `LocalDateTime` |
| P0 | `src/main/.../Dominio/Entidades/ItemPedido.java` | all | `Produto item` + `int quantidade` — the join-row of the aggregate |
| P0 | `src/main/.../Dominio/Servicos/PedidoEntregue.java` | all | UC10 read-model projection |
| P1 | `src/main/resources/schema.sql` | `pedidos`/`itens_pedido`/`historico_status` | Current DDL JPA must match/replace |
| P1 | `src/main/resources/application.yaml` | all | `spring.sql.init` + datasource; where `ddl-auto`/dialect go |
| P1 | `pom.xml` | 32–67 | Currently `spring-boot-starter-jdbc`; Pessoa 2 adds `…-data-jpa` |
| P1 | `src/test/.../Adaptadores/Dados/PedidoRepositoryEntreguesTest.java` | all | The IT must stay green post-migration — the migration's authoritative check |

## External Documentation
| Topic | Source | Key Takeaway |
|---|---|---|
| Spring Data JPA repositories | https://docs.spring.io/spring-data/jpa/reference/ | Define an internal `JpaRepository` + an adapter implementing the domain port that delegates to it |
| `@Query` constructor expressions | https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html | Project the ENTREGUE JOIN into `PedidoEntregue` without a managed entity for the read-model |
| Atomic conditional update | https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html (`@Modifying`) | `baixaSeDisponivel` → `@Modifying @Query("UPDATE … WHERE quantidade >= :q")` returning affected-row count to keep the check-then-act race-free |
| H2 + JPA dialect | Spring Boot reference, Data section | Confirm dialect auto-detect; reconcile `spring.sql.init` vs `ddl-auto` (G2) |

---

## Patterns to Mirror

### PORT_PRESERVED_ADAPTER  (keep the domain port; JPA lives behind it)
```java
// PORT (unchanged) — SOURCE: src/main/.../Dominio/Dados/PedidoRepository.java
public interface PedidoRepository {
    long salvar(Pedido pedido);
    Pedido recuperaPorId(long id);
    void atualizaStatus(long id, Pedido.Status novo);
    void atualizaDataHoraPagamento(long id, LocalDateTime quando);
    int contarPedidosPagosCliente(String cpf, LocalDateTime desde);
    List<PedidoEntregue> entreguesEntre(LocalDateTime ini, LocalDateTime fim);
}
// ADAPTER (new) — implements the SAME port, delegates to a Spring Data JpaRepository + a mapper.
// Mirrors how CardapioRepositoryJDBC implements CardapioRepository today (port/adapter split, §6.2).
```
Rule: services and UCs must not change. The adapter (`PedidoRepositoryJPA`) implements
`PedidoRepository` and translates between the domain `Pedido` and the JPA layer.

### ATOMIC_CONDITIONAL_BAIXA  (concurrency invariant — must survive)
```java
// SOURCE: src/main/.../Adaptadores/Dados/ItensEstoqueRepositoryJDBC.java:34-46
// UPDATE itensEstoque SET quantidade = quantidade - ? WHERE ingrediente_id = ? AND quantidade >= ?
// returns affected-row count; >0 means the baixa succeeded atomically (no check-then-act race).
```
Rule: the JPA version is `@Modifying @Query` returning `int` row-count; `ServicoEstoque.baixaEstoque`
relies on the boolean to roll back the order transaction on under-balance. **Do not** replace this
with a read-then-write — it reintroduces the race the JDBC version eliminated.

### UC10_PROJECTION  (ENTREGUE-window JOIN → read-model)
```java
// SOURCE: src/main/.../Adaptadores/Dados/PedidoRepositoryJDBC.java:107-126
// JOIN pedidos ⋈ historico_status ON status='ENTREGUE' AND data_hora IN [ini, fim)
// ORDER BY data_hora ASC, id ASC  → projects each row to PedidoEntregue(Pedido, dataHoraEntrega)
```
Rule: half-open window `[ini, fim)`; `'ENTREGUE'` literal sourced from `Pedido.Status.ENTREGUE.name()`;
one ENTREGUE row per order (terminal state) so no DISTINCT needed. Reproduce as JPQL/native `@Query`.

---

## Files to Change (shape depends on G1)

| File | Action | Approach A: annotate entities | Approach B: separate model |
|---|---|---|---|
| `Dominio/Entidades/Pedido.java`, `ItemPedido.java` | UPDATE / leave | Add `@Entity/@Id/@OneToMany/@Enumerated` | **Unchanged** (stay pure POJO) |
| `Adaptadores/Dados/*JpaEntity.java` | — / CREATE | n/a | New `PedidoJpaEntity`, `ItemPedidoJpaEntity`, `HistoricoStatusJpaEntity`, `ItemEstoqueJpaEntity` |
| `Adaptadores/Dados/*JpaRepository.java` (Spring Data) | CREATE | `interface PedidoJpaRepository extends JpaRepository<…>` | same |
| `Adaptadores/Dados/PedidoRepositoryJPA.java` (+ historico, estoque) | CREATE (replace JDBC) | adapter implementing the domain port | adapter + entity↔domain mapper |
| `Adaptadores/Dados/*RepositoryJDBC.java` (3 files) | DELETE | once the JPA adapter passes | same |
| `src/main/resources/schema.sql` / `application.yaml` | UPDATE | reconcile `ddl-auto` vs `spring.sql.init` (G2) | same |
| `.claude/PRPs/tele-pizza-backend.prd.md` | UPDATE | record OQ#2 resolution + Progress Log | same |

## NOT Building
- **Pessoa 2's repos** (`Cliente`, cardápio, desconto) — they migrate their own (Seam #3).
- **The JPA starter / framework decision** — Pessoa 2 adds `spring-boot-starter-data-jpa` and decides G1–G3 first.
- **Any change to domain ports or service/UC signatures** — invisible to the application layer.
- **New behavior or endpoints** — pure persistence-mechanism swap.
- **Simultaneous edits to shared entities while Pessoa 2 works** — do this on a dedicated branch (Seam #3).

---

## Step-by-Step Tasks (run ONLY after G1–G3 are recorded)

### Task 0 (GATE): Confirm Seam #3 is resolved
- **ACTION**: Verify §13 has a dated decision answering G1 (annotate vs separate model), G2 (`ddl-auto`/init), and that `spring-boot-starter-data-jpa` is present in `pom.xml`.
- **VALIDATE**: If any is missing → **stop**; this plan is not ready. Ping Pessoa 2.

### Task 1: Map the order aggregate per the chosen approach
- **ACTION**: Under Approach A, annotate `Pedido` (`@Entity`, `@Id @GeneratedValue(IDENTITY)` for the auto_increment, `@Enumerated(STRING)` on `Status`, `@OneToMany(cascade=ALL, orphanRemoval=true)` to `ItemPedido`, money as `double`/`@Column`). Under Approach B, create `PedidoJpaEntity` + mapper; leave `Pedido` pure.
- **MIRROR**: `PORT_PRESERVED_ADAPTER`; aggregate decision D14.
- **GOTCHA**: `pedidos.id` is DB `auto_increment` → `GenerationType.IDENTITY` (not `AUTO`/`SEQUENCE`). `itens_pedido` PK is composite `(pedido_id, produto_id)` — model the join row accordingly; it carries `quantidade`. Reconcile `produtos.preco bigint`-vs-`int` here (OQ#6c).
- **VALIDATE**: Entities compile; schema matches (or `ddl-auto=validate` passes against `schema.sql`).

### Task 2: Spring Data repositories + port adapters (Pedido, Historico, Estoque)
- **ACTION**: Create internal `JpaRepository` interfaces and adapter classes implementing the three domain ports, delegating to them (+ mapper under Approach B).
- **IMPLEMENT**: `salvar` → `save` + return generated id; `recuperaPorId` → `findById` mapped to domain (or `null`); `atualizaStatus`/`atualizaDataHoraPagamento` → `@Modifying @Query` or load-mutate-save; `contarPedidosPagosCliente` → derived/`@Query` count with the `data_hora_pagamento IS NOT NULL AND >= :desde` predicate; `historico` → `findByPedidoIdOrderByDataHoraAscIdAsc`.
- **MIRROR**: `PORT_PRESERVED_ADAPTER`.
- **GOTCHA**: Preserve `recuperaPorId` returning **`null`** (not `Optional`/throw) — `ServicoPedido.recuperaPorId` wraps null into the 404, and the fakes return null. Changing this breaks the 404 contract and the existing tests.
- **VALIDATE**: existing **unit** tests (POJO fakes) are unaffected; app context loads.

### Task 3: UC10 ENTREGUE-window projection
- **ACTION**: Reimplement `entreguesEntre(ini, fim)` as a JPQL/native `@Query` JOIN projecting `PedidoEntregue`.
- **MIRROR**: `UC10_PROJECTION`.
- **GOTCHA**: Keep the half-open `[ini, fim)` window and `ORDER BY data_hora ASC, id ASC`; source the status literal from `Pedido.Status.ENTREGUE`. The read-model is **not** an entity — use a constructor expression or map the projection.
- **VALIDATE**: `PedidoRepositoryEntreguesTest` (the `@SpringBootTest` IT) passes **unchanged** — it is the authoritative migration check (real H2, real query).

### Task 4: `baixaSeDisponivel` atomic conditional update
- **ACTION**: Port the conditional decrement to `@Modifying @Query("UPDATE … SET quantidade = quantidade - :q WHERE ingrediente_id = :id AND quantidade >= :q")` returning the affected-row count → boolean.
- **MIRROR**: `ATOMIC_CONDITIONAL_BAIXA`.
- **GOTCHA**: Must return `true` only when a row was updated. `@Modifying` queries need a transaction — `ServicoPedido.submeter`/`cancelar` are already `@Transactional`, and `ServicoEstoque` is called within them, so the context exists. Add `clearAutomatically`/`flushAutomatically` if stale first-level cache risks a wrong subsequent read.
- **VALIDATE**: `ServicoEstoqueTest.baixaSemSaldoLancaInsuficiente` still passes (unit-level, fake) **and** an H2-backed check confirms concurrent under-balance rolls back.

### Task 5: Init policy + delete JDBC adapters
- **ACTION**: Apply G2 (e.g. keep `schema.sql`+`data.sql` with `ddl-auto=none`/`validate`, or switch to `ddl-auto`). Delete the three `*RepositoryJDBC` classes once the JPA adapters pass.
- **GOTCHA**: `data.sql` re-runs every boot (§3) and seeds clients/produtos the FKs need — don't drop it without replacing the seed path. Two beans implementing the same port = startup failure; delete JDBC adapters in the same change that adds the JPA ones.
- **VALIDATE**: `./mvnw test` green; app boots; `/pedidos` smoke (submit→status→pay) works on :8090.

### Task 6: Update the PRD
- **ACTION**: Record the OQ#2/Seam #3 resolution in §13, flip P3 status (Pessoa-1 repos ✅), append Progress Log, update §7/§8/§11 persistence-style rows.
- **VALIDATE**: PRD consistent; §3/§5 "Persistence style 🟡 JDBC" updated for the migrated repos.

---

## Testing Strategy
- **Unit (fakes) — unchanged:** `ServicoPedidoTest`, `ServicoEstoqueTest`, `SubmeterPedidoParaAprovacaoUCTest`, `PagarPedidoUCTest` use POJO fakes of the ports, so they must pass **without edits** — proof the ports were preserved.
- **Integration — authoritative:** `PedidoRepositoryEntreguesTest` (`@SpringBootTest`, real H2) must stay green; it validates the UC10 JOIN after migration. Add a JPA round-trip IT if Approach B's mapper needs direct coverage.

### Edge Cases Checklist
- [ ] Generated id returned correctly (`IDENTITY`)
- [ ] `recuperaPorId` returns `null` (not throw/Optional) for missing — 404 contract
- [ ] Aggregate cascade: saving a `Pedido` persists its `itens_pedido`
- [ ] `baixaSeDisponivel` atomic under concurrency (no check-then-act race)
- [ ] UC10 half-open window `[ini, fim)` + ENTREGUE-only filter
- [ ] `data.sql` seeds still load (FK targets present) under the chosen init policy
- [ ] `produtos.preco` type reconciled (bigint vs int)

---

## Validation Commands
```bash
export JAVA_HOME=/snap/android-studio/232/jbr   # JDK 21 (system 17 fails)
./mvnw -q compile          # EXPECT: success
./mvnw -q test             # EXPECT: existing unit fakes + the entregues IT all green (no test edits)
SERVER_PORT=8090 ./mvnw spring-boot:run   # EXPECT: boots; schema/data load per G2
# Smoke: submit an order, query status, pay — confirm persistence round-trips
```

## Acceptance Criteria
- [ ] Seam #3 (G1–G3) recorded in §13 before any code.
- [ ] Three Pessoa-1 adapters on JPA; domain ports unchanged.
- [ ] All existing unit tests pass **without modification**; the entregues IT passes.
- [ ] `baixaSeDisponivel` remains atomic; UC10 window semantics preserved.
- [ ] `preco` type mismatch resolved; init policy applied; JDBC adapters removed.
- [ ] PRD updated (OQ#2 resolved, P3 Pessoa-1 ✅, Progress Log).

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Implementing before Seam #3 → wrong framework approach, rework | High (if ungated) | High | **Task 0 gate**; do not start until §13 records G1–G3 |
| Pure-POJO rule broken by `@Entity` (Approach A) | Medium | Medium | Pessoa 2 decides; if purity matters, choose Approach B (separate model + mapper) |
| `recuperaPorId` contract drift (Optional/throw) breaks 404 + fakes | Medium | High | Preserve `null`-return semantics exactly (Task 2 gotcha) |
| Losing the atomic baixa (race reintroduced) | Medium | High | `@Modifying` conditional update returning row-count, never read-then-write |
| `ddl-auto` vs `spring.sql.init` double-create / seed loss | High | Medium | Pick one (G2); keep `data.sql` seeds or move them; test boot |
| Two beans per port at switchover | Medium | Medium | Delete JDBC adapter in the same change as the JPA one |

## Notes
- This plan is the **second** of the "Both, sequenced" deliverable. The **executable-now** task is the
  P6 test-drivers plan (`test-drivers-pessoa1-p6.plan.md`); run that first. Return here once Pessoa 2
  signs off Seam #3.
- The PRD's own load-balancing note (§13, 2026-06-22) sequences exactly this: "once P2 ships, Pessoa 1
  picks up JPA-of-own-repos + UC10" — UC10 is done; JPA is the remaining Pessoa-1 item, still gated.
- JDK 21 mandatory (memory `build-requires-jdk21`).
