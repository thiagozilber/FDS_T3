# Plan: P3 JPA Migration — Pessoa 1's own repositories

> ✅ **Seam #3 RESOLVED (2026-06-28, via PR #4 — PRD §13 / OQ#2).** Pessoa 2 made the framework
> decision: **Approach A — annotate the domain entities directly** (`@Entity` on the entities), with
> **`spring.jpa.hibernate.ddl-auto: none`** (schema.sql keeps owning DDL) and the
> `spring-boot-starter-data-jpa` starter. This plan is **finalized** for that approach (the old
> Approach-B "separate persistence model" branch has been removed).
>
> ⏳ **One execution prerequisite remains: PR #4 must merge to `main` first.** The starter (`pom.xml`),
> the `ddl-auto: none` config (`application.yaml`), and the §13 decision all currently live on
> `skad-branch` and are **not yet on `main`** — and PR #4 is build-red pending two fixes (duplicate
> `ClienteRepository` bean; `ddl-auto: none` not yet applied — see `reviews/pr-4-review.md`). Branch
> off the updated `main` once PR #4 is green and merged, then run `/prp-implement`.

## Summary
Migrate **Pessoa 1's** persistence adapters — `PedidoRepositoryJDBC`, `HistoricoStatusRepositoryJDBC`,
`ItensEstoqueRepositoryJDBC` — from `JdbcTemplate` to JPA/Spring Data, **keeping the existing
`Dominio.Dados` ports unchanged** so `ServicoPedido`/`ServicoEstoque` and all UCs are untouched. Done on
a branch off `main` (post PR #4), using Approach A (annotated entities), without simultaneous edits to
shared entities while Pessoa 2 is still working.

## User Story
As **Pessoa 1**, I want my order/stock repositories on JPA (matching Pessoa 2's Approach-A framework
choice) so that persistence is unified across the project without changing the domain ports my services
depend on.

## Problem → Solution
JDBC adapters with hand-written SQL → Spring Data JPA repositories behind the **same domain ports**,
preserving the order aggregate (Pedido + itens + historico) and the UC10 ENTREGUE-window query.

## Metadata
- **Complexity**: Large (touches entities, 3 adapters, schema/init policy, the UC10 JOIN query)
- **Source PRD**: `.claude/PRPs/tele-pizza-backend.prd.md`
- **PRD Phase**: P3 (17/06/2026) — Pessoa 1 migrates own repos (§9, §10 "▶ P3", §16, Seam #3)
- **Framework approach**: **A — annotate domain entities** (Seam #3 / OQ#2 resolved 2026-06-28, PR #4)
- **Estimated Files**: ~8–10 (entities, 3 JPA repos + adapters, schema/yaml, PRD)
- **Status**: ✅ READY — finalized; **execute after PR #4 merges to `main`** (Task 0 gate)

---

## Resolved framework inputs (Seam #3 / OQ#2 — recorded in PRD §13, 2026-06-28)

| # | Decision | Consequence for this plan |
|---|---|---|
| G1 | **Approach A — annotate the domain entities** (`@Entity/@Id/@Enumerated/@OneToMany`) | `Dominio.Entidades` (`Pedido`, `ItemPedido`, `ItemEstoque`) gain JPA annotations; **no separate persistence model / mapper layer**. Mirrors Pessoa 2's `Cliente`. |
| G2 | **`spring.jpa.hibernate.ddl-auto: none`** (keep `spring.sql.init` `schema.sql`+`data.sql`) | `schema.sql` owns DDL; Hibernate must **not** create/drop tables. Entities map onto the existing schema. (Without this, Hibernate `create-drop` wipes seeded rows — confirmed in the PR #4 review.) |
| G3 | **`spring-boot-starter-data-jpa`** added by Pessoa 2 | Assumed present once PR #4 merges; this plan does not re-add it. |

---

## Decision-INDEPENDENT facts (fixed scope)

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
  but read as `int` in code — reconcile during the entity mapping.
- **Aggregate shape (D14):** `itens_pedido` has no identity of its own; it is persisted with the
  `Pedido` root → `@OneToMany(cascade=ALL, orphanRemoval=true)`.
- **Read-model `PedidoEntregue(Pedido, LocalDateTime dataHoraEntrega)`** is a domain record, not a
  table — the UC10 query projects the `historico_status.data_hora` of the ENTREGUE row alongside the
  order, as a JPQL/`@Query` constructor expression or native query.

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `.claude/PRPs/tele-pizza-backend.prd.md` | §13 Seam #3 (28/06) + OQ#2, §10 "▶ P3", §16 | The resolved decision + ownership split |
| P0 | `reviews/pr-4-review.md` | all | Pessoa 2's JPA precedent + the `ddl-auto`/duplicate-bean lessons to avoid repeating |
| P0 | `Adaptadores/Dados/ClienteRepositoryJPA.java` (post PR #4) | all | The established Approach-A adapter pattern to mirror (EntityManager behind the port) |
| P0 | `src/main/.../Adaptadores/Dados/PedidoRepositoryJDBC.java` | all | The hardest migration — generated key, N+1 item rehydration, UC10 JOIN |
| P0 | `src/main/.../Adaptadores/Dados/ItensEstoqueRepositoryJDBC.java` | 34–46 | **`baixaSeDisponivel` atomic conditional update** — must survive the migration (concurrency invariant) |
| P0 | `src/main/.../Adaptadores/Dados/HistoricoStatusRepositoryJDBC.java` | all | Insert + ordered read |
| P0 | `src/main/.../Dominio/Dados/PedidoRepository.java` | all | The port to preserve verbatim |
| P0 | `src/main/.../Dominio/Entidades/Pedido.java` | 6–43 | Entity to annotate; enum `Status`, `double` money, `LocalDateTime` |
| P0 | `src/main/.../Dominio/Entidades/ItemPedido.java` | all | `Produto item` + `int quantidade` — the join-row of the aggregate |
| P0 | `src/main/.../Dominio/Servicos/PedidoEntregue.java` | all | UC10 read-model projection |
| P1 | `src/main/resources/schema.sql` | `pedidos`/`itens_pedido`/`historico_status` | Current DDL the entities must map onto (ddl-auto=none) |
| P1 | `src/main/resources/application.yaml` | all | `spring.sql.init` + `ddl-auto: none` (from PR #4) |
| P1 | `pom.xml` | 32–67 | `spring-boot-starter-data-jpa` present (from PR #4) |
| P1 | `src/test/.../Adaptadores/Dados/PedidoRepositoryEntreguesTest.java` | all | The IT must stay green post-migration — the migration's authoritative check |

## External Documentation
| Topic | Source | Key Takeaway |
|---|---|---|
| Spring Data JPA repositories | https://docs.spring.io/spring-data/jpa/reference/ | Define an internal `JpaRepository` + an adapter implementing the domain port that delegates to it (or EntityManager directly, as `ClienteRepositoryJPA` does) |
| `@Query` constructor expressions | https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html | Project the ENTREGUE JOIN into `PedidoEntregue` without a managed entity for the read-model |
| Atomic conditional update | https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html (`@Modifying`) | `baixaSeDisponivel` → `@Modifying @Query("UPDATE … WHERE quantidade >= :q")` returning affected-row count to keep the check-then-act race-free |
| H2 + JPA dialect | Spring Boot reference, Data section | Dialect auto-detect; **`ddl-auto: none`** so `schema.sql` owns DDL (G2) |

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
// ADAPTER (new) — implements the SAME port, delegates to a Spring Data JpaRepository (or EntityManager).
// Mirrors ClienteRepositoryJPA (PR #4) and CardapioRepositoryJDBC's port/adapter split (§6.2).
```
Rule: services and UCs must not change. The adapter (`PedidoRepositoryJPA`) implements
`PedidoRepository` and translates between the domain `Pedido` and the JPA layer.

### SINGLE_ADAPTER_PER_PORT  (lesson from PR #4 — do NOT repeat the duplicate-bean bug)
```text
When the JPA adapter is added, the JDBC adapter for the SAME port MUST be deleted in the same change.
Two @Repository beans implementing one Dominio.Dados port → NoUniqueBeanDefinitionException → app won't boot.
(PR #4 shipped both ClienteRepositoryJDBC + ClienteRepositoryJPA and went build-red — see reviews/pr-4-review.md.)
```

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

## Files to Change (Approach A — annotate entities)

| File | Action | Detail |
|---|---|---|
| `Dominio/Entidades/Pedido.java` | UPDATE | `@Entity @Table(name="pedidos")`, `@Id @GeneratedValue(IDENTITY)`, `@Enumerated(STRING) Status`, `@OneToMany(cascade=ALL, orphanRemoval=true)` → `ItemPedido`, money `double` `@Column`, `@ManyToOne`/`cliente_cpf`; `@NoArgsConstructor` (Lombok, as `Cliente`) |
| `Dominio/Entidades/ItemPedido.java` | UPDATE | `@Entity @Table(name="itens_pedido")`, composite key `(pedido_id, produto_id)` (`@IdClass`/`@EmbeddedId`), `int quantidade`, `@ManyToOne Produto` |
| `Dominio/Entidades/ItemEstoque.java` | UPDATE | `@Entity @Table(name="itensEstoque")`, `@Id id`, `quantidade`, `ingrediente_id` |
| `Adaptadores/Dados/*JpaRepository.java` (Spring Data) | CREATE | `PedidoJpaRepository`, `HistoricoStatusJpaRepository`, `ItensEstoqueJpaRepository extends JpaRepository<…>` (or use `EntityManager` directly à la `ClienteRepositoryJPA`) |
| `Adaptadores/Dados/PedidoRepositoryJPA.java` (+ historico, estoque) | CREATE | adapters implementing the domain ports, delegating to the JPA layer |
| `Adaptadores/Dados/*RepositoryJDBC.java` (3 files) | **DELETE** | in the SAME change the JPA adapter is added (SINGLE_ADAPTER_PER_PORT) |
| `src/main/resources/schema.sql` / `application.yaml` | VERIFY | `ddl-auto: none` already set by PR #4; entities must match `schema.sql` exactly; fix `produtos.preco bigint`-vs-`int` |
| `.claude/PRPs/tele-pizza-backend.prd.md` | UPDATE | record P3 Pessoa-1 ✅, Progress Log; flip §7/§8/§11 persistence rows |

## NOT Building
- **Pessoa 2's repos** (`Cliente`, cardápio, desconto) — already migrated / their own scope (Seam #3).
- **The JPA starter / framework decision** — done by Pessoa 2 (PR #4).
- **Any change to domain ports or service/UC signatures** — invisible to the application layer.
- **New behavior or endpoints** — pure persistence-mechanism swap.
- **A separate persistence model / mapper layer** — rejected (Approach B not chosen).

---

## Step-by-Step Tasks

### Task 0 (GATE): Confirm PR #4 is merged to `main`
- **ACTION**: Verify `main` has `spring-boot-starter-data-jpa` in `pom.xml`, `spring.jpa.hibernate.ddl-auto: none` in `application.yaml`, the §13 Seam #3 decision (28/06), and that PR #4 is green/merged. Branch off the updated `main`.
- **VALIDATE**: If the starter or `ddl-auto: none` is missing (PR #4 not merged or not fixed) → **stop**; this plan's foundation isn't on `main` yet. Ping Pessoa 2.

### Task 1: Annotate the order aggregate (Approach A)
- **ACTION**: Annotate `Pedido` (`@Entity`, `@Id @GeneratedValue(IDENTITY)`, `@Enumerated(STRING)` Status, `@OneToMany(cascade=ALL, orphanRemoval=true)` to `ItemPedido`, money `double`), `ItemPedido` (composite key + `@ManyToOne Produto`), and `ItemEstoque`. Mirror `Cliente`'s annotation style from PR #4.
- **MIRROR**: `PORT_PRESERVED_ADAPTER`; aggregate decision D14.
- **GOTCHA**: `pedidos.id` is DB `auto_increment` → `GenerationType.IDENTITY` (not `AUTO`/`SEQUENCE`). `itens_pedido` PK is composite `(pedido_id, produto_id)`, carries `quantidade`. Reconcile `produtos.preco bigint`-vs-`int` here (OQ#6c). With `ddl-auto: none`, annotations must match `schema.sql` exactly — a mismatch fails only at query time, so cross-check column names/types.
- **VALIDATE**: Entities compile; app context loads against `schema.sql`.

### Task 2: Spring Data repositories + port adapters (Pedido, Historico, Estoque)
- **ACTION**: Create the JPA repositories/adapters implementing the three domain ports.
- **IMPLEMENT**: `salvar` → `save` + return generated id; `recuperaPorId` → `findById` mapped to domain (or `null`); `atualizaStatus`/`atualizaDataHoraPagamento` → `@Modifying @Query` or load-mutate-save; `contarPedidosPagosCliente` → `@Query` count with `data_hora_pagamento IS NOT NULL AND >= :desde`; `historico` → `findByPedidoIdOrderByDataHoraAscIdAsc`.
- **MIRROR**: `PORT_PRESERVED_ADAPTER`, `SINGLE_ADAPTER_PER_PORT`.
- **GOTCHA**: Preserve `recuperaPorId` returning **`null`** (not `Optional`/throw) — `ServicoPedido.recuperaPorId` wraps null into the 404, and the fakes return null. **Delete each `*RepositoryJDBC` in the same change** as its JPA replacement (no two beans per port — the PR #4 boot failure).
- **VALIDATE**: existing **unit** tests (POJO fakes) unaffected; app context loads.

### Task 3: UC10 ENTREGUE-window projection
- **ACTION**: Reimplement `entreguesEntre(ini, fim)` as a JPQL/native `@Query` JOIN projecting `PedidoEntregue`.
- **MIRROR**: `UC10_PROJECTION`.
- **GOTCHA**: Keep the half-open `[ini, fim)` window and `ORDER BY data_hora ASC, id ASC`; source the status literal from `Pedido.Status.ENTREGUE`. The read-model is **not** an entity — use a constructor expression or map the projection.
- **VALIDATE**: `PedidoRepositoryEntreguesTest` (the `@SpringBootTest` IT) passes **unchanged** — the authoritative migration check (real H2, real query).

### Task 4: `baixaSeDisponivel` atomic conditional update
- **ACTION**: Port the conditional decrement to `@Modifying @Query("UPDATE … SET quantidade = quantidade - :q WHERE ingrediente_id = :id AND quantidade >= :q")` returning the affected-row count → boolean.
- **MIRROR**: `ATOMIC_CONDITIONAL_BAIXA`.
- **GOTCHA**: Return `true` only when a row was updated. `@Modifying` needs a transaction — `ServicoPedido.submeter`/`cancelar` are already `@Transactional` and call `ServicoEstoque` within them. Add `clearAutomatically`/`flushAutomatically` if stale first-level cache risks a wrong subsequent read.
- **VALIDATE**: `ServicoEstoqueTest.baixaSemSaldoLancaInsuficiente` still passes (unit, fake) **and** an H2-backed check confirms concurrent under-balance rolls back.

### Task 5: Delete JDBC adapters + verify init policy
- **ACTION**: Confirm `ddl-auto: none` (from PR #4) and `schema.sql`+`data.sql` init. Delete the three `*RepositoryJDBC` classes once the JPA adapters pass.
- **GOTCHA**: `data.sql` re-runs every boot (§3) and seeds clients/produtos the FKs need. Two beans per port = startup failure → delete JDBC adapters in the same change that adds the JPA ones (SINGLE_ADAPTER_PER_PORT).
- **VALIDATE**: `./mvnw test` green; app boots; `/pedidos` smoke (submit→status→pay) works on :8090.

### Task 6: Update the PRD
- **ACTION**: Flip P3 status (Pessoa-1 repos ✅) in §9, append Progress Log, update §3/§5/§7/§8/§11 persistence-style rows (Pessoa-1 repos now JPA).
- **VALIDATE**: PRD consistent.

---

## Testing Strategy
- **Unit (fakes) — unchanged:** `ServicoPedidoTest`, `ServicoEstoqueTest`, `SubmeterPedidoParaAprovacaoUCTest`, `PagarPedidoUCTest` use POJO fakes of the ports, so they must pass **without edits** — proof the ports were preserved.
- **Integration — authoritative:** `PedidoRepositoryEntreguesTest` (`@SpringBootTest`, real H2) must stay green; it validates the UC10 JOIN after migration.
- **Context-load:** `Ex4LancheriadddV1ApplicationTests.contextLoads` must pass — the canary for the duplicate-bean / `ddl-auto` class of failures that broke PR #4.

### Edge Cases Checklist
- [ ] Generated id returned correctly (`IDENTITY`)
- [ ] `recuperaPorId` returns `null` (not throw/Optional) for missing — 404 contract
- [ ] Aggregate cascade: saving a `Pedido` persists its `itens_pedido`
- [ ] `baixaSeDisponivel` atomic under concurrency (no check-then-act race)
- [ ] UC10 half-open window `[ini, fim)` + ENTREGUE-only filter
- [ ] `data.sql` seeds still load (FK targets present) with `ddl-auto: none`
- [ ] `produtos.preco` type reconciled (bigint vs int)
- [ ] **No two `@Repository` beans for one port** (delete JDBC with JPA add) — the PR #4 trap

---

## Validation Commands
```bash
export JAVA_HOME=/snap/android-studio/230/jbr   # JDK 21 (system 17 fails)
./mvnw -q compile          # EXPECT: success
./mvnw -q test             # EXPECT: existing unit fakes + the entregues IT + contextLoads all green (no test edits)
SERVER_PORT=8090 ./mvnw spring-boot:run   # EXPECT: boots; schema/data load with ddl-auto=none
# Smoke: submit an order, query status, pay — confirm persistence round-trips
```

## Acceptance Criteria
- [ ] PR #4 merged to `main` (starter + `ddl-auto: none` + §13 decision present) — Task 0 gate.
- [ ] Three Pessoa-1 adapters on JPA; domain ports unchanged; **JDBC adapters deleted** (one adapter per port).
- [ ] All existing unit tests pass **without modification**; the entregues IT and `contextLoads` pass.
- [ ] `baixaSeDisponivel` remains atomic; UC10 window semantics preserved.
- [ ] `preco` type mismatch resolved.
- [ ] PRD updated (P3 Pessoa-1 ✅, Progress Log).

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Starting before PR #4 merges → no starter / `ddl-auto` on `main` | Medium | High | **Task 0 gate**; branch only off updated `main` |
| Two beans per port at switchover (the PR #4 bug) | Medium | High | Delete each JDBC adapter in the same change as its JPA one (SINGLE_ADAPTER_PER_PORT) |
| `recuperaPorId` contract drift (Optional/throw) breaks 404 + fakes | Medium | High | Preserve `null`-return semantics exactly (Task 2 gotcha) |
| Losing the atomic baixa (race reintroduced) | Medium | High | `@Modifying` conditional update returning row-count, never read-then-write |
| Entity annotations diverge from `schema.sql` under `ddl-auto: none` | Medium | Medium | Cross-check column names/types against `schema.sql`; `contextLoads` + IT catch most |
| `@Entity` on `Pedido`/`ItemPedido` breaks the pure-POJO rule | Accepted | — | Approach A was chosen project-wide (Seam #3); consistent with `Cliente` |

## Notes
- This is the **last remaining Pessoa-1 task** (P6 ✅, UC10 ✅). Seam #3 is resolved; only the PR #4
  merge stands between this plan and execution.
- JDK 21 mandatory (memory `build-requires-jdk21`); local JBR at `/snap/android-studio/230/jbr`.
- Learn from PR #4's two defects (duplicate adapter bean; missing `ddl-auto: none`) — both are encoded
  as explicit gotchas/checklist items above so Pessoa 1's migration doesn't repeat them.
