# Plan: P2 — Ciclo do Pedido (Pessoa 1) — UC6, UC7, UC8, UC9

## Summary
Implements the **order cycle** owned by **Pessoa 1** for phase **P2**: submit an order for approval (UC6), query its status (UC7), cancel it (UC8), and pay it (UC9). Adds the `Pedido` aggregate persistence, the `ServicoPedido` (sole writer of order status + history — Seam #2), `ServicoEstoque` (ingredient stock check/decrement), and a fake `IPagamentoService`. Wires the cost formula `custoFinal = (Σ itens − desconto) + imposto` through the existing P1 `ServicoImposto` / `ServicoDesconto`. The Cozinha/Entrega simulations are **Pessoa 2**'s and are consumed here only through frozen interfaces (Seam #1).

## User Story
> As a **customer**, I want to **submit a cart for approval, check its status, cancel it before paying, and pay it**, so that **a priced, stock-validated order flows into the kitchen and delivery pipeline with every status change timestamped and trackable by order number**.

## Problem → Solution
- **Now:** The `Pedido`/`ItemPedido` entities exist as unwired POJOs. There is **no** `pedidos`/`itens_pedido`/`historico_status` table, **no** `PedidoRepository`, **no** `ServicoPedido`/`ServicoEstoque`, **no** order endpoints. The `Pedido.Status` enum has no `CANCELADO`/`RECUSADO`. The Cozinha sim is dead code. There is no JDBC `INSERT`/`UPDATE`/generated-key precedent and no `@PostMapping`/`@RequestBody` precedent.
- **After:** A `POST /pedidos` prices + stock-validates a cart (NOVO → APROVADO, or → RECUSADO highlighting unfulfillable items); `GET /pedidos/{id}/status` returns the current status + full timestamped history; `POST /pedidos/{id}/cancelar` cancels an APROVADO-not-paid order; `POST /pedidos/{id}/pagar` pays a fake-approved order (→ PAGO, stamps payment time) and hands it to the kitchen sim, which (with the delivery sim) drives PREPARACAO → PRONTO → TRANSPORTE → ENTREGUE — **each transition persisted with a timestamp through the single writer `ServicoPedido`.**

## Metadata
- **Complexity:** **Large** (≈25 new + 3 edited files, ~13 tasks; net-new persistence + web patterns).
- **Source PRD:** `.claude/PRPs/tele-pizza-backend.prd.md`
- **PRD Phase:** **P2 (15/06)** — Ciclo do pedido. **Owner: Pessoa 1 (lead).** Pessoa 2 builds the Cozinha/Entrega sims in parallel (Seam #1).
- **Scope of THIS plan:** Pessoa 1's P2 deliverable only. Later Pessoa-1 work (UC10 P4, JPA-of-own-repos P3, P6 tests beyond the unit tests added here) is out of scope — see **NOT Building**.
- **Base package:** `com.bcopstein.ex4_lancheriaddd_v1` · **Stack:** Java 21 · Spring Boot 3.5.4 · `spring-boot-starter-jdbc` + H2 (in-mem) · JUnit 5, no Mockito.
- **Repo root note:** PRD calls the project root `ex5-pizzaria-clean-baseT1/`; the working tree is actually `FDS_T3/`. All paths below are repo-relative (`src/main/...`).

---

## Seam contracts to FREEZE before coding (prerequisite)

> **Seam #1 (PRD §13/§16): agree these signatures with Pessoa 2 in the 30-min sync, then they do not change during P2.** Pessoa 1 codes against the interfaces; Pessoa 2 supplies the Cozinha/Entrega `@Service` impls.

```java
// EXISTS — review & freeze as-is. Pessoa 1 calls chegadaDePedido() on UC9 pay handoff.
public interface ICozinhaService {            // Dominio/Servicos/ICozinhaService.java
    void chegadaDePedido(Pedido p);           // handoff entrypoint (Pessoa 1 → Pessoa 2's sim)
    void pedidoPronto();                       // internal sim tick — Pessoa 1 MUST NOT call
}
// PROPOSED — Pessoa 2 creates. Mirrors ICozinhaService. Cozinha hands off to this on PRONTO.
public interface IEntregaService {            // Dominio/Servicos/IEntregaService.java (Pessoa 2)
    void pedidoParaEntrega(Pedido p);
    void pedidoEntregue();                     // internal sim tick
}
// PROPOSED — Pessoa 1 OWNS interface + PagamentoFake. Synchronous fake that always succeeds.
public interface IPagamentoService {          // Dominio/Servicos/IPagamentoService.java (Pessoa 1)
    boolean processarPagamento(Pedido pedido);
}
// Seam #2 callback — Pessoa 1 OWNS. ServicoPedido implements it; the sims depend on THIS
// narrow port (not concrete ServicoPedido) so they can persist transitions without a bean cycle.
public interface IRegistradorStatus {         // Dominio/Servicos/IRegistradorStatus.java (Pessoa 1)
    void registrarTransicao(long pedidoId, Pedido.Status novo);
}
```

> **Seam #2 (PRD §13): `ServicoPedido` is the SOLE writer of `historico_status`.** Every transition — including the ones the Cozinha/Entrega sims cause — goes through `ServicoPedido.registrarTransicao(id, novo)`, which stamps the timestamp, `UPDATE`s `pedidos.status`, and `INSERT`s a `historico_status` row. The sims (Pessoa 2) call back via the `IRegistradorStatus` port; they never touch the table. **Bean-cycle avoidance (decision D13):** the pay→kitchen handoff lives in `PagarPedidoUC` (Aplicacao), **not** in `ServicoPedido`, so `ServicoPedido` has no dependency on `ICozinhaService`.

---

## UX Design

### Before
```
(no order endpoints exist)
```

### After — HTTP contract
```
POST /pedidos
  body: { "clienteCpf": "9001", "enderecoEntrega": "Rua X, 10",
          "itens": [ { "produtoId": 1, "quantidade": 1 }, { "produtoId": 3, "quantidade": 2 } ] }
  200 APROVADO  -> { id, status:"APROVADO", valor, desconto, impostos, valorCobrado, itensIndisponiveis:[] }
  200 RECUSADO  -> { id, status:"RECUSADO", valor:0, ..., itensIndisponiveis:["Pizza margherita"] }
  400           -> empty cart / unknown produtoId / unknown clienteCpf (IllegalArgumentException)

GET  /pedidos/{id}/status
  200 -> { id, statusAtual:"PREPARACAO", historico:[ {status:"NOVO",dataHora:"..."},
                                                     {status:"APROVADO",dataHora:"..."}, ... ] }
  404 -> unknown id (RecursoNaoEncontradoException) · 400 -> non-numeric id

POST /pedidos/{id}/cancelar
  200 -> { id, statusAtual:"CANCELADO", historico:[...] }
  400 -> order is not APROVADO (already PAGO/ENTREGUE/CANCELADO) · 404 -> unknown id

POST /pedidos/{id}/pagar
  200 -> { id, statusAtual:"AGUARDANDO", historico:[...] }   // PAGO->AGUARDANDO, then sims advance async
  400 -> order is not APROVADO · 404 -> unknown id
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Submit order | — | `POST /pedidos` | First `@PostMapping` + `@RequestBody` in the repo (D10) |
| Status | — | `GET /pedidos/{id}/status` | Reads `historico_status` (UC7 "trackable from order number") |
| Cancel | — | `POST /pedidos/{id}/cancelar` | Only APROVADO-not-paid (UC8) |
| Pay | — | `POST /pedidos/{id}/pagar` | Fake payment → PAGO → kitchen sim (UC9) |
| Cozinha sim | dead code | `@Service`, persists via callback | **Pessoa 2** annotates/wires (Seam #1/#2) |

---

## Mandatory Reading
| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `Dominio/Entidades/Pedido.java` | all | Status enum (no CANCELADO/RECUSADO), `double` money fields, `setStatus`, ctor — entity to extend (D7) |
| P0 | `Dominio/Servicos/ServicoDesconto.java` · `Desconto/ContextoDesconto.java` | all | `calcularDesconto(double subtotal, ContextoDesconto)` returns the discount **amount**; `ContextoDesconto(int pedidosUltimos20Dias)` record, rejects negatives |
| P0 | `Dominio/Servicos/ServicoImposto.java` | all | `calcularImposto(double valorVenda)` returns the tax **amount** |
| P0 | `Adaptadores/Dados/ConfiguracaoRepositoryJDBC.java` | all | The ONLY JDBC write precedent (`jdbcTemplate.update(sql, ps->{...})`); mirror its `update` idiom (not its k/v helper) |
| P0 | `Adaptadores/Dados/ProdutosRepositoryJDBC.java` | all | RowMapper idiom, nested-port hydration, `isEmpty()?null:getFirst()`, `rs.getInt("preco")` |
| P0 | `Adaptadores/Apresentacao/RestExceptionHandler.java` | all | Exception→HTTP map: IAE→400, ISE→500, RecursoNaoEncontrado→404, type-mismatch→400 |
| P1 | `Dominio/Servicos/CardapioService.java` | all | `@Service` facade; throws `RecursoNaoEncontradoException`/`IllegalArgumentException`/`IllegalStateException` per case |
| P1 | `Adaptadores/Apresentacao/CardapioController.java` · `DescontoController.java` | all | Controller→UC→Response→Presenter (Presenter built in controller); **no `@CrossOrigin`** (CorsConfig is global) |
| P1 | `Dominio/Servicos/CozinhaService.java` · `ICozinhaService.java` | all | Sim template + the handoff contract Pessoa 1 calls |
| P1 | `src/test/.../Dominio/Servicos/ServicoDescontoTest.java` | all | Hand-rolled `FakeXxxRepository implements <port>`, `DELTA=1e-9`, comment-spec header — copy this test culture |
| P1 | `src/main/resources/schema.sql` · `data.sql` | all | Naming conventions, seeded `clientes`/`produtos`(cents)/`itensEstoque`(qty 30) |
| ref | `Aplicacao/RecuperarCardapioCorrenteUC.java` · `Responses/PoliticasDescontoResponse.java` | all | UC `@Component`+`run(...)`; Response = `record` |
| ref | `.claude/PRPs/plans/completed/cardapio-corrente-e-descontos.plan.md` | all | House plan style + P1 decisions D2–D5 this plan continues |

## External Documentation
| Topic | Source | Key Takeaway |
|---|---|---|
| H2 generated keys | Spring `JdbcTemplate#update(PreparedStatementCreator, KeyHolder)` | Retrieve auto-increment id via `GeneratedKeyHolder` + `Statement.RETURN_GENERATED_KEYS` (D9 — first use in repo) |
| H2 identity column | H2 `BIGINT AUTO_INCREMENT` | DDL for the `pedidos`/`historico_status` PKs (no auto-increment precedent in schema) |

> No new dependencies. `spring-boot-starter-jdbc`, H2, JUnit 5 already present (`pom.xml`). No JPA, no validation starter, no Mockito.

---

## Patterns to Mirror (copy these — new code must be indistinguishable)

### SERVICE_FACADE
```java
// SOURCE: Dominio/Servicos/CardapioService.java:14-46
@Service
public class CardapioService {
    private CardapioRepository cardapioRepository;            // field NOT final
    @Autowired public CardapioService(CardapioRepository r){ this.cardapioRepository = r; }
    public Cardapio recuperaCardapio(long id){
        Cardapio c = cardapioRepository.recuperaPorId(id);
        if (c == null) throw new RecursoNaoEncontradoException("Cardapio inexistente: " + id); // 404
        return c;
    }
}
```

### USE_CASE
```java
// SOURCE: Aplicacao/DefinirPoliticaDescontoUC.java  (and RecuperarCardapioCorrenteUC.java)
@Component
public class DefinirPoliticaDescontoUC {
    private ServicoDesconto servicoDesconto;                 // non-final
    @Autowired public DefinirPoliticaDescontoUC(ServicoDesconto s){ this.servicoDesconto = s; }
    public String run(String codigo){ servicoDesconto.definirPolitica(codigo); return codigo; }
}
```
Rules: UCs are `@Component`, explicit `@Autowired` ctor, exactly one public method named `run`, returns a `Response` record (query UCs) or echoes input (mutating UCs).

### CONTROLLER
```java
// SOURCE: Adaptadores/Apresentacao/DescontoController.java
@RestController
@RequestMapping("/descontos")
public class DescontoController {
    private ListarPoliticasDescontoUC listarPoliticasUC;
    private DefinirPoliticaDescontoUC definirPoliticaUC;
    public DescontoController(ListarPoliticasDescontoUC a, DefinirPoliticaDescontoUC b){ this.listarPoliticasUC=a; this.definirPoliticaUC=b; }
    @PutMapping("/corrente/{codigo}")
    public PoliticasDescontoPresenter definirPolitica(@PathVariable(value="codigo") String codigo){
        definirPoliticaUC.run(codigo);                       // mutate (throws IAE->400 on unknown)
        PoliticasDescontoResponse resp = listarPoliticasUC.run();   // re-query
        return new PoliticasDescontoPresenter(resp.politicas(), resp.corrente()); // build Presenter HERE
    }
}
```
Rules: class `@RestController`+`@RequestMapping`, **NO `@CrossOrigin`** (global `CorsConfig` already allows GET/POST/PUT/DELETE/OPTIONS on `/**`), positional ctor injection, mutate-then-re-query-then-return-Presenter, Presenter built in the controller (never returned by a UC/service).

### REPOSITORY_READ
```java
// SOURCE: Adaptadores/Dados/ProdutosRepositoryJDBC.java:48-67
List<Produto> produtos = this.jdbcTemplate.query(
    "SELECT p.id, p.descricao, p.preco, pr.receita_id FROM produtos p " +
    "JOIN produto_receita pr ON p.id = pr.produto_id WHERE p.id = ?",
    ps -> ps.setLong(1, id),
    (rs, rowNum) -> new Produto(rs.getLong("id"), rs.getString("descricao"),
        receitasRepository.recuperaReceita(rs.getLong("receita_id")), rs.getInt("preco")));
return produtos.isEmpty() ? null : produtos.getFirst();
```
Rules: 3-arg `query(sql, PreparedStatementSetter, RowMapper)`, 1-based positional params, inline lambda mapper, `isEmpty()?null:getFirst()`, `rs.getInt("preco")` (cents). `@Repository` stereotype. Adapters inject **ports** (interfaces), not concrete JDBC classes.

### JDBC_WRITE (mirror — the only write precedent)
```java
// SOURCE: Adaptadores/Dados/ConfiguracaoRepositoryJDBC.java:31-37
this.jdbcTemplate.update(sql, ps -> { ps.setString(1, chave); ps.setString(2, valor); });
```
Rules: `jdbcTemplate.update(sqlString, PreparedStatementSetter)` for parameterized writes. (This plan ADDS `INSERT`/`UPDATE` and generated-key retrieval — see N-patterns below.)

### TEST_HELPER_BUILDER (no Mockito — hand-rolled fakes)
```java
// SOURCE: src/test/.../ServicoDescontoTest.java:26-40
class ServicoDescontoTest {
    private static final double DELTA = 1e-9;
    private static class FakeDescontoRepository implements DescontoRepository {
        private String corrente;
        FakeDescontoRepository(String inicial){ this.corrente = inicial; }
        @Override public String politicaCorrente(){ return corrente; }
        @Override public void definePolitica(String c){ this.corrente = c; }
    }
    private ServicoDesconto servicoCom(String pol){
        return new ServicoDesconto(new FabricaEstrategiaDesconto(
            List.of(new SemDesconto(), new FidelidadeFrequente(), new PromocaoVerao())),
            new FakeDescontoRepository(pol));
    }
}
```
Rules: `<Class>Test` package-private, `private static class Fake… implements <port>`, REAL domain-service collaborators (build real `ServicoImposto`/`ServicoDesconto`), `DELTA=1e-9`, `assertThrows(Ex.class, ()->…)`, post-throw "no mutation" assertion, **numbered `/* Casos de teste -- ... */` header comment** (spec requires test cases as comments). NO Spring, NO `@SpringBootTest` (reserved for the one `contextLoads`).

---

## NEW patterns (NO precedent in repo — grep-verified absent; introduce deliberately like P1's N1/N2)

- **N1 — JDBC `INSERT` + generated key (D9).** No auto-increment / `KeyHolder` anywhere. For `pedidos`/`historico_status` use:
```java
KeyHolder keyHolder = new GeneratedKeyHolder();
jdbcTemplate.update(connection -> {
    PreparedStatement ps = connection.prepareStatement(
        "INSERT INTO pedidos (cliente_cpf, status, valor, impostos, desconto, valor_cobrado, data_hora_pagamento, endereco_entrega) " +
        "VALUES (?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
    ps.setString(1, p.getCliente().getCpf());
    ps.setString(2, p.getStatus().name());
    ps.setDouble(3, p.getValor()); ps.setDouble(4, p.getImpostos());
    ps.setDouble(5, p.getDesconto()); ps.setDouble(6, p.getValorCobrado());
    if (p.getDataHoraPagamento() == null) ps.setNull(7, Types.TIMESTAMP);
    else ps.setTimestamp(7, Timestamp.valueOf(p.getDataHoraPagamento()));
    ps.setString(8, p.getEnderecoEntrega());
    return ps;
}, keyHolder);
long novoId = keyHolder.getKey().longValue();
```
- **N2 — JDBC `UPDATE`.** First real `UPDATE` in the repo: `jdbcTemplate.update("UPDATE pedidos SET status = ? WHERE id = ?", ps -> { ps.setString(1, status.name()); ps.setLong(2, id); })`. Same for `itensEstoque` quantity decrement.
- **N3 — `@PostMapping` + `@RequestBody` (D10).** First inbound payload. Request DTOs are `record`s in **`Aplicacao/Requests/`** (inward of the web layer; the controller imports them). Empty/invalid carts → throw `IllegalArgumentException` (→400) from the UC/service.
- **N4 — `Pedido.Status` extension (D7).** Add `CANCELADO`, `RECUSADO` to the enum (Pessoa 1 owns `Pedido`). Status persisted as `varchar` via `.name()`, read via `Pedido.Status.valueOf(...)` (token style, like the `SemDesconto` discount token).
- **N5 — Reuse the existing `@RestControllerAdvice` (P1 N2/D5).** Do NOT add a second advice. Throw `IllegalArgumentException` for client errors (empty cart, unknown id, invalid transition) → 400; `RecursoNaoEncontradoException` for unknown order id → 404. **Do NOT throw `IllegalStateException` for client-correctable errors** — it maps to **500** (see G-EXC).

---

## Files to Change

### CREATE (Pessoa 1)
| # | File | Role |
|---|---|---|
| 1 | `Dominio/Servicos/IPagamentoService.java` | Seam #1 interface (Pessoa 1) |
| 2 | `Dominio/Servicos/Pagamento/PagamentoFake.java` | `@Service` fake, always returns `true` |
| 3 | `Dominio/Servicos/IRegistradorStatus.java` | Seam #2 callback port (impl: `ServicoPedido`) |
| 4 | `Dominio/Servicos/TransicaoStatus.java` | `record (Pedido.Status status, LocalDateTime dataHora)` |
| 5 | `Dominio/Dados/PedidoRepository.java` | Port: persist/read `pedidos` + `itens_pedido` aggregate; loyalty count |
| 6 | `Dominio/Dados/HistoricoStatusRepository.java` | Port: append/read `historico_status` |
| 7 | `Dominio/Dados/ItensEstoqueRepository.java` | Port: read all stock + set quantity |
| 8 | `Adaptadores/Dados/PedidoRepositoryJDBC.java` | `@Repository`, N1/N2 |
| 9 | `Adaptadores/Dados/HistoricoStatusRepositoryJDBC.java` | `@Repository`, N1 |
| 10 | `Adaptadores/Dados/ItensEstoqueRepositoryJDBC.java` | `@Repository`, N2 |
| 11 | `Dominio/Servicos/ServicoEstoque.java` | `@Service`: availability (derived) + decrement |
| 12 | `Dominio/Servicos/ServicoPedido.java` | `@Service` core; `implements IRegistradorStatus`; sole writer of status/history |
| 13 | `Aplicacao/Requests/ItemPedidoRequest.java` | record `(long produtoId, int quantidade)` |
| 14 | `Aplicacao/Requests/SubmeterPedidoRequest.java` | record `(String clienteCpf, String enderecoEntrega, List<ItemPedidoRequest> itens)` |
| 15 | `Aplicacao/Responses/SubmeterPedidoResponse.java` | record |
| 16 | `Aplicacao/Responses/TransicaoStatusResponse.java` | record `(String status, String dataHora)` |
| 17 | `Aplicacao/Responses/PedidoStatusResponse.java` | record `(long id, String statusAtual, List<TransicaoStatusResponse> historico)` |
| 18 | `Aplicacao/SubmeterPedidoParaAprovacaoUC.java` | UC6 (P6 test target — exact name) |
| 19 | `Aplicacao/ConsultarStatusPedidoUC.java` | UC7 |
| 20 | `Aplicacao/CancelarPedidoUC.java` | UC8 |
| 21 | `Aplicacao/PagarPedidoUC.java` | UC9 orchestration (pay→PAGO→AGUARDANDO→Cozinha) |
| 22 | `Adaptadores/Apresentacao/PedidoController.java` | `@RestController @RequestMapping("/pedidos")` |
| 23 | `Adaptadores/Apresentacao/Presenters/SubmeterPedidoPresenter.java` | record |
| 24 | `Adaptadores/Apresentacao/Presenters/PedidoStatusPresenter.java` | record (+ history items) |
| 25 | `src/test/.../Dominio/Servicos/ServicoEstoqueTest.java` | unit |
| 26 | `src/test/.../Dominio/Servicos/ServicoPedidoTest.java` | unit |
| 27 | `src/test/.../Aplicacao/SubmeterPedidoParaAprovacaoUCTest.java` | unit (P6 target) |

### UPDATE
| File | Change |
|---|---|
| `Dominio/Entidades/Pedido.java` | Add `CANCELADO`, `RECUSADO` to `Status` enum (N4); add `String enderecoEntrega` field + getter to ctor; add `void setDataHoraPagamento(LocalDateTime)` |
| `src/main/resources/schema.sql` | Add `pedidos`, `itens_pedido`, `historico_status` tables |
| `src/main/resources/data.sql` | (Optional) no order seed needed; estoque/clientes/produtos already seeded |

> **`Pedido` ctor change is a shared-entity edit but `Pedido` is Pessoa 1's** (PRD §16) — no Seam conflict. Add `enderecoEntrega` as the last ctor param to avoid reordering existing args; the entity is unwired (no current caller).

---

## NOT Building (out of scope for this plan)
- ❌ **Cozinha/Entrega sims** (`CozinhaService` `@Service` wiring, `IEntregaService`/`EntregaService`) — **Pessoa 2** (Seam #1/#2). Pessoa 1 codes to the frozen interfaces; for local end-to-end boot before Pessoa 2 delivers, a *temporary* no-op `ICozinhaService` stub may be used and deleted on integration (note in Task 11).
- ❌ **UC10** `GET /pedidos/entregues?ini=&fim=` — **P4** (Pessoa 1, Seam #4). The query `pedidosEntreguesEntre(...)` is deferred to that phase.
- ❌ **JPA migration** of these repos — **P3** (Seam #3, against Pessoa 2's framework decision).
- ❌ **Authentication / authorization** on `/pedidos/*` — **P5** (Pessoa 2). Actor (`clienteCpf`) is a request field for now.
- ❌ **UC5 cardápio reflecting indisponibilidade** in the menu read — **Pessoa 2**'s `CardapioService` enhancement. This plan only *derives + reports* unfulfillable items at submit (D11).
- ❌ Per-line-item price snapshot column — order total is snapshotted in `pedidos.valor`; line items rehydrate the live `Produto` (drift caveat accepted, JPA phase may revisit).
- ❌ Persisted `entregadores`/courier table — **Pessoa 2** if the Entrega sim needs it.

---

## Cost formula (fixed — implement exactly)

Per **PRD §2**: *"Imposto: currently a single 10% tax over the sum of item costs."* → **imposto base is the subtotal `Σ itens`, NOT `subtotal − desconto`** (decision D8).

```
subtotalCentavos = Σ (produto.getPreco() * item.getQuantidade())      // int cents
subtotal  = subtotalCentavos / 100.0                                  // reais (double)
desconto  = servicoDesconto.calcularDesconto(subtotal, contexto)      // amount, reais
imposto   = servicoImposto.calcularImposto(subtotal)                  // amount over SUBTOTAL (D8)
custoFinal = (subtotal - desconto) + imposto
```
Round each persisted money value to cents with `arredonda(v) = Math.round(v * 100) / 100.0` (decision D6). Persist `valor=subtotal`, `desconto`, `impostos=imposto`, `valorCobrado=custoFinal`, all `double` reais.

**Worked example (seed data, default policy `SemDesconto`, lei `0412/2022`=10%):**
order = 1× Pizza calabresa (5500¢) + 2× Pizza margherita (4000¢):
`subtotal = (5500 + 2*4000)/100 = 135.00` · `desconto = 0.00` · `imposto = 135.00*0.10 = 13.50` · `valorCobrado = (135.00 - 0) + 13.50 = 148.50`.
Same cart under `PromocaoVerao` (5%): `desconto = 6.75`, `imposto = 13.50`, `valorCobrado = (135 - 6.75) + 13.50 = 141.75`.

**`contexto` (D12):** `pedidosUltimos20Dias` is now **derived server-side** — `PedidoRepository.contarPedidosPagosCliente(cpf, agora.minusDays(20))` — superseding P1's caller-supplied stopgap (resolves OQ#4 tail). `FidelidadeFrequente` gives 7% only when count `> 3` (strict). Count is always ≥ 0 (satisfies `ContextoDesconto`'s non-negative invariant).

---

## Status lifecycle (this plan)
```
            UC6 submit
NOVO --estoque ok--> APROVADO --UC9 pay (fake ok)--> PAGO --> AGUARDANDO --+ (handoff to Cozinha sim)
  |                     |                                                   |  Pessoa 2 sims drive:
  +-estoque insuf.->RECUSADO   UC8 cancel (only here)-> CANCELADO           +--> PREPARACAO -> PRONTO
                                                                               -> TRANSPORTE -> ENTREGUE
```
- **Pessoa 1 owns:** NOVO, APROVADO, RECUSADO, CANCELADO, PAGO, AGUARDANDO (and the single-writer persistence of ALL transitions, including the sims').
- **Pessoa 2's sims cause** PREPARACAO/PRONTO (Cozinha) and TRANSPORTE/ENTREGUE (Entrega) but persist them **only** by calling `IRegistradorStatus.registrarTransicao(...)` (Seam #2).

---

## Step-by-Step Tasks

### Task 1 — Schema: `pedidos`, `itens_pedido`, `historico_status`
- **ACTION:** Append to `src/main/resources/schema.sql`.
- **IMPLEMENT:**
```sql
create table if not exists pedidos (
  id bigint auto_increment primary key,
  cliente_cpf varchar(15) not null,
  status varchar(20) not null,
  valor double not null,
  impostos double not null,
  desconto double not null,
  valor_cobrado double not null,
  data_hora_pagamento timestamp,
  endereco_entrega varchar(255) not null,
  foreign key (cliente_cpf) references clientes(cpf)
);
create table if not exists itens_pedido (
  pedido_id bigint not null,
  produto_id bigint not null,
  quantidade int not null,
  primary key (pedido_id, produto_id),
  foreign key (pedido_id) references pedidos(id),
  foreign key (produto_id) references produtos(id)
);
create table if not exists historico_status (
  id bigint auto_increment primary key,
  pedido_id bigint not null,
  status varchar(20) not null,
  data_hora timestamp not null,
  foreign key (pedido_id) references pedidos(id)
);
```
- **MIRROR:** existing DDL (plural lowercase entity table `pedidos`; snake_case child/junction `itens_pedido`, `historico_status`).
- **GOTCHA [D6/D9]:** money columns are `double` (match `Pedido`'s `double` fields), **not** `bigint` cents — `produtos.preco` stays cents but the order aggregate is double-reais. PKs are `auto_increment` (first in repo). H2 folds unquoted identifiers to upper-case — keep everything unquoted (consistent with the existing `itensEstoque` outlier).
- **VALIDATE:** `./mvnw spring-boot:run` boots; in `/h2` console, `SELECT * FROM pedidos;` returns an empty table (no PK errors).

### Task 2 — Extend `Pedido` entity (Status + endereço + payment-time setter)
- **ACTION:** Edit `Dominio/Entidades/Pedido.java`.
- **IMPLEMENT:** (a) enum → `NOVO, APROVADO, PAGO, AGUARDANDO, PREPARACAO, PRONTO, TRANSPORTE, ENTREGUE, CANCELADO, RECUSADO`. (b) add `private String enderecoEntrega;` as the **last** ctor parameter + `public String getEnderecoEntrega()`. (c) add `public void setDataHoraPagamento(LocalDateTime d){ this.dataHoraPagamento = d; }`.
- **MIRROR [N4]:** pure POJO, no framework annotations; `setStatus`-style mutator.
- **GOTCHA:** `Pedido` is unwired today (no existing caller) so the ctor signature change is safe; keep `enderecoEntrega` last to minimize churn. Do NOT add JPA/Lombok.
- **VALIDATE:** `./mvnw -q compile` succeeds.

### Task 3 — Seam #1/#2 interfaces + `TransicaoStatus` value
- **ACTION:** Create `Dominio/Servicos/IPagamentoService.java`, `Dominio/Servicos/IRegistradorStatus.java` (exactly as frozen above), and `Dominio/Servicos/TransicaoStatus.java` = `public record TransicaoStatus(Pedido.Status status, java.time.LocalDateTime dataHora) {}`.
- **MIRROR:** `ICozinhaService.java` (interface in `Dominio.Servicos`, no annotations); `CabecalhoCardapio` (record value).
- **GOTCHA:** keep `IRegistradorStatus` narrow (one method) so the sims depend on it, not concrete `ServicoPedido` (Seam #2, no bean cycle).
- **VALIDATE:** compiles.

### Task 4 — `PagamentoFake` (`@Service`)
- **ACTION:** Create `Dominio/Servicos/Pagamento/PagamentoFake.java`.
- **IMPLEMENT:**
```java
@Service
public class PagamentoFake implements IPagamentoService {
    @Override public boolean processarPagamento(Pedido pedido){ return true; } // spec: always "paid"
}
```
- **MIRROR:** strategy-class `@Service` placement under a service subpackage (`Imposto/`, `Desconto/` → `Pagamento/`).
- **VALIDATE:** Spring finds exactly one `IPagamentoService` bean.

### Task 5 — Ports: `PedidoRepository`, `HistoricoStatusRepository`, `ItensEstoqueRepository`
- **ACTION:** Create the three interfaces in `Dominio/Dados/`.
- **IMPLEMENT:**
```java
public interface PedidoRepository {
    long salvar(Pedido pedido);                       // INSERT pedidos + itens_pedido; returns generated id
    Pedido recuperaPorId(long id);                    // header + itens (rehydrate Produto); null if absent
    void atualizaStatus(long id, Pedido.Status novo); // UPDATE pedidos.status
    void atualizaDataHoraPagamento(long id, java.time.LocalDateTime quando);
    int contarPedidosPagosCliente(String cpf, java.time.LocalDateTime desde); // loyalty count (D12)
}
public interface HistoricoStatusRepository {
    void registrar(long pedidoId, Pedido.Status status, java.time.LocalDateTime quando);
    java.util.List<TransicaoStatus> historico(long pedidoId); // ordered by data_hora asc
}
public interface ItensEstoqueRepository {
    java.util.List<ItemEstoque> recuperaTodos();
    void defineQuantidade(long ingredienteId, int novaQuantidade); // UPDATE itensEstoque
}
```
- **NOTE [D14]:** `itens_pedido` is folded into `PedidoRepository` (aggregate root persists its items — like `CardapioRepositoryJDBC` composes the graph), consolidating PRD §16's "ItensPedidoRepository" — line items have no independent identity.
- **MIRROR:** `Dominio.Dados` ports — Portuguese names, no Spring.
- **VALIDATE:** compiles.

### Task 6 — `ItensEstoqueRepositoryJDBC`
- **ACTION:** Create `Adaptadores/Dados/ItensEstoqueRepositoryJDBC.java` (`@Repository`). Inject `JdbcTemplate` + `IngredientesRepository` (port).
- **IMPLEMENT:** `recuperaTodos()` → `SELECT e.ingrediente_id, e.quantidade, i.descricao FROM itensEstoque e JOIN ingredientes i ON e.ingrediente_id = i.id` mapped to `new ItemEstoque(new Ingrediente(rs.getLong("ingrediente_id"), rs.getString("descricao")), rs.getInt("quantidade"))`. `defineQuantidade` → `jdbcTemplate.update("UPDATE itensEstoque SET quantidade = ? WHERE ingrediente_id = ?", ps -> { ps.setInt(1, q); ps.setLong(2, ingredienteId); })` (N2).
- **IMPORTS:** `org.springframework.stereotype.Repository`, `org.springframework.jdbc.core.JdbcTemplate`, `org.springframework.beans.factory.annotation.Autowired`.
- **MIRROR [REPOSITORY_READ / JDBC_WRITE / N2]:** table name is the camelCase outlier `itensEstoque` (unquoted → H2 folds to `ITENSESTOQUE`).
- **VALIDATE:** runtime: after `defineQuantidade(1, 5)`, H2 console shows ingrediente 1 at qty 5.

### Task 7 — `HistoricoStatusRepositoryJDBC`
- **ACTION:** Create `Adaptadores/Dados/HistoricoStatusRepositoryJDBC.java` (`@Repository`).
- **IMPLEMENT:** `registrar` → `INSERT INTO historico_status (pedido_id, status, data_hora) VALUES (?,?,?)` via `jdbcTemplate.update(sql, ps -> { ps.setLong(1,id); ps.setString(2,status.name()); ps.setTimestamp(3, Timestamp.valueOf(quando)); })`. `historico` → `SELECT status, data_hora FROM historico_status WHERE pedido_id = ? ORDER BY data_hora ASC, id ASC` mapped to `new TransicaoStatus(Pedido.Status.valueOf(rs.getString("status")), rs.getTimestamp("data_hora").toLocalDateTime())`.
- **GOTCHA:** order by `id` as tie-breaker so same-millisecond transitions stay in insertion order (determinism, mirror P1 fix-C).
- **VALIDATE:** covered by ServicoPedido test + UC7 smoke.

### Task 8 — `PedidoRepositoryJDBC`
- **ACTION:** Create `Adaptadores/Dados/PedidoRepositoryJDBC.java` (`@Repository`). Inject `JdbcTemplate` + `ProdutosRepository` (port, to rehydrate line items).
- **IMPLEMENT:** `salvar` = N1 generated-key insert of the header, then loop `INSERT INTO itens_pedido (pedido_id, produto_id, quantidade) VALUES (?,?,?)` per item; return the id. `recuperaPorId` = header read (`isEmpty()?null:getFirst()`), then `SELECT produto_id, quantidade FROM itens_pedido WHERE pedido_id = ?` → for each, `new ItemPedido(produtosRepository.recuperaProdutoPorid(produto_id), quantidade)`; reconstruct `Pedido` (read `endereco_entrega`; `data_hora_pagamento` may be null → null-safe `getTimestamp`). `atualizaStatus`/`atualizaDataHoraPagamento` = N2 `UPDATE`. `contarPedidosPagosCliente` = `SELECT COUNT(*) FROM pedidos WHERE cliente_cpf = ? AND data_hora_pagamento IS NOT NULL AND data_hora_pagamento >= ?` via `jdbcTemplate.queryForObject(sql, Integer.class, cpf, Timestamp.valueOf(desde))`.
- **IMPORTS:** `java.sql.{PreparedStatement,Statement,Timestamp,Types}`, `org.springframework.jdbc.support.{GeneratedKeyHolder,KeyHolder}`.
- **MIRROR [N1/N2 + REPOSITORY_READ]:** inject the `ProdutosRepository` **port** (not the JDBC class), like `ProdutosRepositoryJDBC` injects `ReceitasRepository`.
- **GOTCHA [D6]:** money via `ps.setDouble`/`rs.getDouble`. Null payment time → `ps.setNull(.., Types.TIMESTAMP)` on insert and `rs.getTimestamp(...)` null-check on read.
- **VALIDATE:** ServicoPedido test (fakes) + live: `POST /pedidos` then `SELECT * FROM pedidos` shows a row with a generated id.

### Task 9 — `ServicoEstoque` (`@Service`)
- **ACTION:** Create `Dominio/Servicos/ServicoEstoque.java`. Inject `ItensEstoqueRepository`.
- **IMPLEMENT:**
  - `List<Produto> itensIndisponiveis(List<ItemPedido> itens)`: build `Map<Long,Integer> requerido` = `Σ` over items of `item.getQuantidade()` for each `Ingrediente` (by `getId()`) in `item.getItem().getReceita().getIngredientes()` (1 unit of each listed ingredient per pizza — see D11/G-STOCK). Build `Map<Long,Integer> estoque` from `recuperaTodos()` keyed by ingrediente id. A produto is unfulfillable if ANY of its ingredients' required total exceeds available stock. Return the distinct list of such `Produto`s (sorted by id — determinism).
  - `boolean haDisponibilidade(List<ItemPedido> itens)` = `itensIndisponiveis(itens).isEmpty()`.
  - `void baixaEstoque(List<ItemPedido> itens)`: for each ingrediente, `defineQuantidade(id, atual - requerido)`. Call ONLY after availability confirmed.
- **MIRROR [SERVICE_FACADE]:** `@Service`, ctor-injected repo, Portuguese names.
- **GOTCHA [G-STOCK]:** `Receita.getIngredientes()` carries **no per-recipe quantity** — model 1 unit per listed ingredient (document D11). Match `Ingrediente`↔`ItemEstoque` by `getId()` (no `equals` override).
- **VALIDATE:** `ServicoEstoqueTest` — with seeded qty 30, a 1-item order is available; an order needing >30 of one ingredient is unfulfillable; `baixaEstoque` decrements.

### Task 10 — `ServicoPedido` (`@Service`, `implements IRegistradorStatus`) — CORE
- **ACTION:** Create `Dominio/Servicos/ServicoPedido.java`. Inject `PedidoRepository`, `HistoricoStatusRepository`, `ServicoEstoque`, `ServicoImposto`, `ServicoDesconto`.
- **IMPLEMENT:**
  - `private static double arredonda(double v){ return Math.round(v * 100) / 100.0; }`
  - `Pedido submeter(Cliente cliente, String enderecoEntrega, List<ItemPedido> itens)`:
    1. if `itens` null/empty → `throw new IllegalArgumentException("Pedido sem itens")` (→400).
    2. compute `subtotal` (cents→reais, D6). Build `ContextoDesconto` from `pedidoRepository.contarPedidosPagosCliente(cliente.getCpf(), LocalDateTime.now().minusDays(20))`.
    3. `List<Produto> indisp = servicoEstoque.itensIndisponiveis(itens)`.
    4. **denied path:** if `!indisp.isEmpty()` → build `Pedido` status `NOVO`, money zeros, `salvar`, `registrarTransicao(id, RECUSADO)`, return the persisted RECUSADO `Pedido` (UC computes the unfulfillable descriptions separately via `servicoEstoque.itensIndisponiveis`). Do NOT decrement stock.
    5. **approved path:** `desconto=arredonda(servicoDesconto.calcularDesconto(subtotal, ctx))`, `imposto=arredonda(servicoImposto.calcularImposto(subtotal))` (D8), `custoFinal=arredonda((subtotal-desconto)+imposto)`. Build `Pedido(0, cliente, null, itens, NOVO, arredonda(subtotal), imposto, desconto, custoFinal, enderecoEntrega)`; `id=pedidoRepository.salvar(pedido)`; `registrarTransicao(id, NOVO)` (creation stamp) then `registrarTransicao(id, APROVADO)`; `servicoEstoque.baixaEstoque(itens)`; return `recuperaPorId(id)`.
  - `Pedido recuperaPorId(long id)`: `p=pedidoRepository.recuperaPorId(id); if(p==null) throw new RecursoNaoEncontradoException("Pedido inexistente: "+id);` return p.
  - `List<TransicaoStatus> historico(long id)`: `recuperaPorId(id)` (404 guard) then `historicoStatusRepository.historico(id)`.
  - `void cancelar(long id)`: `p=recuperaPorId(id); if(p.getStatus()!=Pedido.Status.APROVADO) throw new IllegalArgumentException("Somente pedido APROVADO e nao pago pode ser cancelado; status atual: "+p.getStatus());` then `registrarTransicao(id, CANCELADO)`.
  - **`@Override public void registrarTransicao(long id, Pedido.Status novo)`** (Seam #2 single writer): `LocalDateTime agora = LocalDateTime.now(); pedidoRepository.atualizaStatus(id, novo); if(novo==Pedido.Status.PAGO){ pedidoRepository.atualizaDataHoraPagamento(id, agora); } historicoStatusRepository.registrar(id, novo, agora);`
- **MIRROR [SERVICE_FACADE / TEST_HELPER_BUILDER]:** thin, Portuguese, real `ServicoImposto`/`ServicoDesconto` collaborators.
- **GOTCHA [N5/G-EXC]:** client errors use `IllegalArgumentException` (→400), unknown id `RecursoNaoEncontradoException` (→404). Never `IllegalStateException` for cancel/pay validation (→500). `registrarTransicao` is the ONLY method writing `historico_status` (Seam #2) — sims call it via `IRegistradorStatus`.
- **VALIDATE:** `ServicoPedidoTest` (fakes) covers submit-approved totals (148.50 / 141.75), submit-denied (RECUSADO, no stock decrement), cancel-guard, and that every transition appends history.

### Task 11 — Use cases (UC6–UC9)
- **ACTION:** Create the four UCs in `Aplicacao/` + the request/response records (Tasks-13..17 files). Inject ports/services per UC.
- **IMPLEMENT:**
  - `SubmeterPedidoParaAprovacaoUC.run(SubmeterPedidoRequest req)`: inject `ServicoPedido`, `ServicoEstoque`, `ProdutosRepository`. Build `Cliente` from `req.clienteCpf()` (a `Cliente` with only the cpf populated suffices for P2 — only `getCpf()` is read; null other fields are acceptable until UC11/auth). Map `req.itens()` → `List<ItemPedido>` via `produtosRepository.recuperaProdutoPorid(produtoId)` (null → `IllegalArgumentException("Produto inexistente: "+id)` →400). `Pedido p = servicoPedido.submeter(cliente, req.enderecoEntrega(), itens)`. If `p.getStatus()==RECUSADO` compute `List<String> indisp = servicoEstoque.itensIndisponiveis(itens).stream().map(Produto::getDescricao).sorted().toList()` else `List.of()`. Return `new SubmeterPedidoResponse(p.getId(), p.getStatus().name(), p.getValor(), p.getDesconto(), p.getImpostos(), p.getValorCobrado(), indisp)`.
  - `ConsultarStatusPedidoUC.run(long id)`: `Pedido p = servicoPedido.recuperaPorId(id); var h = servicoPedido.historico(id);` → `new PedidoStatusResponse(id, p.getStatus().name(), h.stream().map(t -> new TransicaoStatusResponse(t.status().name(), t.dataHora().toString())).toList())`.
  - `CancelarPedidoUC.run(long id)`: `servicoPedido.cancelar(id);` then build a `PedidoStatusResponse` (re-query via the same mapping as UC7).
  - `PagarPedidoUC.run(long id)` (orchestration, D13): inject `ServicoPedido`, `IPagamentoService`, `ICozinhaService`. `Pedido p = servicoPedido.recuperaPorId(id); if(p.getStatus()!=Pedido.Status.APROVADO) throw new IllegalArgumentException("Pedido nao esta apto a pagamento; status: "+p.getStatus());` `if(!pagamentoService.processarPagamento(p)) throw new IllegalStateException("Falha no pagamento");` `servicoPedido.registrarTransicao(id, Pedido.Status.PAGO); servicoPedido.registrarTransicao(id, Pedido.Status.AGUARDANDO);` `cozinhaService.chegadaDePedido(servicoPedido.recuperaPorId(id));` return the re-queried `PedidoStatusResponse`.
- **MIRROR [USE_CASE / N3]:** `@Component`, single `run`, Response records in `Aplicacao/Responses/`, Request records in `Aplicacao/Requests/`.
- **GOTCHA [Seam #1]:** `PagarPedidoUC` injects `ICozinhaService` (interface). At runtime this needs Pessoa 2's `@Service CozinhaService`. If integrating before Pessoa 2 is ready, add a temporary `@Service` no-op `ICozinhaService` stub (delete on integration) so the context loads — note it in the Progress Log.
- **VALIDATE:** `SubmeterPedidoParaAprovacaoUCTest` green; live smoke for all four endpoints.

### Task 12 — `PedidoController` + Presenters
- **ACTION:** Create `Adaptadores/Apresentacao/PedidoController.java` (`@RestController @RequestMapping("/pedidos")`) + the two Presenter records (`SubmeterPedidoPresenter`, `PedidoStatusPresenter` with a nested/parallel item record for history).
- **IMPLEMENT:**
```java
@PostMapping("")
public SubmeterPedidoPresenter submeter(@RequestBody SubmeterPedidoRequest req){
    SubmeterPedidoResponse r = submeterPedidoUC.run(req);
    return new SubmeterPedidoPresenter(r.id(), r.status(), r.valor(), r.desconto(), r.impostos(), r.valorCobrado(), r.itensIndisponiveis());
}
@GetMapping("/{id}/status")
public PedidoStatusPresenter status(@PathVariable(value="id") long id){ /* map ConsultarStatusPedidoUC.run(id) */ }
@PostMapping("/{id}/cancelar")
public PedidoStatusPresenter cancelar(@PathVariable(value="id") long id){ /* CancelarPedidoUC.run(id) */ }
@PostMapping("/{id}/pagar")
public PedidoStatusPresenter pagar(@PathVariable(value="id") long id){ /* PagarPedidoUC.run(id) */ }
```
- **MIRROR [CONTROLLER]:** positional ctor injection of the 4 UCs; build Presenter in the controller; **NO `@CrossOrigin`** (CorsConfig global). Presenters are `record`s.
- **GOTCHA [N3/N5]:** first `@PostMapping`+`@RequestBody` — import `org.springframework.web.bind.annotation.{PostMapping,RequestBody,GetMapping,PathVariable,RequestMapping,RestController}`. Errors propagate to `RestExceptionHandler` (no try/catch).
- **VALIDATE:** live curl sequence (see Validation Commands).

### Task 13 — Tests (unit, P6 targets, comment-spec headers)
- **ACTION:** Create `ServicoEstoqueTest`, `ServicoPedidoTest`, `SubmeterPedidoParaAprovacaoUCTest` under `src/test/.../`.
- **IMPLEMENT [TEST_HELPER_BUILDER]:** hand-rolled `FakePedidoRepository`, `FakeHistoricoStatusRepository`, `FakeItensEstoqueRepository`, `FakeProdutosRepository` implementing the ports; REAL `ServicoImposto`/`ServicoDesconto` (built with `FakeDescontoRepository` + real strategy lists + real `ImpostoProperties`). `DELTA=1e-9`. Each file opens with a numbered `/* Casos de teste -- ... */` block (spec requirement). Cover: approved totals (148.50 default, 141.75 PromocaoVerao), Fidelidade7 boundary (count 3→0%, 4→7%), denied/RECUSADO with no stock decrement, cancel-guard rejects non-APROVADO (assert status unchanged), history appended per transition.
- **GOTCHA:** NO Mockito; NO `@SpringBootTest`; package-private classes/methods; Portuguese test names.
- **VALIDATE:** `./mvnw -q test` green; new methods raise the suite count.

---

## Testing Strategy

### Unit tests
| Test | Input | Expected | Edge? |
|---|---|---|---|
| `submeterAprovadoCalculaTotais` | 1×calabresa+2×margherita, SemDesconto | valor 135.00, desconto 0, imposto 13.50, cobrado 148.50, APROVADO | — |
| `submeterComPromocaoVerao` | same, PromocaoVerao | desconto 6.75, cobrado 141.75 | — |
| `fidelidadeBoundary` | count 3 vs 4 (Fidelidade7) | 3→desconto 0; 4→7% | ✅ boundary `>3` |
| `submeterSemEstoqueRecusa` | item needing >30 of an ingredient | status RECUSADO, itensIndisponiveis≠[], stock unchanged | ✅ |
| `submeterCarrinhoVazio` | `itens=[]` | `IllegalArgumentException` (→400) | ✅ empty |
| `submeterProdutoInexistente` | produtoId 999 | `IllegalArgumentException` | ✅ |
| `cancelarApenasAprovado` | cancel a PAGO order | `IllegalArgumentException`; status still PAGO | ✅ invalid transition |
| `cancelarPedidoInexistente` | id 999 | `RecursoNaoEncontradoException` (→404) | ✅ |
| `historicoRegistraCadaTransicao` | submit→cancel | history = [NOVO, APROVADO, CANCELADO] in order | — |
| `estoqueBaixaAposAprovacao` | approve order | each ingredient decremented by qty | — |

### Edge Cases Checklist
- [x] Empty cart → 400
- [x] Unknown produtoId / clienteCpf → 400
- [x] Unknown order id → 404
- [x] Insufficient stock → RECUSADO, no decrement
- [x] Cancel non-APROVADO → 400, no mutation
- [x] Pay non-APROVADO → 400
- [x] Fidelidade7 strict `>3` boundary
- [x] Money rounding to cents (`arredonda`)
- [x] Non-numeric path id → 400 (`MethodArgumentTypeMismatchException`)

---

## Validation Commands

### Static / build
```bash
JAVA_HOME=/home/thiago/Documents/curseforge/minecraft/Install/runtime/java-runtime-delta/linux/java-runtime-delta \
  ./mvnw -q compile        # EXPECT: BUILD SUCCESS (JDK 21 required — List.getFirst())
```

### Unit + integration
```bash
JAVA_HOME=<jdk21> ./mvnw -q test   # EXPECT: all green, suite count up; contextLoads still passes
```
> Pre-req: a bean implementing `ICozinhaService` must exist at context load (Pessoa 2's `@Service CozinhaService`, or the temporary stub from Task 11) or `PagarPedidoUC` injection fails. Unit tests themselves use fakes and are unaffected.

### Boot + smoke (needs network — drop `-o`)
```bash
JAVA_HOME=<jdk21> ./mvnw spring-boot:run    # EXPECT: starts on :8080, schema+data load
# UC6 approved:
curl -s -X POST localhost:8080/pedidos -H 'Content-Type: application/json' \
  -d '{"clienteCpf":"9001","enderecoEntrega":"Rua X, 10","itens":[{"produtoId":1,"quantidade":1},{"produtoId":3,"quantidade":2}]}'
#   EXPECT: {"id":1,"status":"APROVADO","valor":135.0,"desconto":0.0,"impostos":13.5,"valorCobrado":148.5,"itensIndisponiveis":[]}
curl -s localhost:8080/pedidos/1/status      # EXPECT: statusAtual APROVADO, historico [NOVO, APROVADO]
curl -s -X POST localhost:8080/pedidos/1/cancelar   # EXPECT: CANCELADO
curl -s -X POST localhost:8080/pedidos/1/pagar       # EXPECT: 400 (already cancelled)
# fresh order then pay:
curl -s -X POST localhost:8080/pedidos -H 'Content-Type: application/json' \
  -d '{"clienteCpf":"9001","enderecoEntrega":"Rua X, 10","itens":[{"produtoId":1,"quantidade":1}]}'
curl -s -X POST localhost:8080/pedidos/2/pagar       # EXPECT: AGUARDANDO; ~6s later /status shows PRONTO (if Pessoa 2's sim is wired)
curl -s localhost:8080/pedidos/999/status            # EXPECT: 404
```

### DB
```bash
# H2 console http://localhost:8080/h2  (jdbc:h2:mem:pizzadb, sa, no password)
# SELECT * FROM pedidos; SELECT * FROM historico_status ORDER BY data_hora;
# After an approved order: SELECT * FROM itensEstoque;  -- decremented quantities
```

---

## Acceptance Criteria
- [ ] `POST /pedidos` returns APROVADO with the correct `(Σ itens − desconto) + imposto` total, or RECUSADO highlighting unfulfillable items (no stock decrement on denial).
- [ ] Approved orders decrement `itensEstoque`.
- [ ] `GET /pedidos/{id}/status` returns current status + full timestamped history; unknown id → 404.
- [ ] `POST /pedidos/{id}/cancelar` cancels only APROVADO-not-paid; otherwise 400.
- [ ] `POST /pedidos/{id}/pagar` (fake-approved) → PAGO (payment time stamped) → AGUARDANDO → handed to `ICozinhaService`; every transition timestamped in `historico_status` **via `ServicoPedido` only** (Seam #2).
- [ ] Existing menu/discount/tax behaviour unchanged; `./mvnw test` green.

## Completion Checklist
- [ ] Patterns mirrored (SERVICE_FACADE, USE_CASE, CONTROLLER, REPOSITORY_READ, JDBC_WRITE, TEST_HELPER_BUILDER).
- [ ] N1–N5 introduced deliberately; no second `@RestControllerAdvice`.
- [ ] No `@CrossOrigin` added (CorsConfig global).
- [ ] No Mockito; tests carry the numbered comment-spec header (spec).
- [ ] Money rounded via `arredonda`; imposto base = subtotal (D8); no 100× unit bug.
- [ ] Seam #1 signatures match the frozen contracts; Seam #2 single-writer respected.
- [ ] PRD updated: §7 UC6–UC9 status, §8 services, §9 P2 → in-progress + plan link, §13 decisions D6–D14, §14 Progress Log.

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Pessoa 2's `ICozinhaService` bean absent → context fails to load `PagarPedidoUC` | Med | High (no boot) | Freeze Seam #1 first; temporary no-op stub (Task 11) until integration |
| Money unit confusion (cents vs reais) → 100× error | Med | High | D6: explicit cents→reais at one place, `arredonda` at the boundary, worked-example tests |
| `IllegalStateException` used for cancel/pay validation → 500 instead of 400 | Med | Med | G-EXC: use `IllegalArgumentException` for client-correctable invalid transitions |
| Bean cycle `ServicoPedido ↔ ICozinhaService` | Low | Med | D13: handoff lives in `PagarPedidoUC`; sims use narrow `IRegistradorStatus` |
| Generated-key insert (no precedent) wrong | Low | Med | N1 exact code; H2 `auto_increment` + `GeneratedKeyHolder` |
| Stock model (no per-recipe qty) misjudged | Med | Low | D11/G-STOCK: 1 unit per listed ingredient; flag for team ratification |

## Notes — Decisions made by this plan (record into PRD §13 as D6–D14)
- **D6 (money/rounding):** order aggregate is `double` reais; convert `produto.preco` (int cents) → reais via `/100.0`; round every persisted money value with `arredonda(v)=Math.round(v*100)/100.0`; `pedidos` money columns are `double`. *Rationale:* matches `Pedido`'s existing `double` fields, avoids the 100× conversion bug the P1 report flagged. *Alternative (bigint cents, DB-consistent with `produtos`) deferred to JPA phase.* **[Team ratify.]**
- **D7:** add `CANCELADO` + `RECUSADO` to `Pedido.Status` (Pessoa 1 owns `Pedido`).
- **D8:** imposto base = `Σ itens` (subtotal), per PRD §2 "10% over the sum of item costs" — not `subtotal − desconto`. `custoFinal = (subtotal − desconto) + imposto`.
- **D9:** order/history ids via H2 `auto_increment` + `GeneratedKeyHolder` (first generated-key use).
- **D10:** first `@PostMapping`/`@RequestBody`; request DTO records in `Aplicacao/Requests/`. First `INSERT`/`UPDATE`.
- **D11 (indisponibilidade):** availability is **derived from stock** (no persisted flag, no `produtos` schema change) — a produto is indisponível iff a receita ingredient is short; UC6 returns the unfulfillable list; replenishment auto-restores availability. Avoids touching Pessoa 2's `produtos`/`CardapioService`. *Alternative (explicit flag) deferred.* **[Team ratify.]**
- **D12:** `pedidosUltimos20Dias` derived server-side from `PedidoRepository.contarPedidosPagosCliente(cpf, now−20d)` — supersedes P1's caller-supplied stopgap (resolves OQ#4 tail).
- **D13 (Seam #2 cycle-avoidance):** pay→kitchen orchestration lives in `PagarPedidoUC` (Aplicacao), not `ServicoPedido`; `ServicoPedido implements IRegistradorStatus` and the sims depend on that narrow port.
- **D14 (aggregate cohesion):** `itens_pedido` persistence folded into `PedidoRepository` (aggregate root) rather than a separate `ItensPedidoRepository` (PRD §16) — line items have no independent identity.

> Confidence: 8/10 for single-pass implementation of Pessoa 1's slice. The −2 is the Seam #1 runtime dependency on Pessoa 2's Cozinha bean (mitigated by the temporary stub) and two team-ratifiable modelling calls (D6 money unit, D11 derived availability) — both have clear, documented defaults that don't block coding.
