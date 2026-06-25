# Plan: UC10 — Listar pedidos entregues entre 2 datas (Pessoa 1)

## Summary
Add the read-only use case **UC10**: list orders that reached **ENTREGUE** with their delivery
timestamp inside a `[ini, fim]` date window. This is the next **unblocked, fully Pessoa-1-owned**
deliverable (Seam #4): it reads the `pedidos`/`historico_status` tables Pessoa 1 already built in P2,
needs no input from Pessoa 2, and adds one repository query + service method + UC + controller endpoint
+ presenter, mirroring the existing order-cycle stack.

## User Story
As a **customer or admin (Adm)**, I want **to list the orders delivered between two dates**, so that
**I can review delivery history for a period** (reporting / lookup).

## Problem → Solution
**Current:** the order lifecycle persists every status transition in `historico_status`, but there is no
way to query which orders were **delivered** in a given period. → **Desired:** `GET /pedidos/entregues?ini=&fim=`
returns the orders whose `ENTREGUE` transition timestamp falls within `[ini, fim]`.

## Metadata
- **Complexity**: Small–Medium (1 endpoint, ~7 new files + 2 edits, ~250 lines incl. tests)
- **Source PRD**: `.claude/PRPs/tele-pizza-backend.prd.md`
- **PRD Phase**: P4 (22/06) — **UC10 only** (UC11/UC12 are Pessoa 2; do **not** touch them). Seam #4.
- **Estimated Files**: 7 new, 2 edited

---

## Why UC10 now (and not JPA / P3)

Pessoa 1 has two candidate next tasks per the PRD load-balancing note (§16):
1. **UC10** — fully Pessoa-1-owned (Seam #4), reads Pessoa 1's own `pedidos` table, **no cross-person dependency. UNBLOCKED.** ← this plan.
2. **JPA migration of Pessoa 1's own repos (P3)** — **BLOCKED on Seam #3**: Pessoa 2 must first make the
   JPA framework decision (annotate entities vs. separate persistence model + `ddl-auto`/init-mode, open
   question #2) before Pessoa 1 migrates `Pedido*`/`ItensEstoque`. Cannot start cleanly yet.

UC10 is therefore the correct next pass. It also keeps committed contributions balanced (grading note, §9)
while Pessoa 2 builds the Cozinha/Entrega sims and the auth/user skeleton.

---

## UX Design

Internal/backend change — one new REST endpoint. No UI.

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| `GET /pedidos/entregues?ini=YYYY-MM-DD&fim=YYYY-MM-DD` | 404 (no route) | `200` JSON array of delivered-order summaries | Window inclusive on whole days |
| `ini > fim` or missing param | — | `400` (IllegalArgument / missing-param) | Validated in UC / by Spring |

Example response (array of `PedidoEntreguePresenter`):
```json
[
  { "id": 7, "clienteCpf": "12345", "status": "ENTREGUE",
    "valorCobrado": 148.5, "enderecoEntrega": "Rua X, 10", "dataHoraEntrega": "2026-06-20T19:42:11" }
]
```

---

## Key design decision (record as **D15** in PRD §13 on implementation)

**D15 — UC10 filters by the `ENTREGUE` transition timestamp in `historico_status`, not by any column on
`pedidos`.** `pedidos` has no delivery-time column (it has only `data_hora_pagamento`). The delivery
instant is the timestamp of the `ENTREGUE` row in `historico_status` (Seam #2 makes `historico_status`
the single source of status-time truth). So the repo query **JOINs** `pedidos` to `historico_status` on
`status='ENTREGUE'` and ranges on `historico_status.data_hora`. **No new column, no entity mutation** —
consistent with D11's "derive, don't add persisted flags" philosophy.

- **Window semantics:** `ini`/`fim` are **dates** (`yyyy-MM-dd`). The UC expands them to a datetime window
  `[ini.atStartOfDay(), fim.plusDays(1).atStartOfDay())` (start-inclusive, end-exclusive) so the whole
  `fim` day is included regardless of delivery time-of-day. Repo takes two `LocalDateTime` bounds.
- **Actor scope:** returns **all** delivered orders in the window (admin-style view). Per-customer
  filtering is deferred to P5 (auth), per the §7 note "treat the actor as a parameter/header until auth."

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `Adaptadores/Dados/PedidoRepositoryJDBC.java` | 1-135 | The JDBC repo to extend; reuse its `Pedido` row-mapping + `recuperaItens` |
| P0 | `Dominio/Dados/PedidoRepository.java` | all | Port to extend with the new query (Seam #4 says it lives here) |
| P0 | `Aplicacao/ConsultarStatusPedidoUC.java` | all | UC shape to mirror (`@Component`, single `run`) |
| P0 | `Aplicacao/Responses/PedidoStatusResponse.java` | all | Response DTO + static `de(...)` factory convention |
| P0 | `Adaptadores/Apresentacao/PedidoController.java` | 1-75 | Controller to add the endpoint to; Response→Presenter mapping in-controller |
| P1 | `Dominio/Servicos/ServicoPedido.java` | 1-131 | Service to add `listarEntreguesEntre(...)`; conventions (`@Service`, ctor inject) |
| P1 | `Dominio/Entidades/Pedido.java` | all | Entity getters available (no setter for delivery time — do **not** add one) |
| P1 | `test/.../Dominio/Servicos/ServicoPedidoTest.java` | 36-168 | Test conventions: comment-form case list, in-memory fakes, builders, `DELTA` |
| P1 | `Adaptadores/Dados/HistoricoStatusRepositoryJDBC.java` | all | JOIN target table columns (`pedido_id, status, data_hora`) |
| ref | `src/main/resources/schema.sql` | 73-103 | `pedidos` / `historico_status` columns for the JOIN |
| ref | `Adaptadores/Apresentacao/RestExceptionHandler.java` | all | `IllegalArgumentException→400` already wired (no new handler needed) |

## External Documentation
No external research needed — feature uses established internal patterns (JdbcTemplate query, `@Component`
UC, controller/presenter) plus one standard Spring annotation (`@DateTimeFormat`/`@RequestParam`).

| Topic | Source | Key Takeaway |
|---|---|---|
| `@RequestParam` date binding | Spring Web (already on classpath) | `@RequestParam @DateTimeFormat(iso=DATE) LocalDate ini` binds `?ini=2026-06-20`; missing param → `MissingServletRequestParameterException` → 400 by default |

---

## Patterns to Mirror

### REPOSITORY_PORT (add one method)
```java
// SOURCE: Dominio/Dados/PedidoRepository.java:9-15
public interface PedidoRepository {
    long salvar(Pedido pedido);
    Pedido recuperaPorId(long id);
    void atualizaStatus(long id, Pedido.Status novo);
    void atualizaDataHoraPagamento(long id, LocalDateTime quando);
    int contarPedidosPagosCliente(String cpf, LocalDateTime desde);
    // NEW: pedidos com transicao ENTREGUE no intervalo [ini, fim) — Seam #4
    List<PedidoEntregue> entreguesEntre(LocalDateTime ini, LocalDateTime fim);
}
```

### REPOSITORY_JDBC (mirror parameterized query + inline RowMapper; reuse recuperaItens)
```java
// SOURCE: Adaptadores/Dados/PedidoRepositoryJDBC.java:74-105  (recuperaPorId + recuperaItens row-mapping)
String sql = "SELECT p.id, p.cliente_cpf, p.status, p.valor, p.impostos, p.desconto, p.valor_cobrado, " +
             "p.data_hora_pagamento, p.endereco_entrega, h.data_hora AS data_hora_entrega " +
             "FROM pedidos p JOIN historico_status h ON h.pedido_id = p.id " +
             "WHERE h.status = 'ENTREGUE' AND h.data_hora >= ? AND h.data_hora < ? " +
             "ORDER BY h.data_hora ASC, p.id ASC";
return this.jdbcTemplate.query(sql,
    ps -> { ps.setTimestamp(1, Timestamp.valueOf(ini)); ps.setTimestamp(2, Timestamp.valueOf(fim)); },
    (rs, n) -> { /* build Pedido (status ENTREGUE) + delivery timestamp -> PedidoEntregue */ });
```
Rules (from §6.2): Portuguese method names; `?` placeholders with 1-based `ps.setX`; inline lambda
`RowMapper`; `@Repository` on the adapter. An order can have **only one** `ENTREGUE` row (terminal,
single-writer Seam #2), so no `DISTINCT` needed — but `ORDER BY h.data_hora, p.id` keeps output deterministic.

### SERVICE_METHOD (thin, `@Service`, ctor-injected — extend existing ServicoPedido)
```java
// SOURCE: Dominio/Servicos/ServicoPedido.java:94-105 (read methods delegate to the repo)
public List<PedidoEntregue> listarEntreguesEntre(LocalDateTime ini, LocalDateTime fim) {
    if (ini == null || fim == null) throw new IllegalArgumentException("Datas ini e fim sao obrigatorias");
    if (ini.isAfter(fim)) throw new IllegalArgumentException("Data inicial nao pode ser posterior a final: " + ini + " > " + fim);
    return pedidoRepository.entreguesEntre(ini, fim);
}
```
> The service takes already-expanded `LocalDateTime` bounds; **the UC owns the LocalDate→window expansion**
> (keeps the domain service free of presentation-format concerns).

### USE_CASE (`@Component`, single `run`, returns Response)
```java
// SOURCE: Aplicacao/ConsultarStatusPedidoUC.java:10-23
@Component
public class ListarPedidosEntreguesUC {
    private final ServicoPedido servicoPedido;
    @Autowired public ListarPedidosEntreguesUC(ServicoPedido servicoPedido){ this.servicoPedido = servicoPedido; }
    public PedidosEntreguesResponse run(LocalDate ini, LocalDate fim) {
        // expansao da janela: dia fim inteiro incluso (fim de semana exclusivo no fim)
        List<PedidoEntregue> entregues = servicoPedido.listarEntreguesEntre(
            ini.atStartOfDay(), fim.plusDays(1).atStartOfDay());
        return PedidosEntreguesResponse.de(entregues);
    }
}
```

### RESPONSE_DTO (record + static `de(...)` factory, like PedidoStatusResponse)
```java
// SOURCE: Aplicacao/Responses/PedidoStatusResponse.java:10-18
public record PedidosEntreguesResponse(List<PedidoEntregueResponse> pedidos) {
    public static PedidosEntreguesResponse de(List<PedidoEntregue> entregues) {
        return new PedidosEntreguesResponse(entregues.stream()
            .map(pe -> new PedidoEntregueResponse(
                pe.pedido().getId(), pe.pedido().getCliente().getCpf(), pe.pedido().getStatus().name(),
                pe.pedido().getValorCobrado(), pe.pedido().getEnderecoEntrega(),
                pe.dataHoraEntrega().toString()))
            .toList());
    }
}
```

### CONTROLLER_ENDPOINT (add method; map Response→Presenter in-controller)
```java
// SOURCE: Adaptadores/Apresentacao/PedidoController.java:54-74 (GET + Response->Presenter mapping)
@GetMapping("/entregues")
public List<PedidoEntreguePresenter> entregues(
        @RequestParam("ini") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ini,
        @RequestParam("fim") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
    return listarEntreguesUC.run(ini, fim).pedidos().stream()
        .map(r -> new PedidoEntreguePresenter(r.id(), r.clienteCpf(), r.status(),
            r.valorCobrado(), r.enderecoEntrega(), r.dataHoraEntrega()))
        .toList();
}
```

### TEST_STRUCTURE (comment-form case list + in-memory fakes, no Spring for unit)
```java
// SOURCE: test/.../ServicoPedidoTest.java:36-79  (comment block enumerating cases + FakePedidoRepository)
/*
 * Casos de teste -- ListarPedidosEntregues (UC10):
 *  1. janelaVaziaRetornaListaVazia         : sem entregas no periodo -> []
 *  2. ...
 */
private static class FakePedidoRepository implements PedidoRepository { ... entreguesEntre(...) ... }
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `Dominio/Servicos/PedidoEntregue.java` | CREATE | Read-model record `(Pedido pedido, LocalDateTime dataHoraEntrega)` — carries the delivery instant without mutating the `Pedido` entity (mirrors `TransicaoStatus`) |
| `Dominio/Dados/PedidoRepository.java` | UPDATE | Add port method `entreguesEntre(ini, fim)` (Seam #4 — query lives here) |
| `Adaptadores/Dados/PedidoRepositoryJDBC.java` | UPDATE | Implement the JOIN query, reusing the existing `Pedido` row-mapping + `recuperaItens` |
| `Dominio/Servicos/ServicoPedido.java` | UPDATE | Add thin `listarEntreguesEntre(...)` with ini≤fim validation |
| `Aplicacao/Responses/PedidoEntregueResponse.java` | CREATE | Per-order DTO (record) |
| `Aplicacao/Responses/PedidosEntreguesResponse.java` | CREATE | List wrapper DTO + static `de(...)` factory |
| `Aplicacao/ListarPedidosEntreguesUC.java` | CREATE | `@Component` UC; owns LocalDate→window expansion |
| `Adaptadores/Apresentacao/Presenters/PedidoEntreguePresenter.java` | CREATE | View-model record |
| `Adaptadores/Apresentacao/PedidoController.java` | UPDATE | Inject the UC; add `GET /entregues` |
| `test/.../Dominio/Servicos/ServicoPedidoTest.java` | UPDATE | Add UC10 service cases + extend `FakePedidoRepository` with `entreguesEntre` |
| `test/.../Adaptadores/Dados/PedidoRepositoryEntreguesIT.java` | CREATE | `@SpringBootTest` integration test exercising the **real SQL JOIN** against H2 (net-new query, no precedent) |

## NOT Building
- **No per-customer filtering / auth** — returns all delivered orders in the window; auth + scoping is P5.
- **No `pedidos` delivery-time column and no setter on `Pedido`** — delivery instant comes from `historico_status` (D15); keeps the entity immutable-shaped and within scope.
- **No pagination** — course-scale data; add only if a real need appears (YAGNI).
- **No changes to UC11/UC12 or any Pessoa 2 file** — P4 is multi-owner; this plan is UC10 only.
- **No new exception handler** — `IllegalArgumentException→400` and missing-param→400 are already covered.

---

## Step-by-Step Tasks

### Task 1: Read-model record `PedidoEntregue`
- **ACTION**: Create `Dominio/Servicos/PedidoEntregue.java`.
- **IMPLEMENT**: `public record PedidoEntregue(Pedido pedido, java.time.LocalDateTime dataHoraEntrega) {}`.
- **MIRROR**: `Dominio/Servicos/TransicaoStatus.java` (existing value record in the same package).
- **IMPORTS**: `com.bcopstein...Dominio.Entidades.Pedido`, `java.time.LocalDateTime`.
- **GOTCHA**: Put it in `Dominio.Servicos` (not `Aplicacao`) — it is a domain read-model returned by the repo port; no Spring/JPA annotations.
- **VALIDATE**: `./mvnw -q compile`.

### Task 2: Extend the repository port
- **ACTION**: Add `List<PedidoEntregue> entreguesEntre(LocalDateTime ini, LocalDateTime fim);` to `PedidoRepository`.
- **IMPLEMENT**: One method signature + a short Portuguese comment ("pedidos com transicao ENTREGUE no intervalo [ini, fim)").
- **MIRROR**: `PedidoRepository.java` existing method comments.
- **IMPORTS**: `java.util.List`, `PedidoEntregue` (already imports `LocalDateTime`, `Pedido`).
- **GOTCHA**: Keep the half-open contract explicit in the comment (`ini` inclusive, `fim` exclusive) — the UC relies on it.
- **VALIDATE**: `./mvnw -q compile` (will fail until Task 3 implements it — expected RED).

### Task 3: Implement the JDBC JOIN query
- **ACTION**: Implement `entreguesEntre` in `PedidoRepositoryJDBC`.
- **IMPLEMENT**: The SQL from **REPOSITORY_JDBC** above. In the RowMapper, build the `Pedido` exactly as
  `recuperaPorId` does (same columns + `recuperaItens(rs.getLong("id"))`), then wrap it with
  `rs.getTimestamp("data_hora_entrega").toLocalDateTime()` into a `PedidoEntregue`.
- **MIRROR**: `recuperaPorId` (lines 74-94) for the `Pedido` construction; `historico` ordering idiom (`ORDER BY data_hora ASC, id ASC`).
- **IMPORTS**: `java.sql.Timestamp`, `java.time.LocalDateTime`, `PedidoEntregue` (others already present).
- **GOTCHA**: Use `ps.setTimestamp(i, Timestamp.valueOf(ldt))` (the codebase's LocalDateTime↔Timestamp idiom). The `ENTREGUE` row is unique per order (Seam #2), so no `DISTINCT`. Re-hydrating items is N+1 but consistent with `recuperaPorId` and fine at course scale (note in a comment).
- **VALIDATE**: `./mvnw -q compile`; covered by the IT in Task 8.

### Task 4: Service method on `ServicoPedido`
- **ACTION**: Add `listarEntreguesEntre(LocalDateTime ini, LocalDateTime fim)`.
- **IMPLEMENT**: The **SERVICE_METHOD** snippet — null-guard + `ini.isAfter(fim)`→`IllegalArgumentException`, then delegate to the repo. Read-only (no `@Transactional` needed; matches `recuperaPorId`/`historico`).
- **MIRROR**: `ServicoPedido.recuperaPorId` / `historico` (delegation style).
- **IMPORTS**: `java.util.List`, `PedidoEntregue` (already imports `LocalDateTime`).
- **GOTCHA**: Validate `ini > fim` here so the rule is enforced regardless of caller; the UC passes an
  already-expanded window where `ini < fim` always holds for valid input, but a same-day request
  (`ini==fim`) expands to a valid non-empty window — do **not** reject `ini==fim` at the LocalDate level.
- **VALIDATE**: `./mvnw -q compile`.

### Task 5: Response DTOs
- **ACTION**: Create `Aplicacao/Responses/PedidoEntregueResponse.java` and `PedidosEntreguesResponse.java`.
- **IMPLEMENT**: `PedidoEntregueResponse(long id, String clienteCpf, String status, double valorCobrado, String enderecoEntrega, String dataHoraEntrega)`; `PedidosEntreguesResponse(List<PedidoEntregueResponse> pedidos)` with the static `de(List<PedidoEntregue>)` factory (**RESPONSE_DTO** snippet).
- **MIRROR**: `PedidoStatusResponse` (record + `de` factory; `.toString()` on the timestamp, as done there).
- **IMPORTS**: `java.util.List`, `PedidoEntregue`.
- **GOTCHA**: Serialize `dataHoraEntrega` as `String` via `.toString()` (ISO-8601) to match how `PedidoStatusResponse` renders timestamps — keeps JSON shape consistent across the order API.
- **VALIDATE**: `./mvnw -q compile`.

### Task 6: Use case
- **ACTION**: Create `Aplicacao/ListarPedidosEntreguesUC.java`.
- **IMPLEMENT**: The **USE_CASE** snippet — `run(LocalDate ini, LocalDate fim)` expands to
  `[ini.atStartOfDay(), fim.plusDays(1).atStartOfDay())` and returns `PedidosEntreguesResponse.de(...)`.
- **MIRROR**: `ConsultarStatusPedidoUC` (`@Component`, ctor inject, single `run`).
- **IMPORTS**: `java.time.LocalDate`, `java.util.List`, `PedidoEntregue`, the two Response types, `ServicoPedido`.
- **GOTCHA**: Add a guard `if (ini.isAfter(fim)) throw new IllegalArgumentException(...)` **before** expansion too (clearer message at the date level); the service guard is the backstop.
- **VALIDATE**: `./mvnw -q compile`.

### Task 7: Presenter + controller endpoint
- **ACTION**: Create `Presenters/PedidoEntreguePresenter.java`; add `GET /entregues` to `PedidoController`.
- **IMPLEMENT**: `PedidoEntreguePresenter(long id, String clienteCpf, String status, double valorCobrado, String enderecoEntrega, String dataHoraEntrega)`. Inject `ListarPedidosEntreguesUC` via the constructor; add the **CONTROLLER_ENDPOINT** method.
- **MIRROR**: `PedidoController.status` (Response→Presenter in-controller); `TransicaoStatusPresenter` (record presenter).
- **IMPORTS**: `org.springframework.web.bind.annotation.RequestParam`, `org.springframework.format.annotation.DateTimeFormat`, `java.time.LocalDate`, `java.util.List`.
- **GOTCHA**: Register the route as `@GetMapping("/entregues")` — it must **not** collide with `@GetMapping("/{id}/status")`; `/entregues` is a literal segment so Spring matches it before `{id}` patterns, but keep the path exactly `/entregues` (no trailing `{id}`). CORS is global (`CorsConfig`) — do **not** add `@CrossOrigin`.
- **VALIDATE**: `./mvnw -q compile`; then live smoke (see Validation Commands).

### Task 8: Tests (unit + integration)
- **ACTION**: (a) Extend `ServicoPedidoTest` with UC10 service cases; (b) create `PedidoRepositoryEntreguesIT` (`@SpringBootTest`) for the real SQL.
- **IMPLEMENT**:
  - (a) Add `entreguesEntre(...)` to the in-file `FakePedidoRepository` (store delivered orders + their ENTREGUE time; filter by `>= ini && < fim`). Add a comment-form case block and tests: empty window → `[]`; in-window order returned; out-of-window excluded; **boundary** (delivery exactly at `ini` start-of-day included; exactly at `fim+1` start-of-day excluded); `ini.isAfter(fim)` → `IllegalArgumentException`.
  - (b) IT: boot context, insert via `JdbcTemplate`/H2 a client + a `pedidos` row + a `historico_status` `ENTREGUE` row with a known timestamp, call the real `PedidoRepositoryJDBC.entreguesEntre`, assert it is returned with the right `dataHoraEntrega`; insert a second order delivered outside the window and assert it is excluded; insert an order that is `PAGO`-only (no ENTREGUE row) and assert excluded.
- **MIRROR**: `ServicoPedidoTest` (fakes, builders, `DELTA`, comment block); `Ex4LancheriadddV1ApplicationTests` for `@SpringBootTest` bootstrapping.
- **IMPORTS**: JUnit 5, `@SpringBootTest`, `@Autowired JdbcTemplate`/`PedidoRepository`.
- **GOTCHA**: In the IT, remember **`schema.sql`+`data.sql` reload every boot** and tests share the in-memory DB — insert with high/explicit ids or clean up, and don't assume an empty `pedidos` table. The status filter is a **string** literal `'ENTREGUE'` — keep it in sync with `Pedido.Status.ENTREGUE.name()`.
- **VALIDATE**: `./mvnw -q test`.

---

## Testing Strategy

### Unit Tests (service, with `FakePedidoRepository`)
| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `janelaVaziaRetornaListaVazia` | no delivered orders | `[]` | — |
| `entregaNaJanelaRetorna` | 1 order ENTREGUE inside window | list of 1, right id + dataHoraEntrega | — |
| `entregaForaDaJanelaExcluida` | order delivered after `fim` | `[]` | yes |
| `limiteInicioInclusivo` | delivery == `ini` start-of-day | included | boundary |
| `limiteFimInclusivoNoDiaTodo` | delivery at `fim` 23:59 | included | boundary |
| `limiteAposFimExcluido` | delivery at `fim+1` 00:00 | excluded | boundary |
| `iniDepoisDeFimLanca` | `ini > fim` | `IllegalArgumentException` | yes |

### Integration Test (`@SpringBootTest`, real H2 + JDBC)
| Test | Setup | Expected |
|---|---|---|
| `retornaEntreguesNoIntervalo` | insert client+pedido+historico ENTREGUE in-window | returned with correct `dataHoraEntrega` |
| `excluiForaDoIntervalo` | a second ENTREGUE outside window | not returned |
| `excluiSemEntregue` | a pedido with only PAGO in historico | not returned |

### Edge Cases Checklist
- [x] Empty result (window with no deliveries)
- [x] Inclusive start / inclusive whole `fim` day / exclusive `fim+1`
- [x] `ini > fim` → 400
- [x] Missing `ini`/`fim` query param → 400 (Spring default)
- [x] Order delivered but with status later changed? N/A — ENTREGUE is terminal in this domain
- [ ] Concurrency / network — N/A (read-only, in-memory)

---

## Validation Commands

### Static Analysis / Build
```bash
# JDK 21 required (system default may be 17). Use the bundled JDK 21 as in prior phases:
export JAVA_HOME=/snap/android-studio/232/jbr   # or any JDK 21
./mvnw -q compile
```
EXPECT: build success, no compile errors.

### Unit + Integration Tests
```bash
./mvnw -q test
```
EXPECT: all tests pass, including the new UC10 unit cases + `PedidoRepositoryEntreguesIT`. Suite count grows by ~10.

### Live smoke (manual)
```bash
# Port 8080 may be occupied -> prior phases used 8090:
SERVER_PORT=8090 ./mvnw spring-boot:run
# empty window (no order reaches ENTREGUE at runtime yet -> expect []):
curl -s "localhost:8090/pedidos/entregues?ini=2026-06-01&fim=2026-06-30"   # -> []
# bad range -> 400:
curl -s -o /dev/null -w "%{http_code}\n" "localhost:8090/pedidos/entregues?ini=2026-06-30&fim=2026-06-01"   # -> 400
# missing param -> 400:
curl -s -o /dev/null -w "%{http_code}\n" "localhost:8090/pedidos/entregues?ini=2026-06-01"   # -> 400
```
> **GOTCHA (manual non-empty check):** no order can reach **ENTREGUE** through the running app until
> **Pessoa 2** ships the Entrega sim. To smoke a non-empty result, seed an `ENTREGUE` row via the H2
> console (`http://localhost:8090/h2`): insert a `clientes` row, a `pedidos` row, then
> `INSERT INTO historico_status(pedido_id,status,data_hora) VALUES (<id>,'ENTREGUE', CURRENT_TIMESTAMP);`
> and re-query. The **integration test (Task 8b) is the authoritative non-empty validation** — do not
> add a permanent seed to `data.sql`.

### Database Validation
```bash
# Confirm the JOIN columns exist (no schema change needed for UC10):
grep -nE "historico_status|pedidos" src/main/resources/schema.sql
```
EXPECT: `pedidos` + `historico_status` present; **no DDL change required by this plan.**

---

## Acceptance Criteria
- [ ] `GET /pedidos/entregues?ini=&fim=` returns a JSON array of orders whose `ENTREGUE` transition is in the day-window.
- [ ] `ini > fim` and missing params → 400.
- [ ] Window includes the entire `fim` day; excludes `fim+1`.
- [ ] All validation commands pass; `./mvnw -q test` green.
- [ ] No type/compile errors; no changes to UC11/UC12 or any Pessoa 2 file.
- [ ] D15 recorded in PRD §13; UC10 row + Progress Log updated.

## Completion Checklist
- [ ] Code mirrors discovered patterns (repo port/JDBC, `@Component` UC, Response `de` factory, in-controller presenter mapping).
- [ ] Error handling reuses the existing `RestExceptionHandler` (no new advice).
- [ ] No mutation of `Pedido`; `PedidoEntregue` carries the delivery instant.
- [ ] Tests follow §6.8 (comment-form case list, fakes, `DELTA`, `@SpringBootTest` for the IT).
- [ ] No hardcoded values beyond the `'ENTREGUE'` status literal (kept in sync with the enum).
- [ ] Self-contained — no further codebase searching needed to implement.

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| `'ENTREGUE'` string literal drifts from `Pedido.Status` enum | Low | Med | Comment cross-refs the enum; IT asserts a real delivered order is returned |
| N+1 re-hydration of items per delivered order | Med | Low | Acceptable at course scale (mirrors `recuperaPorId`); note in comment; revisit in P3/JPA |
| Route `/entregues` vs `/{id}/status` ambiguity | Low | Med | Literal segment matches before `{id}`; IT/smoke confirms 200 on `/entregues` |
| Manual non-empty smoke impossible until Pessoa 2's sim | High | Low | Integration test is the authoritative check; document H2-console seeding for manual demo |
| Filtering by `data_hora_pagamento` instead of ENTREGUE (wrong interpretation) | Low | High | D15 fixes the semantics explicitly; boundary tests assert the ENTREGUE-time window |

## Notes
- **Seam #4 confirmed:** the query lives on `PedidoRepository` and Pessoa 1 owns the full UC10 vertical
  (UC + controller + presenter), per the §13 recommendation. Mark Seam #4 "resolved — Pessoa 1 owns full UC10" when implemented.
- **JPA (P3) is the other Pessoa 1 task but is gated on Seam #3** (Pessoa 2's framework decision). Start it
  only after that decision is recorded in §13 / open question #2.
- On completion: flip the **UC10** row in §7 to ✅ (leave P4 phase status as-is — UC11/UC12 remain Pessoa 2's),
  append a dated Progress Log entry (§14), and record **D15** in §13.
- Confidence for single-pass implementation: **9/10** — pure mirror of the existing order stack; the only
  net-new element is the JOIN query, which the integration test covers directly.
