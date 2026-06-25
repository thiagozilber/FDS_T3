# PRD — Backend do Sistema de uma Tele Pizza (Trabalho Final FDS)

> **Living document.** This is the single source of truth for the project. Future
> sessions (human or agent) **read this first**, implement the next pending phase,
> then **update the status tables and the Progress Log at the bottom** before
> finishing. Do not duplicate code structure that the repo already records — capture
> here only what is non-obvious: requirements, conventions, decisions, and progress.

- **Source spec:** `TF_2026_1_Pizzaria.pdf` (Prof. Bernardo Copstein), at the `t3/` workspace root (one level above the project).
- **Project root:** `ex5-pizzaria-clean-baseT1/`
- **Base Java package:** `com.bcopstein.ex4_lancheriaddd_v1`
- **Created:** 2026-06-08 · **Last updated:** 2026-06-22
- **Complexity:** XL (12 use cases, 8 domain services, JPA migration, authentication) — delivered in phases, not a single pass.

---

## 1. How future sessions use this document

1. **Read sections 2–8** to load the system, the tech stack, the current state, and the **patterns to mirror**. The patterns section contains real snippets copied from the codebase — new code must be indistinguishable from existing code.
2. **Find the next pending phase** in **§9 Implementation Phases** (status `pending`, dependencies satisfied). Today's date drives which milestone is next per the cronograma. **§9 now carries an Owner column; the full two-person split and the cross-phase seams are in §16 — read it before claiming work.**
3. **Implement** following the per-phase task breakdown (§10) and the patterns (§6).
4. **Update**: flip the phase status (`pending → in-progress → complete`), tick UC/service status tables (§7/§8), append a dated entry to the **Progress Log (§14)**, and record any new decisions (§13).
5. **Verify** with the validation commands (§12) before marking anything complete.

**Status legend:** ✅ complete · 🟡 partial / scaffolded · 🔜 next up · ⬜ not started

> **Ownership labels:** phases are `P0–P7`; people are **`Pessoa 1`** and **`Pessoa 2`** (never abbreviated to `P1/P2` — that means *phases*). See §16.

---

## 2. The system (from the spec)

An online pizzeria runs an automated order-management backend. A customer uses a phone app: registers (nome, cpf, celular, endereço, email, senha — **username is always the email**), logs in, browses the current cardápio (menu items with description + unit price), and builds an order (any number of menu items, each with a quantity) plus a delivery address.

**Order lifecycle (status machine):**

```
NOVO ──(estoque ok)──► APROVADO ──(pagamento)──► PAGO
  │                        │                        │
  │(falta ingrediente)     │(cancelar, se não pago) ▼
  ▼                        ▼                     AGUARDANDO ► PREPARACAO ► PRONTO
negado/itens               cancelado                                        │
indisponíveis                                                               ▼
                                                          TRANSPORTE ◄─── (fila entrega)
                                                               │
                                                               ▼
                                                           ENTREGUE ──► arquivado p/ cliente
```

- On submit → status **NOVO**. System knows each item's required ingredients; checks **estoque**. If insufficient → order returned highlighting unfulfillable items, and those cardápio items are marked **indisponível** (until stock arrives).
- If ok → status **APROVADO**, cost calculated.
- **Cost formula:** `custoFinal = (Σ custo dos itens − desconto) + imposto`.
  - **Imposto:** currently a single 10% tax over the sum of item costs.
  - **Desconto:** customers with **more than 3 orders in the last 20 days** get **7% off each item**.
- APROVADO order returned to customer → customer **pays** or **cancels**. On payment → **PAGO** (no longer cancelable), order number returned, sent to kitchen queue.
- Kitchen: **AGUARDANDO → PREPARACAO → PRONTO** → forwarded to delivery queue.
- Delivery: order waits for a courier → **TRANSPORTE** → **ENTREGUE** → archived against the customer.
- Every status change is timestamped and trackable from the order number.

---

## 3. Tech stack & build (verified)

| Aspect | Value | Source |
|---|---|---|
| Language | Java 21 (records, `var`, text blocks) | `pom.xml` |
| Framework | Spring Boot 3.5.4 (`spring-boot-starter-parent`) | `pom.xml` |
| Web | `spring-boot-starter-web` (REST, Tomcat, Jackson) | `pom.xml` |
| Data (current) | `spring-boot-starter-jdbc` + `JdbcTemplate` | `pom.xml`, `Adaptadores/Dados/*` |
| Data (target) | **JPA** — migration is a dedicated phase (17/06) | spec |
| DB | H2 in-memory `jdbc:h2:mem:pizzadb`, console at `/h2` | `application.yaml` |
| DB init | `spring.sql.init.mode: always` → **`schema.sql` + `data.sql` re-run on every boot** | `application.yaml` |
| Lombok | present in `pom.xml` but **deliberately unused** in domain | convention |
| Tests | JUnit 5 + Mockito + Spring Test (`spring-boot-starter-test`) | `pom.xml` |
| Server port | 8080 | `application.yaml` |
| Build/run | `./mvnw spring-boot:run` · test `./mvnw test` | wrapper present |

> ⚠️ **DB resets every boot.** Because `mode: always` reloads `data.sql`, any runtime-persisted state (current cardápio, current discount policy, orders, new users) is wiped on restart unless seeded in `data.sql` or the init mode is reconsidered during the JPA phase. Note this when designing UC2/UC4 persistence.

---

## 4. Clean Architecture — package map & dependency rule

Three levels, Portuguese package names. **Dependencies point inward only.**

```
com.bcopstein.ex4_lancheriaddd_v1
├── Dominio/                         (core — depends on NOTHING)
│   ├── Entidades/                   pure POJOs / records, no Spring, no JPA, no Lombok
│   ├── Servicos/                    @Service domain services (+ Imposto/ strategy subpackage)
│   └── Dados/                       repository INTERFACES only (ports)
├── Aplicacao/                       (use cases — depends on Dominio)
│   ├── *UC.java                     @Component, single public run(...) method
│   └── Responses/                   DTOs returned by UCs (records or classes)
└── Adaptadores/                     (adapters — depend on Aplicacao + Dominio)
    ├── Apresentacao/                @RestController + Presenters/ (view models)
    ├── Dados/                       *JDBC repository IMPLEMENTATIONS (adapters)
    └── Config/                      @ConfigurationProperties beans
```

**Dependency rule (from `OrganizacaoEmPacotes.puml`):**
`Adaptadores.Apresentacao → Aplicacao → Dominio.Servicos → Dominio.Dados (interface) ◄.. Adaptadores.Dados`.
Domain entities and `Dominio.Dados` interfaces carry **no framework annotations**. The boot class uses `@ComponentScan("com.bcopstein")` so beans in every subpackage are discovered.

---

## 5. Current state — what already exists (baseline)

| Area | State | Notes |
|---|---|---|
| Study-case menu flow | ✅ | `RecuperaListaCardapiosUC` (GET `/cardapio/lista`) + `RecuperarCardapioUC` (GET `/cardapio/{id}`), `CardapioService`, `CardapioRepository(JDBC)`, Presenters |
| Welcome endpoint | ✅ | `Controller` → GET `/` returns "Bem Vindo a Pizzaria ECA" |
| **Imposto (tax) service** | ✅ | `ServicoImposto` + `FabricaEstrategiaImposto` + `IEstrategiaCalculoImposto` + 2 strategies (`Lei0412de2022` = 10%, `Lei5762de2026` = 15% above R$50) + `ImpostoProperties` + `imposto.lei-vigente` config. **Satisfies the ≥2 tax strategies requirement.** Unit tests present. |
| Domain entities | 🟡 | `Cardapio`, `CabecalhoCardapio`(record), `Produto`, `Receita`, `Ingrediente`, `ItemEstoque`, `Pedido`(+`Status` enum), `ItemPedido`, `Cliente` exist as POJOs. `Pedido`/`ItemPedido`/`Cliente` are **unwired** (no service/repo/UC yet). |
| Cozinha simulation | 🟡 | `ICozinhaService` + `CozinhaService` (queues + `ScheduledExecutorService`). **Not Spring-annotated** — see gotcha in §6.7. Template for Entrega. |
| DB schema | 🟡 | 9 tables (see §11). Missing: current-cardápio, discount-policy, orders, status-history, users-with-password, couriers. |
| JDBC repositories | 🟡 | Cardápio/Produtos/Ingredientes/Receitas only. |
| Persistence style | 🟡 | JDBC today; **JPA migration pending** (phase 17/06). |
| Authentication | ⬜ | Not started. `Cliente` and `clientes` table have **no `senha` column**. |
| Pedidos / Estoque / Descontos / Entrega / Pagamento services | ⬜ | Not started. |

---

## 6. Patterns to Mirror (copy these — new code must match)

> All snippets are real excerpts from the codebase. Source paths are relative to the project root.

### 6.1 Domain entity — pure POJO / record, no framework
```java
// SOURCE: Dominio/Entidades/Cardapio.java
public class Cardapio {
    private CabecalhoCardapio cabecalhoCardapio;
    private List<Produto> produtos;
    public Cardapio(CabecalhoCardapio cabecalhoCardapio, List<Produto> produtos) { ... }
    public CabecalhoCardapio getCabecalhoCardapio(){ return cabecalhoCardapio; }
    public List<Produto> getProdutos() { return produtos; }
    public void setProdutos(List<Produto> produtos){ this.produtos = produtos; } // two-stage hydration
}
// SOURCE: Dominio/Entidades/CabecalhoCardapio.java  — value objects as records
public record CabecalhoCardapio(long id, String titulo) { }
```
Rules: no `@Entity`/`@Component`/Lombok in `Dominio.Entidades`. Constructor takes all fields. Validate invariants in the constructor (see `Produto` — throws `IllegalArgumentException` on invalid preço/descrição/receita). Prefer `record` for immutable value objects.

### 6.2 Repository — interface in `Dominio.Dados`, JDBC impl in `Adaptadores.Dados`
```java
// SOURCE: Dominio/Dados/CardapioRepository.java  (PORT — no Spring, domain language)
public interface CardapioRepository {
    List<CabecalhoCardapio> cardapiosDisponiveis();
    Cardapio recuperaPorId(long id);
    List<Produto> indicacoesDoChef();
}
// SOURCE: Adaptadores/Dados/CardapioRepositoryJDBC.java  (ADAPTER)
@Component
public class CardapioRepositoryJDBC implements CardapioRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ProdutosRepository produtosRepository;
    @Autowired
    public CardapioRepositoryJDBC(JdbcTemplate jdbcTemplate, ProdutosRepository produtosRepository) { ... }
    @Override public Cardapio recuperaPorId(long id) {
        String sql = "SELECT id, titulo FROM cardapios WHERE id = ?";
        List<Cardapio> cs = jdbcTemplate.query(sql, ps -> ps.setLong(1, id),
            (rs, n) -> new Cardapio(new CabecalhoCardapio(rs.getLong("id"), rs.getString("titulo")), null));
        if (cs.isEmpty()) return null;
        Cardapio c = cs.getFirst();
        c.setProdutos(produtosRepository.recuperaProdutosCardapio(id));
        return c;
    }
}
```
Rules: method names in Portuguese (`recuperaPorId`, not `findById`). Parameterized queries with `?` + `ps.setLong(1, …)` (1-based). Inline lambda `RowMapper`. Repositories may depend on other repositories to compose the object graph. **Stereotype:** existing code mixes `@Component` and `@Repository`; **use `@Repository` for new repos** (semantic), it is interchangeable here.

### 6.3 Domain service — `@Service`, constructor injection, thin
```java
// SOURCE: Dominio/Servicos/CardapioService.java
@Service
public class CardapioService {
    private final CardapioRepository cardapioRepository;
    @Autowired public CardapioService(CardapioRepository cardapioRepository){ this.cardapioRepository = cardapioRepository; }
    public Cardapio recuperaCardapio(long id){ return cardapioRepository.recuperaPorId(id); }
    public List<CabecalhoCardapio> recuperaListaDeCardapios(){ return cardapioRepository.cardapiosDisponiveis(); }
}
```

### 6.4 Use Case — `@Component`, single `run(...)`, returns a Response DTO
```java
// SOURCE: Aplicacao/RecuperarCardapioUC.java
@Component
public class RecuperarCardapioUC {
    private final CardapioService cardapioService;
    @Autowired public RecuperarCardapioUC(CardapioService cardapioService){ this.cardapioService = cardapioService; }
    public CardapioResponse run(long idCardapio){
        Cardapio cardapio = cardapioService.recuperaCardapio(idCardapio);
        List<Produto> sugestoes = cardapioService.recuperaSugestoesDoChef();
        return new CardapioResponse(cardapio, sugestoes);
    }
}
// SOURCE: Aplicacao/Responses/CabecalhoCardapioResponse.java  (record DTO)
public record CabecalhoCardapioResponse(List<CabecalhoCardapio> cabecalhos) { }
```
Rules: UCs are `@Component` (services are `@Service`). Exactly one public method, named `run`. Returns a `Response` DTO from `Aplicacao.Responses` (record for simple wraps, class with getters when aggregating). Never return entities to the controller directly through the public API — see Presenters.

### 6.5 Controller + Presenter — `@RestController`, returns Presenter view models
```java
// SOURCE: Adaptadores/Apresentacao/CardapioController.java
@RestController
@RequestMapping("/cardapio")
public class CardapioController {
    private final RecuperarCardapioUC recuperaCardapioUC;
    private final RecuperaListaCardapiosUC recuperaListaCardapioUC;
    public CardapioController(RecuperarCardapioUC a, RecuperaListaCardapiosUC b){ this.recuperaCardapioUC=a; this.recuperaListaCardapioUC=b; }

    @GetMapping("/{id}") @CrossOrigin("*")
    public CardapioPresenter recuperaCardapio(@PathVariable("id") long id){
        CardapioResponse resp = recuperaCardapioUC.run(id);
        CardapioPresenter p = new CardapioPresenter(resp.getCardapio().getCabecalhoCardapio().titulo());
        // ...map produtos → p.insereItem(...)
        return p;
    }
}
// SOURCE: Adaptadores/Apresentacao/Presenters/CabecalhoCardapioPresenter.java
public record CabecalhoCardapioPresenter(long id, String titulo) {}
```
Rules: `@RequestMapping("/base")` on the class, `@GetMapping/@PostMapping` on methods. **`@CrossOrigin("*")` must be repeated on each method** (class-level is not inherited here). Inject UCs by constructor. Transform `Response → Presenter` **in the controller** (two-stage: Domain → Response → Presenter). Jackson serializes the returned Presenter automatically.

### 6.6 ⭐ Strategy service (Imposto) — THE canonical pattern for Descontos
```java
// SOURCE: Dominio/Servicos/Imposto/IEstrategiaCalculoImposto.java
public interface IEstrategiaCalculoImposto {
    String getCodigoLei();              // registry key (the law number string)
    double calcular(double valorVenda); // domain behavior
}
// SOURCE: Dominio/Servicos/Imposto/Lei0412de2022.java
@Component
public class Lei0412de2022 implements IEstrategiaCalculoImposto {
    public static final String CODIGO = "0412/2022";
    private static final double ALIQUOTA = 0.10;
    @Override public String getCodigoLei(){ return CODIGO; }
    @Override public double calcular(double valorVenda){
        if (valorVenda < 0) throw new IllegalArgumentException("Valor da venda nao pode ser negativo: " + valorVenda);
        return valorVenda * ALIQUOTA;
    }
}
// SOURCE: Dominio/Servicos/Imposto/FabricaEstrategiaImposto.java
@Component
public class FabricaEstrategiaImposto {
    private final Map<String, IEstrategiaCalculoImposto> porCodigo; // unmodifiable
    private final ImpostoProperties properties;
    public FabricaEstrategiaImposto(List<IEstrategiaCalculoImposto> estrategias, ImpostoProperties properties){
        this.porCodigo = estrategias.stream()
            .collect(Collectors.toUnmodifiableMap(IEstrategiaCalculoImposto::getCodigoLei, Function.identity()));
        this.properties = properties;
    }
    public IEstrategiaCalculoImposto criar(){ return criar(properties.getLeiVigente()); }     // config-driven
    public IEstrategiaCalculoImposto criar(String codigoLei){                                  // explicit override
        if (codigoLei == null || codigoLei.isBlank())
            throw new IllegalStateException("Lei vigente nao configurada (imposto.lei-vigente em application.yaml)");
        IEstrategiaCalculoImposto e = porCodigo.get(codigoLei);
        if (e == null) throw new IllegalArgumentException("Nenhuma estrategia registrada para a lei: " + codigoLei
            + ". Leis disponiveis: " + porCodigo.keySet());
        return e;
    }
}
// SOURCE: Dominio/Servicos/ServicoImposto.java
@Service
public class ServicoImposto {
    private final FabricaEstrategiaImposto fabrica;
    public ServicoImposto(FabricaEstrategiaImposto fabrica){ this.fabrica = fabrica; }
    public double calcularImposto(double valorVenda){ return fabrica.criar().calcular(valorVenda); }
    public String getCodigoLeiVigente(){ return fabrica.criar().getCodigoLei(); }
}
// SOURCE: Adaptadores/Config/ImpostoProperties.java  +  application.yaml
@Component @ConfigurationProperties(prefix = "imposto")
public class ImpostoProperties { private String leiVigente; /* getter+setter */ }
//   application.yaml →  imposto:\n    lei-vigente: "0412/2022"   (kebab-case ↔ camelCase)
```
**How Descontos differs (read carefully):**
- Imposto strategy is identified by the **law-number string**; Desconto by a **code string** like `"PromocaoVerao"`, `"PromocaoDiaDosPais"`.
- Imposto active strategy is chosen at **startup** via `@ConfigurationProperties` / **environment variable** (Spring relaxed binding: env `IMPOSTO_LEIVIGENTE` overrides `imposto.lei-vigente` — this already satisfies "configurável por variável de ambiente").
- **Desconto active policy must be switchable at RUNTIME via an admin endpoint (UC4)** and **persisted**. So instead of (or in addition to) a `Properties` bean, the discount factory/service needs a **mutable, persisted "política corrente"** (a `DescontoRepository` holding the current code). Use `criar(String codigo)` with the code read from that store; keep the auto-discovered `Map<String, IEstrategiaCalculoDesconto>` for the strategies themselves.
- Imposto needs **≥2** strategies (done). Desconto needs **≥3** strategies. A discount strategy likely needs more inputs than a tax one (e.g. customer order history for the loyalty 7% rule) — design `calcular(...)` accordingly (e.g. pass item subtotal + a customer/loyalty context).

### 6.7 Simulated service behind an interface (Cozinha → template for Entrega/Pagamento)
```java
// SOURCE: Dominio/Servicos/ICozinhaService.java
public interface ICozinhaService { void chegadaDePedido(Pedido p); void pedidoPronto(); }
// SOURCE: Dominio/Servicos/CozinhaService.java
public class CozinhaService implements ICozinhaService {
    private Queue<Pedido> filaEntrada; private Pedido emPreparacao; private Queue<Pedido> filaSaida;
    private ScheduledExecutorService scheduler;
    public CozinhaService(){ filaEntrada=new LinkedBlockingQueue<>(); filaSaida=new LinkedBlockingQueue<>();
        scheduler=Executors.newSingleThreadScheduledExecutor(); }
    private synchronized void colocaEmPreparacao(Pedido p){ p.setStatus(Pedido.Status.PREPARACAO);
        emPreparacao=p; scheduler.schedule(this::pedidoPronto, 5, TimeUnit.SECONDS); }
    @Override public synchronized void chegadaDePedido(Pedido p){ filaEntrada.add(p);
        if (emPreparacao==null) colocaEmPreparacao(filaEntrada.poll()); }
}
```
Rules for fakes: define an `IXxxService` interface in `Dominio.Servicos`; the fake implements it. Simulate timed transitions with `ScheduledExecutorService`; guard shared state with `synchronized`.
> ⚠️ **Gotcha:** `CozinhaService` has **no `@Service` annotation** and a no-arg constructor — it is not currently a managed bean. For UC9 you will need status changes **persisted to the DB**; either annotate it `@Service` and inject a repository, or wrap it. When mirroring for Entrega/Pagamento, decide bean management up front. Spec allows Pagamento to be a "fake" that always reports success. **→ Resolved by Seam #2 (§13/§16): annotate `@Service`, inject `ServicoPedido`, and route status persistence through it.**

### 6.8 Tests — JUnit 5, pure-logic without Spring, integration with `@SpringBootTest`
```java
// SOURCE: src/test/.../Imposto/Lei0412de2022Test.java   (pure unit — no Spring context)
class Lei0412de2022Test {
    private static final double DELTA = 1e-9;
    private final Lei0412de2022 lei = new Lei0412de2022();
    @Test void venda100ImpostoDe10(){ assertEquals(10.0, lei.calcular(100.0), DELTA); }
    @Test void vendaNegativaLancaExcecao(){ assertThrows(IllegalArgumentException.class, () -> lei.calcular(-1.0)); }
}
// SOURCE: FabricaEstrategiaImpostoTest.java  — helper builder to control the dependency graph
private FabricaEstrategiaImposto fabricaCom(String leiVigente){
    ImpostoProperties props = new ImpostoProperties(); props.setLeiVigente(leiVigente);
    return new FabricaEstrategiaImposto(List.of(lei0412, lei5762), props);
}
// SOURCE: Ex4LancheriadddV1ApplicationTests.java  — integration smoke test
@SpringBootTest class Ex4LancheriadddV1ApplicationTests { @Test void contextLoads(){} }
```
Rules: `*Test` class suffix, descriptive `void` method names, `DELTA` for double equality, `assertThrows` for errors, private helper builders for setup. Pure-logic tests instantiate directly (no `@SpringBootTest`); use `@SpringBootTest` only for wiring/integration.
> 📌 **Spec requirement — Pessoa 1 slice ✅ (2026-06-25), Pessoa 2 slice ⬜:** "os casos de teste devem ser especificados na forma de comentário no mesmo arquivo do driver de teste." **Pessoa 1's order-cycle drivers already carry the comment-form `Casos de teste` blocks** (`ServicoPedidoTest`, `ServicoEstoqueTest`, `SubmeterPedidoParaAprovacaoUCTest`, `PagarPedidoUCTest`, `PedidoRepositoryEntreguesTest` — written during P2/UC10, coverage closed to 100% line+branch in P6). **Still ⬜ for Pessoa 2's drivers** (discount strategies, `ServicoDesconto`, `ServicoImposto`, auth) — the 29/06 phase must add the comment blocks there.

---

## 7. Use Case catalog (UC1–UC12)

`(Adm)` = authenticated admin · `(A)` = authenticated customer. Auth itself lands in the 24/06 phase; until then, build the endpoints and treat the actor as a parameter/header. **Owner per UC is in §16.**

| UC | Name | Actor | Status | Suggested endpoint | Touches | Acceptance |
|---|---|---|---|---|---|---|
| UC1 | Listar cardápios disponíveis | Adm | ✅ | GET `/cardapio/lista` | CardapioService | Returns all stored cardápio headers |
| UC2 | Definir cardápio corrente | Adm | ✅ | PUT `/cardapio/corrente/{id}` | CardapioService + `configuracao` k/v | Sets active cardápio; persisted (MERGE); unknown id → 400 |
| UC3 | Listar políticas de desconto | Adm | ✅ | GET `/descontos/politicas` | ServicoDesconto | Lists 3 codes (SemDesconto, Fidelidade7, PromocaoVerao) + corrente |
| UC4 | Definir política de desconto corrente | Adm | ✅ | PUT `/descontos/corrente/{codigo}` | ServicoDesconto + DescontoRepository | Switches active policy at runtime; persisted; unknown code → 400 (path var, not body — decision D4) |
| UC5 | Carregar cardápio | Cliente | ✅ | GET `/cardapio/corrente` | CardapioService | Returns the **current** cardápio (no corrente set → IllegalState/500) |
| UC6 | Submeter pedido para aprovação | Cliente | ✅ | POST `/pedidos` | **ServicoPedido**, Estoque, Cardápio, **ServicoImposto**, **ServicoDesconto** | Returns approved order w/ price, or RECUSADO highlighting unfulfillable items (availability derived from estoque, D11); sets NOVO→APROVADO, baixa estoque |
| UC7 | Solicitar status de pedido | Cliente | ✅ | GET `/pedidos/{id}/status` | ServicoPedido | Returns current status + full timestamped `historico_status` by order number |
| UC8 | Cancelar pedido | Cliente | ✅ | POST `/pedidos/{id}/cancelar` | ServicoPedido | Cancels an APROVADO-but-not-PAGO order (→CANCELADO); rejects otherwise (400) |
| UC9 | Pagar pedido | Cliente | 🟡 | POST `/pedidos/{id}/pagar` | ServicoPagamento (fake), Cozinha (sim), Entrega (sim), ServicoPedido | **Pessoa 1 done:** fake-pay → PAGO (stamped) → AGUARDANDO → handoff to `ICozinhaService`; each transition persisted (Seam #2). **Pending Pessoa 2:** real Cozinha/Entrega sims advance PREPARACAO→…→ENTREGUE |
| UC10 | Listar pedidos entregues entre 2 datas | Cliente/Adm | ✅ | GET `/pedidos/entregues?ini=&fim=` | ServicoPedido | Returns ENTREGUE orders in `[ini, fim]` — **Pessoa 1 done (Seam #4)**. Filters by the `ENTREGUE` transition timestamp in `historico_status` (D15); day-window `[ini, fim+1)`; bad range/missing param → 400. [report](reports/listar-pedidos-entregues-uc10-report.md) |
| UC11 | Cadastrar usuário | Anônimo | ⬜ | POST `/clientes` | ServicoCliente + ClienteRepository | Registers cliente (usuário=email, senha, nome, cpf, telefone, endereço); needs `senha` column |
| UC12 | Entrar no sistema | Anônimo | ⬜ | POST `/auth/login` | Auth | Authenticates by email+senha; issues session/token |

---

## 8. Domain services catalog (8 services)

| Service | Status | Interface / location | Notes |
|---|---|---|---|
| **Impostos** | ✅ | `ServicoImposto` + `Imposto/` strategy pkg | ≥2 strategies done; env-var configurable |
| **Descontos** | ✅ | `ServicoDesconto` + `Desconto/` strategy pkg | 3 strategies (SemDesconto/Fidelidade7/PromocaoVerao); runtime-switchable (UC4), persisted via `configuracao`; `calcular` returns the discount **amount** w/ `ContextoDesconto` |
| **Cardápio** | 🟡 | `CardapioService` | "cardápio corrente" ✅ (UC2/UC5); indisponibilidade of items still pending (UC6, P2) |
| **Pedidos** | ✅ | `ServicoPedido` | Validates order, computes `(Σ−desconto)+imposto` via Imposto+Desconto, drives the status machine, `@Transactional` submit. **Sole writer of `historico_status` — Seam #2** (impl via `IRegistradorStatus`). |
| **Estoque** | ✅ | `ServicoEstoque` | `ItensEstoqueRepository` + availability derived from stock; `verificaDisponibilidade`/`baixaEstoque` per order (1 unidade por ingrediente da receita, D11) |
| **Cozinha** | 🟡 | `ICozinhaService`/`CozinhaService` | Simulated; must **persist** status changes to DB for UC9 — via `ServicoPedido` callback (Seam #2) |
| **Entrega** | ⬜ | `IEntregaService` + fake (NEW) | Simulate like Cozinha; assign courier; persist TRANSPORTE→ENTREGUE via `ServicoPedido` (Seam #2) |
| **Pagamento** | ✅ | `IPagamentoService` + `PagamentoFake` | Fake that always returns "paid" (synchronous boolean; Pessoa 1) |

> **Design constraint (spec):** every service that starts as a fake/simplification must sit **behind an interface** to allow swapping for a real implementation later. Imposto and Desconto must be designed for frequent formula changes (strategy pattern — already the model).

---

## 9. Implementation Phases (the cronograma — drives "next pending")

Update the **Status** column as you go. Each phase depends on the prior unless noted. **Owner** = who drives that phase; full task-level split is in **§16**. Labels `Pessoa 1`/`Pessoa 2` (never `P1/P2` — those are phases).

| # | Date | Phase | Use cases / deliverable | Depends on | Owner | Status |
|---|---|---|---|---|---|---|
| P0 | 08/06/2026 | Definição + estudo de caso rodando | App boots, menu UCs run, Imposto service | — | ambos | ✅ complete |
| P1 | 10/06/2026 | Cardápio corrente + Descontos | UC1, UC2, UC3, UC4, UC5 | P0 | ambos | ✅ complete — [plan](plans/completed/cardapio-corrente-e-descontos.plan.md) · [report](reports/cardapio-corrente-e-descontos-report.md) |
| **P2** | **15/06/2026** | **Ciclo do pedido** | **UC6, UC7, UC8, UC9** (+ Pedidos, Estoque, Pagamento, Entrega services) | P1 | **Pessoa 1 (lead) · Pessoa 2 (sims Cozinha/Entrega)** — Seams #1, #2 | **🟡 in-progress** — Pessoa 1 ✅ (UC6–UC9 flow, verified) · Pessoa 2 sims pending — [plan](plans/completed/ciclo-do-pedido-pessoa1.plan.md) · [report](reports/ciclo-do-pedido-pessoa1-report.md) |
| P3 | 17/06/2026 | Persistência com JPA | Migrate JDBC repos → JPA; entities annotated | P2 | **Pessoa 2 (framework) · ambos (repos próprios)** — Seam #3 | ⬜ pending |
| P4 | 22/06/2026 | Usuários + histórico | UC10, UC11, UC12 | P3 | **Pessoa 2 (UC11/UC12) · Pessoa 1 (UC10)** — Seam #4 | ⬜ pending |
| P5 | 24/06/2026 | Autenticação | Login/authorization enforced on `(A)`/`(Adm)` UCs | P4 | **Pessoa 2** | ⬜ pending |
| P6 | 29/06/2026 | Drivers de teste | Tests (with comment-form specs) for: discount strategies, ServicoDesconto, ServicoImposto, SubmeterPedidoUC | P1–P5 | **dividido — Pessoa 1 (Pedido/Estoque) · Pessoa 2 (Desconto/Imposto/Auth)** | 🟡 Pessoa 1 ✅ · Pessoa 2 ⬜ — [plan](plans/completed/test-drivers-pessoa1-p6.plan.md) · [report](reports/test-drivers-pessoa1-p6-report.md) |
| P7 | 01/07/2026 | Apresentação | Demo; deliver source `.zip` to Moodle | all | ambos | ⬜ pending |

> **Team note (spec):** balanced commits/PRs per member are graded; members without provable contributions get no grade. A project leader owns the base repo. **→ Load is front-loaded onto Pessoa 1 (P2 is critical-path); see §16 "Load balancing" for the interleave that keeps contributions even over time.**
>
> ⚠️ **Schedule reality (2026-06-22):** the cronograma dates for P2 (15/06) and P3 (17/06) have passed while the last logged milestone is P1 (✅ 09/06). P2–P3 are therefore late and strict phase-gating is no longer affordable — the parallelism in §16 is what recovers the schedule, **contingent on Seams #1 and #2 being locked before P2 coding begins.** No phase statuses are changed by this note; it records the calendar, not new progress.

---

## 10. Per-phase task breakdown

### ▶ P1 (10/06) — Cardápio corrente + Descontos — DETAILED (next pass)

**T1.1 — Confirm UC1 (Adm) & UC5 (Cliente).** UC1 already exists. For UC5 "carregar cardápio", add a notion of **current cardápio**: a `GET /cardapio/corrente` that returns the active menu (UC5 depends on UC2). Mirror §6.4/§6.5.

**T1.2 — UC2 Definir cardápio corrente.**
- Persist the current cardápio id. Recommended: a tiny key/value table `configuracao(chave varchar pk, valor varchar)` (also reusable for the discount policy), seeded in `data.sql` with a default — **remember the DB resets each boot (§3)**.
- `CardapioRepository`: add `void defineCorrente(long id)` + `Cardapio recuperaCorrente()`. Implement in `CardapioRepositoryJDBC` (§6.2).
- `CardapioService`: add `defineCardapioCorrente(long id)` / `recuperaCardapioCorrente()`.
- New `DefinirCardapioCorrenteUC` (`@Component`, `run(long id)`) + `RecuperarCardapioCorrenteUC`.
- Controller: `@PutMapping("/corrente/{id}")` and `@GetMapping("/corrente")` on `CardapioController`.

**T1.3 — Descontos strategy package (mirror §6.6).** Create `Dominio/Servicos/Desconto/`:
- `IEstrategiaCalculoDesconto` — `String getCodigo();` + a `calcular(...)` that takes the item subtotal and whatever loyalty context the rules need.
- **≥3 concrete strategies**, each `@Component` with `public static final String CODIGO`. Suggested set:
  1. `SemDesconto` (CODIGO `"SemDesconto"`) → 0%.
  2. `FidelidadeFrequente` (CODIGO `"Fidelidade7"`) → 7% per item **if** the customer made >3 orders in the last 20 days (the spec's loyalty rule).
  3. `PromocaoVerao` (CODIGO `"PromocaoVerao"`) → a flat seasonal %.
- `FabricaEstrategiaDesconto` — auto-discovers `List<IEstrategiaCalculoDesconto>` into an unmodifiable `Map<String,…>`; `criar(String codigo)` with the same error handling (`IllegalState` blank / `IllegalArgument` unknown + list available).

**T1.4 — Runtime-configurable current policy (UC4).**
- `DescontoRepository` (port in `Dominio.Dados`) + JDBC impl: `String politicaCorrente()` / `void definePolitica(String codigo)` backed by the `configuracao` table. **This is the key difference from Imposto** (which is startup/env-config): the active discount policy changes at runtime and persists.
- `ServicoDesconto` (`@Service`): `criar()` reads current code from the repo then `fabrica.criar(code)`; expose `listarPoliticas()` (codes), `definirPolitica(codigo)`, and `calcularDesconto(...)`.

**T1.5 — UC3 / UC4 endpoints.** New `DescontoController` (`@RestController @RequestMapping("/descontos")`): `GET /politicas` (UC3, returns codes via a Presenter), `PUT /corrente` (UC4, body `{codigo}`; 400 on unknown). New UCs `ListarPoliticasDescontoUC`, `DefinirPoliticaDescontoUC`. Mirror §6.4/§6.5.

**T1.6 — Tests** (full drivers land in P6, but add now): unit tests for each discount strategy + `FabricaEstrategiaDesconto` following §6.8.

**P1 acceptance:** `GET /descontos/politicas` lists ≥3 codes; `PUT /descontos/corrente` switches policy and persists; `GET /cardapio/corrente` returns the menu set by `PUT /cardapio/corrente/{id}`; existing Imposto/menu behavior unchanged; `./mvnw test` green.

### ▶ P2 (15/06) — Order cycle (UC6–UC9) — outline
> **Ownership:** Pessoa 1 builds the order flow; Pessoa 2 builds the Cozinha/Entrega/Pagamento simulations. **Freeze Seam #1 (service interfaces) and Seam #2 (`historico_status` single writer) before either starts.** See §16.
- **Estoque (Pessoa 1):** `ItensEstoqueRepository` (table `itensEstoque` exists) + `ServicoEstoque` with `verificaDisponibilidade(pedido)` and `baixaEstoque(pedido)` (portions per recipe).
- **ServicoPedido (Pessoa 1):** consistency check → stock check (mark unfulfillable cardápio items indisponível) → NOVO→APROVADO → cost = `(Σ itens − desconto) + imposto` via `ServicoImposto` + `ServicoDesconto`. **Owns all status transitions via `registrarTransicao(pedidoId, novoStatus)` (timestamped) — Seam #2.**
- `PedidoRepository` + tables `pedidos`, `itens_pedido`, `historico_status` (status + timestamp) — **Pessoa 1**.
- UC7 status lookup; UC8 cancel (only APROVADO & not PAGO) — **Pessoa 1**.
- UC9 pay → `ServicoPagamento` (fake, **Pessoa 2**) → Cozinha (sim, **Pessoa 2**) → Entrega (sim, **Pessoa 2**), each transition timestamped **through `ServicoPedido` (Pessoa 1)**. The pay endpoint/trigger is **Pessoa 1**.
- **`SubmeterPedidoParaAprovacaoUC`** is a required test target in P6 — keep it cleanly testable.

### ▶ P3 (17/06) — JPA migration — outline
> **Ownership (Seam #3):** Pessoa 2 owns the migration **framework decision** (resolves open question #2) + adds `spring-boot-starter-data-jpa` + decides `ddl-auto`/init-mode. Then **each person migrates their own repos** against that agreed pattern. Do it on a branch; do not edit shared entities simultaneously.
- Add `spring-boot-starter-data-jpa`; annotate entities (`@Entity/@Id/@ManyToMany/@OneToMany`) — note this **adds framework annotations to the domain**, a tension with the current pure-POJO rule; the class may prefer a separate persistence-model or accept JPA on entities. Decide and record in §13.
- Replace `*JDBC` adapters with Spring Data repositories implementing the same `Dominio.Dados` ports. Revisit `spring.sql.init` vs `ddl-auto`. `preco` is `bigint` in schema but read as `int` in code — fix the type mismatch here.

### ▶ P4 (22/06) — UC10/UC11/UC12 — outline
> **Ownership:** Pessoa 2 = UC11/UC12 (users/login); Pessoa 1 = UC10 (delivered-orders query, since it reads `pedidos` — Seam #4).
- UC11 register: add `senha` to `Cliente` + `clientes` table; `ClienteRepository` + `ServicoCliente`; validate uniqueness of email.
- UC12 login skeleton; UC10 delivered-orders-between-dates query (`PedidoRepository.pedidosEntreguesEntre(ini, fim)`).

### ▶ P5 (24/06) — Authentication — outline
> **Ownership:** Pessoa 2 (full). The security config/filter wraps Pessoa 1's `/pedidos/*` controllers — coordinate how the authenticated cliente reaches `ServicoPedido`.
- Enforce auth on `(A)`/`(Adm)` endpoints (Spring Security or a token filter). Username = email. Distinguish admin vs customer roles.

### ▶ P6 (29/06) — Test drivers — outline
> **Ownership:** Pessoa 1 = `SubmeterPedidoParaAprovacaoUC`, `ServicoPedido`, `ServicoEstoque`. Pessoa 2 = discount strategies, `ServicoDesconto`, `ServicoImposto`, auth.
- Unit + integration drivers with **test cases written as comments in the same file** (spec) for the targets above. Target ≥80% on these.

---

## 11. Database — current schema & what's needed

**Existing tables (`schema.sql`):** `clientes(cpf pk, nome, celular, endereco, email)` · `ingredientes(id pk, descricao)` · `itensEstoque(id pk, quantidade, ingrediente_id→ingredientes)` · `receitas(id pk, titulo)` · `receita_ingrediente(receita_id, ingrediente_id)` · `produtos(id pk, descricao, preco bigint)` · `produto_receita(produto_id, receita_id)` · `cardapios(id pk, titulo)` · `cardapio_produto(cardapio_id, produto_id)`.

**Tables/columns to add (by phase + owner):**
- P1 (ambos, done): `configuracao(chave varchar pk, valor varchar)` — stores `cardapio_corrente` and `desconto_corrente`. (Seed defaults in `data.sql`.)
- P2 (**Pessoa 1**): `pedidos(id, cliente_cpf, status, valor, impostos, desconto, valor_cobrado, data_hora_pagamento, endereco_entrega)`, `itens_pedido(pedido_id, produto_id, quantidade)`, `historico_status(pedido_id, status, data_hora)`. Possibly `entregadores` (**Pessoa 2**, if the Entrega sim needs persisted couriers).
- P4/P5 (**Pessoa 2**): `clientes.senha` column; role/`usuarios` if separating admin.

> Reminder: `clientes` currently has **no `senha`**, and the delivery address is collected **per order** (UC6), distinct from the customer's registered `endereco`.

---

## 12. Validation commands

```bash
# From project root: ex5-pizzaria-clean-baseT1
./mvnw -q compile             # EXPECT: build success, no compile errors
./mvnw -q test                # EXPECT: all unit + integration tests pass
./mvnw spring-boot:run        # EXPECT: starts on :8080, schema.sql+data.sql load
# Smoke checks (separate shell):
curl -s localhost:8080/                 # "Bem Vindo a Pizzaria ECA"
curl -s localhost:8080/cardapio/lista   # JSON list of cardápio headers
# H2 console: http://localhost:8080/h2  (jdbc:h2:mem:pizzadb, user sa, no password)
```
Per-phase: add the new endpoints' curl checks to the Progress Log when you implement them.

---

## 13. Decisions & open questions

**Decisions (append as made):**
- _2026-06-08_: This PRD is the project's living tracker, stored in-repo at `.claude/PRPs/`.
- _2026-06-08 (P1 plan)_: **k/v `configuracao(chave pk, valor)` table** holds both `cardapio.corrente` and `desconto.corrente`. Writes go through one shared adapter-layer helper `ConfiguracaoRepositoryJDBC` (DRY) using **H2 `MERGE INTO`** — the repo had **zero** JDBC write precedent (grep-verified).
- _2026-06-08 (P1 plan)_: **`FabricaEstrategiaDesconto` does NOT take a config/`Properties` bean** (unlike Imposto) and has **no no-arg `criar()`**. The active policy is read from `DescontoRepository` at call time so UC4 can switch it at **runtime**.
- _2026-06-08 (P1 plan)_: **400-on-unknown-code** is delivered by a new global `@RestControllerAdvice` (`IllegalArgumentException→400`, `IllegalStateException→500`); no `ResponseEntity`/advice precedent existed. Affects all endpoints (intentional, semantically correct).
- _2026-06-08 (P1 plan)_: **UC4 uses `PUT /descontos/corrente/{codigo}` (path var)**, not a JSON body — no `@RequestBody` precedent, codes are path-safe.
- _2026-06-22 (divisão de trabalho)_: **Adopted a two-person split** — **Pessoa 1** = Pedidos / fluxo de negócio; **Pessoa 2** = Usuários, cardápio, descontos e infraestrutura. Full task-level breakdown in **§16**; per-phase Owner column added to §9. Labels `Pessoa 1/2` are used everywhere to avoid colliding with the phase labels `P0–P7`.
- _2026-06-22 (Seam #1 — service interfaces, P2)_: `IPagamentoService`, `ICozinhaService`, `IEntregaService` **method signatures are agreed jointly and frozen BEFORE any P2 implementation starts.** This contract is what lets Pessoa 1 (order flow) and Pessoa 2 (sims) work in parallel instead of serializing on integration. `ICozinhaService` already exists (§6.7) — review and freeze it; define the other two to match its shape. **[Action: 30-min sync to sign off signatures; record final signatures here when done.]**
- _2026-06-22 (Seam #2 — `historico_status` single writer, P2)_: **`ServicoPedido` (Pessoa 1) is the SOLE writer of `historico_status`.** All status transitions go through one method, e.g. `registrarTransicao(long pedidoId, Pedido.Status novo)`, which stamps the timestamp. Cozinha/Entrega (Pessoa 2) **call back into `ServicoPedido`** rather than writing the table directly. This keeps the write path + timestamp logic single-owner (no merge conflicts on the table) and **resolves open question #3**: annotate `CozinhaService @Service`, inject `ServicoPedido` (or a narrow port), same for the Entrega fake.
- _2026-06-22 (Seam #3 — JPA ownership, P3)_: **Pessoa 2 owns the JPA framework decision** (resolves open question #2 — annotate entities vs. separate persistence model), adds `spring-boot-starter-data-jpa`, and decides `ddl-auto`/init-mode. **Then each person migrates their OWN repos** against that agreed pattern (Pessoa 1: `Pedido*`, `ItensEstoque`; Pessoa 2: `Cliente`, cardápio, desconto). Migrate on a branch; never edit shared entities at the same time.
- _2026-06-22 (Seam #4 — UC10 ownership, P4)_: UC10 (listar entregues entre datas) reads `pedidos`, so the query `pedidosEntreguesEntre(ini, fim)` lives on `PedidoRepository` (**Pessoa 1**). **Recommendation: Pessoa 1 owns the full UC10** (UC + Controller + Presenter) since it is a pedido query — this moves UC10 off Pessoa 2's original list. Alternative: Pessoa 1 exposes the repo query, Pessoa 2 builds the UC on top. **[Team: pick one and mark resolved.]**
- _2026-06-22 (load balancing / cronograma)_: P2 is critical-path and almost entirely Pessoa 1, while Pessoa 2's heavy new work (auth, users) is later and their cardápio/descontos work is already done. To keep commits balanced over time (grading note, §9) and avoid idle gaps: **during P2, Pessoa 2 builds `Cliente`+`senha` / `ServicoCliente` / the auth skeleton (all independent of the order cycle) plus the Cozinha/Entrega/Pagamento sims; once P2 ships, Pessoa 1 picks up JPA-of-own-repos + UC10.** As of 2026-06-22 the P2/P3 cronograma dates have passed (see §9 schedule note) — this interleave is also the recovery plan, contingent on Seams #1 and #2 being locked first.

- _2026-06-24 (P2 impl — Pessoa 1)_: decisions made by the order-cycle plan (full rationale in [`plans/completed/ciclo-do-pedido-pessoa1.plan.md`](plans/completed/ciclo-do-pedido-pessoa1.plan.md)): **D6** money is `double` reais (preco int-centavos ÷100, each value rounded via `Math.round(v*100)/100.0`; `pedidos` money columns `double`); **D7** add `CANCELADO`+`RECUSADO` to `Pedido.Status`; **D8** imposto base = subtotal `Σ itens` (per §2), `custoFinal=(subtotal−desconto)+imposto`; **D9** order ids via H2 `auto_increment`+`GeneratedKeyHolder`; **D10** first `@PostMapping`/`@RequestBody`/`INSERT`/`UPDATE`, request DTOs in `Aplicacao/Requests/`; **D11** indisponibilidade **derived from estoque** (no persisted flag — resolves OQ#6c rounding too) **[team-ratifiable]**; **D12** loyalty count server-derived from `PedidoRepository`; **D13** pay→kitchen orchestration in `PagarPedidoUC` (not `ServicoPedido`) to avoid a bean cycle; `ServicoPedido implements IRegistradorStatus` (narrow Seam #2 port); **D14** `itens_pedido` folded into `PedidoRepository` (aggregate root). **Hardening from the impl review:** `DataIntegrityViolationException`→**400** (bad cpf/over-width/dup item no longer leaks a 500); `@Transactional` on `ServicoPedido.submeter` (atomic order creation). **Seam #1 signatures frozen:** `ICozinhaService{chegadaDePedido(Pedido);pedidoPronto()}` (kept), `IEntregaService{pedidoParaEntrega(Pedido);pedidoEntregue()}` (Pessoa 2), `IPagamentoService{boolean processarPagamento(Pedido)}` (Pessoa 1). A **temporary `CozinhaServiceStub`** (`Adaptadores/Config`) satisfies `ICozinhaService` until Pessoa 2 ships the real sim — **delete it then** (else two beans).

- _2026-06-25 (UC10 impl — Pessoa 1)_: **D15** — UC10 (`GET /pedidos/entregues?ini=&fim=`) filters by the **`ENTREGUE` transition timestamp in `historico_status`** (JOIN `pedidos ⋈ historico_status` on `status='ENTREGUE'`), **not** by any `pedidos` column — there is no delivery-time column and Seam #2 makes `historico_status` the single source of status-time truth (consistent with D11: derive, don't add columns). **No DDL change, no `Pedido` mutation.** `ini`/`fim` are **dates**; the UC expands them to a half-open instant window `[ini 00:00, fim+1 00:00)` so the whole `fim` day is inclusive. Returns **all** delivered orders in the window (admin view; per-customer scoping deferred to P5/auth). `ini>fim`/null → `IllegalArgumentException`→400; missing param → 400 (Spring default). The `'ENTREGUE'` SQL literal is sourced from `Pedido.Status.ENTREGUE.name()` (kept in sync with the enum). New domain read-model `PedidoEntregue(Pedido, LocalDateTime dataHoraEntrega)` carries the delivery instant. **Seam #4 resolved:** the query `entreguesEntre(ini,fim)` lives on `PedidoRepository` and **Pessoa 1 owns the full UC10 vertical** (UC + controller + presenter). Report: [`reports/listar-pedidos-entregues-uc10-report.md`](reports/listar-pedidos-entregues-uc10-report.md). **Deviation:** integration driver named `PedidoRepositoryEntreguesTest` (not `...IT`) because the project runs only Surefire (`*Test`); a `*IT` class needs Failsafe (unconfigured) and would be skipped by `./mvnw test`.

**Open questions for future sessions (resolve and record):**
1. ~~**Persistence of "corrente" state vs DB reset on boot.**~~ **Resolved (P1):** accepted; `configuracao` defaults re-seeded in `data.sql` each boot; runtime switches are session-scoped. Revisit during JPA phase (P3).
2. **JPA vs pure-POJO domain.** Annotating entities with JPA breaks the current "no framework annotations in `Dominio.Entidades`" rule. Choose: annotate entities, or introduce separate persistence models. (P3) **→ Owner: Pessoa 2 (Seam #3) decides; record the choice here.**
3. ~~**Cozinha/Entrega bean management.**~~ **Resolved (Seam #2):** annotate `@Service`, inject `ServicoPedido`, persist status changes through `registrarTransicao(...)`. (P2)
4. ~~**Discount `calcular` signature.**~~ **Resolved (P1):** `calcular(double subtotalItens, ContextoDesconto contexto)` where `ContextoDesconto(int pedidosUltimos20Dias)` is **caller-supplied** (no `pedidos` persistence yet); returns the discount **amount**. Real order-counting waits for `PedidoRepository` (P2).
5. **Auth mechanism.** Spring Security vs lightweight token filter. (P5) **→ Owner: Pessoa 2.**
6. **Issues from the P1 review.** **✅ Addressed in the fix-up pass (2026-06-09):** (a) `GET /cardapio/{id}` unknown id → **404** via `RecursoNaoEncontradoException`, plus numeric path type-mismatch → 400; (d) `Adaptadores.Dados` stereotypes normalized to **`@Repository`**; (e-cors) per-method `@CrossOrigin("*")` replaced by a centralized, configurable `CorsConfig` (`app.cors.allowed-origins`); plus L1 `{codigo}` length bound, L2 Response/Presenter field alignment (`codigos`→`politicas`), L5 unused-import cleanup. **⬜ Still deferred (pre-existing, future phases):** (b) `recuperaProdutosCardapio` JOIN can duplicate a produto with >1 receita — add `DISTINCT`/UNIQUE (P3); (c) `preco int` centavos vs `double` discount math — round/`BigDecimal` when the cost formula is wired (P2) and fix `preco bigint`-vs-`int` (P3); (e-sec) H2 console open + `jdbc: DEBUG` logging — kept for the course demo, lock down with auth (P5).

---

## 14. Progress Log (append-only — newest first)

> Each session adds a dated entry: what changed, which phase/UCs, test status, follow-ups.

- **2026-06-25** — **P6 IMPLEMENTED (Pessoa 1 slice) → P6 `⬜ → 🟡` (Pessoa 1 ✅ · Pessoa 2 ⬜).** Wired **JaCoCo** (`jacoco-maven-plugin` 0.8.12, `prepare-agent`+`report` on the `test` phase — no coverage tool existed before) and closed coverage on the four Pessoa-1 order-cycle drivers. **Finding (deviation from plan):** the three named targets were **already at 100% line coverage** at baseline — `ServicoPedido.historico()` is exercised transitively via `PagarPedidoUC` (the plan predicted it line-uncovered). The real residual gaps were **defensive null branches** (2 in `ServicoPedido.submeter`, 2 in `SubmeterPedidoParaAprovacaoUC.run`), so Task 3/4 were **re-aimed** at those: added `historicoRetornaTransicoesDoPedido` + `submeterItensNulosLanca`/`submeterEnderecoNuloLanca` (ServicoPedidoTest 16→19 cases) and `itensDuplicadosSaoAgregados` + `requestNuloLanca`/`itensNulosLanca` (SubmeterPedidoParaAprovacaoUCTest 5→8 cases), each added to the in-file comment-form `Casos de teste` block. **Result: `ServicoPedido`, `ServicoEstoque`, `SubmeterPedidoParaAprovacaoUC`, `PagarPedidoUC` all 100% line AND 100% branch.** Suite **84→90, all green, BUILD SUCCESS** under **JDK 21** (`/snap/android-studio/232/jbr`; system 17 fails). **Plan Task 5 (optional JDBC IT) skipped** — coverage already ≫80%. §6.8 spec-requirement note ratified (Pessoa 1 ✅; Pessoa 2 drivers still ⬜). 1 build edit (`pom.xml`) + 2 test files edited; no `src/main` behavior changed. Plan archived to `plans/completed/`; report at [`reports/test-drivers-pessoa1-p6-report.md`](reports/test-drivers-pessoa1-p6-report.md). **Not committed** (awaiting user). Next Pessoa 1 task: **P3 JPA-of-own-repos**, still gated on Seam #3 (Pessoa 2's framework decision) — provisional plan at [`plans/jpa-migration-pessoa1-p3.plan.md`](plans/jpa-migration-pessoa1-p3.plan.md).
- **2026-06-25** — **Two Pessoa-1 plans created ("Both, sequenced") — no code changed.** After UC10 shipped, the two remaining Pessoa-1 items are **P6 test drivers** (unblocked) and **P3 JPA-of-own-repos** (blocked on Seam #3). (1) **P6 — [`plans/test-drivers-pessoa1-p6.plan.md`](plans/test-drivers-pessoa1-p6.plan.md)** (Small–Medium, confidence 9/10): **finding — the P6 Pessoa-1 target drivers already exist *with* the spec's comment-form `Casos de teste` blocks** (`ServicoPedidoTest` 16 cases, `ServicoEstoqueTest` 6, `SubmeterPedidoParaAprovacaoUCTest` 5, `PagarPedidoUCTest` 3, `PedidoRepositoryEntreguesTest` IT 3 — all written during P2/UC10). So §6.8's "tests lack comment-form specs" note is **stale for Pessoa 1's slice**. P6 reduces to: add JaCoCo (none configured), measure the 3 targets, fill 2 gaps (`ServicoPedido.historico()` happy+404; UC duplicate-product `merge` aggregation), optional JDBC IT, ratify §6.8. **Recommended next executable task.** (2) **P3 — [`plans/jpa-migration-pessoa1-p3.plan.md`](plans/jpa-migration-pessoa1-p3.plan.md)** (Large, **PROVISIONAL — ⛔ gated on Seam #3 / OQ#2**): migrate `Pedido*`/`Historico`/`ItensEstoque` JDBC adapters to JPA behind the **unchanged domain ports**, with two mutually-exclusive approaches (annotate entities vs separate persistence model) pending **Pessoa 2's framework decision** (G1) + `ddl-auto`/init policy (G2) + the `…-data-jpa` starter (G3). Carries a Task-0 gate, the decision-independent scope (3 adapters, UC10 JOIN, atomic `baixaSeDisponivel`, `preco bigint`-vs-`int` fix), and `recuperaPorId`-returns-null 404 contract preservation. **Do not implement until Seam #3 is recorded in §13.** No phase statuses flipped (P6 not yet started; P3 blocked).
- **2026-06-25** — **UC10 IMPLEMENTED & VERIFIED (Pessoa 1) → UC10 `🔜 → ✅`.** `GET /pedidos/entregues?ini=&fim=` lists ENTREGUE orders in a day-window, filtering by the `ENTREGUE` timestamp in `historico_status` (JOIN, **D15** — no DDL/entity change). Added the full vertical: `PedidoEntregue` read-model, `PedidoRepository.entreguesEntre` + JDBC JOIN (shared `mapeiaPedido` row-mapper extracted from `recuperaPorId`), `ServicoPedido.listarEntreguesEntre`, `ListarPedidosEntreguesUC` (owns `LocalDate`→`[ini, fim+1)` expansion), 2 Responses + Presenter, `PedidoController` endpoint. **6 new + 7 edited files; suite 78→84, all green** (3 service unit cases + a `@SpringBootTest` integration driver `PedidoRepositoryEntreguesTest` exercising the real H2 JOIN). Built/tested **offline** with **JDK 21** (`/snap/android-studio/232/jbr`; system default 17). **Live HTTP smoke on :8090 passed:** empty window→`[]`, bad range→400, missing param→400, existing `/status`→404 (no regression). **Seam #4 resolved** (Pessoa 1 owns full UC10). **Deviations:** IT named `...Test` not `...IT` (project has no Failsafe); two extra UC test fakes gained the new port method to compile; window-boundary semantics verified in the IT (real SQL) rather than via a fake. Plan archived to `plans/completed/`; report at [`reports/listar-pedidos-entregues-uc10-report.md`](reports/listar-pedidos-entregues-uc10-report.md). **Not committed** (awaiting user). Next Pessoa 1 task: JPA-of-own-repos (P3), still gated on Seam #3 (Pessoa 2's framework decision).
- **2026-06-25** — **UC10 plan created (Pessoa 1, next unblocked task) → UC10 flipped `⬜ → 🔜`.** Plan at [`plans/completed/listar-pedidos-entregues-uc10.plan.md`](plans/completed/listar-pedidos-entregues-uc10.plan.md) (single-pass, confidence 9/10). After P2 (Pessoa 1 slice ✅), the two Pessoa-1 candidates were **UC10** (P4, Seam #4 — fully Pessoa-1-owned, reads own `pedidos`/`historico_status`, **unblocked**) and **JPA-of-own-repos** (P3 — **blocked on Seam #3**, Pessoa 2's framework decision first). Picked **UC10**. Scope: `GET /pedidos/entregues?ini=&fim=` returning ENTREGUE orders in a day-window; ~7 new + 2 edited files (read-model `PedidoEntregue`, `PedidoRepository.entreguesEntre` + JDBC JOIN, `ServicoPedido.listarEntreguesEntre`, `ListarPedidosEntreguesUC`, 2 Responses, Presenter, controller endpoint, unit cases + a `@SpringBootTest` IT for the SQL). **New decision D15 (to record in §13 on impl):** UC10 filters by the `ENTREGUE` timestamp in `historico_status` (JOIN), **not** `pedidos.data_hora_pagamento` — no new column, no entity mutation (consistent with D11). **No DDL change.** Confirmed via codebase read: no `entregues`/`pedidosEntregues` precedent (only the `ENTREGUE` enum constant exists); the JOIN is the sole net-new element. **GOTCHA noted:** no order reaches ENTREGUE at runtime until Pessoa 2 ships the Entrega sim → the IT is the authoritative non-empty check (manual non-empty smoke needs an H2-console seed). No production code changed yet — next step: `/prp-implement plans/listar-pedidos-entregues-uc10.plan.md`.
- **2026-06-24** — **P2 IMPLEMENTED & VERIFIED (Pessoa 1 slice).** UC6–UC8 ✅, UC9 🟡 (pay→PAGO→AGUARDANDO→handoff done; Cozinha/Entrega advancement pending Pessoa 2). Built the full order aggregate: `pedidos`/`itens_pedido`/`historico_status` tables, `Pedido*`/`HistoricoStatus`/`ItensEstoque` repos (+JDBC: `auto_increment`+`GeneratedKeyHolder` INSERT, first `UPDATE`), `ServicoEstoque`, `ServicoPedido` (sole writer of history, `@Transactional`), `PagamentoFake`, 4 UCs, `PedidoController` (first `@PostMapping`/`@RequestBody`), + a **temporary `CozinhaServiceStub`** for Seam #1. **26 new main + 4 test + 3 edited; suite 51→75, all green; full live HTTP smoke on :8090 passed** (approved 148.5, status+historico, cancel, pay→AGUARDANDO, RECUSADO, 404, 400s). Built/ran with **JDK 21 at `/snap/android-studio/232/jbr`** (system default 17; port 8080 was occupied → used `SERVER_PORT=8090`; offline build fails — needs network). Adversarial 4-reviewer workflow: 13 findings raised, **verification stage failed on a session limit** so triaged manually → **2 fixed** (`DataIntegrityViolation`→400 for bad client input; `@Transactional` atomic submit — both live-verified), 11 dismissed (intentional/pre-existing/can't-occur). Plan archived to `plans/completed/`; report at [`reports/ciclo-do-pedido-pessoa1-report.md`](reports/ciclo-do-pedido-pessoa1-report.md). **Not committed** (awaiting user). Next: **Pessoa 2** delivers the real Cozinha/Entrega sims (Seam #2) and **deletes `CozinhaServiceStub`**; then P3 (JPA).
- **2026-06-24** — **P2 plan created (Pessoa 1 slice) → P2 flipped `pending → in-progress`.** Plan at [`plans/completed/ciclo-do-pedido-pessoa1.plan.md`](plans/completed/ciclo-do-pedido-pessoa1.plan.md) (single-pass, confidence 8/10) via an 8-reader codebase-mapping workflow (verbatim digests of entities, repos, services/UCs, presentation, the Cozinha sim, schema/config, tests, and the P1 plan/report). Scope: **Pessoa 1's** UC6–UC9 — `pedidos`/`itens_pedido`/`historico_status` tables, `PedidoRepository`/`HistoricoStatusRepository`/`ItensEstoqueRepository` (+JDBC), `ServicoEstoque`, `ServicoPedido` (sole writer of `historico_status` — Seam #2), `IPagamentoService`+`PagamentoFake`, 4 UCs + `PedidoController`, 3 unit-test drivers (~25 new + 3 edited files). **Cozinha/Entrega sims stay Pessoa 2** (consumed via the frozen Seam #1 interfaces — `ICozinhaService` reviewed; `IEntregaService`/`IPagamentoService` signatures proposed). Records **8 new decisions D6–D14** for §13 (money=double-reais+round D6; add `CANCELADO`/`RECUSADO` to `Pedido.Status` D7; imposto base = subtotal per §2 D8; `auto_increment`+`GeneratedKeyHolder` D9; first `@PostMapping`/`@RequestBody`/`INSERT`/`UPDATE` D10; indisponibilidade derived from estoque D11; loyalty count now server-derived D12; pay→kitchen orchestration in `PagarPedidoUC` to avoid a bean cycle D13; `itens_pedido` folded into the aggregate repo D14). **Confirmed via the map: no `pedidos`/`PedidoRepository`/`@PostMapping`/`@RequestBody`/`INSERT`/`UPDATE`/generated-key/`CANCELADO`-status precedent — all net-new; CORS is now global via `CorsConfig` (no per-method `@CrossOrigin`).** No production code changed yet — next step: `/prp-implement plans/completed/ciclo-do-pedido-pessoa1.plan.md`. **Two calls flagged for team ratification: D6 (money unit) and D11 (derived availability).**
- **2026-06-22** — **Division of labor recorded (no code change).** Added a two-person split (**Pessoa 1** = Pedidos/fluxo; **Pessoa 2** = Suporte/infra) as new **§16**, an **Owner** column to the §9 phase table, and four **seam decisions** to §13 (Seam #1 service-interface freeze, Seam #2 `historico_status` single writer + resolves OQ#3, Seam #3 JPA ownership, Seam #4 UC10 ownership) plus a load-balancing note. Annotated §6.7, §7, §8, §10, §11 with owners/seam pointers. Logged the **schedule reality**: P2 (15/06) and P3 (17/06) cronograma dates have passed with only P1 logged complete — phase-gating relaxed in favor of the §16 parallelism, contingent on Seams #1/#2 being locked before P2 coding. **No phase statuses changed; no production code touched.** Next: **P2 (UC6–UC9)** — start by freezing the three service interfaces.
- **2026-06-09** — **P1 IMPLEMENTED & VERIFIED → ✅ complete.** All UC1–UC5 done: cardápio corrente (UC2/UC5) + discount service (UC3/UC4) with 3 strategies, runtime-switchable & persisted via the `configuracao` k/v table (H2 `MERGE`), 400-on-unknown via `@RestControllerAdvice`. **18 new + 5 test + 6 edited files; suite 22→45 tests, all green; live HTTP smoke incl. both 400s passed.** Built/ran with **JDK 21** (system default is 17 — set `JAVA_HOME` to the bundled 21; `spring-boot:run` needs network, not `-o`). Adversarial review workflow (4 reviewers + skeptics): **0 confirmed HIGH/CRITICAL**; applied 4 hardening fixes (null-guard, safe parse, deterministic order, context validation); pre-existing issues deferred to §13 #6. Report: [`reports/cardapio-corrente-e-descontos-report.md`](reports/cardapio-corrente-e-descontos-report.md); plan archived to `plans/completed/`. **Not committed** (awaiting user). Next: **P2 (UC6–UC9)**.
- **2026-06-08** — **P1 plan created** at [`plans/completed/cardapio-corrente-e-descontos.plan.md`](plans/completed/cardapio-corrente-e-descontos.plan.md) (single-pass, confidence 9/10) via a 7-reader + audit workflow. Grep confirmed the repo has **no JDBC write path, no `@PutMapping`/`@RequestBody`/`ResponseEntity`/advice, no Mockito** — so the plan introduces 5 net-new patterns (H2 `MERGE` upsert, global `@RestControllerAdvice` 400, runtime-switchable factory, `ContextoDesconto` loyalty param, fake-repo tests). Scope: 16 new + 6 edited files, 11 tasks. P1 flipped `pending → in-progress`. Open questions #1 and #4 resolved (see §13). No production code changed yet — next step: `/prp-implement`.
- **2026-06-08** — PRD created from `TF_2026_1_Pizzaria.pdf` + full codebase analysis (8-cluster exploration). Baseline confirmed: study-case menu flow (UC1/UC5 partial) and Imposto strategy service (≥2 strategies) complete = **P0 ✅**. Next pending phase: **P1 (UC1–UC5)** — cardápio corrente + Descontos service mirroring Imposto. No code changed in this session.

---

## 15. Source references (mandatory reading before implementing)

| Priority | File | Why |
|---|---|---|
| P0 | `Dominio/Servicos/Imposto/*`, `Dominio/Servicos/ServicoImposto.java`, `Adaptadores/Config/ImpostoProperties.java`, `application.yaml` | Canonical strategy-service pattern to mirror for Descontos (§6.6) |
| P0 | `Aplicacao/RecuperarCardapioUC.java` + `Aplicacao/Responses/*` | UC + Response DTO pattern (§6.4) |
| P0 | `Adaptadores/Apresentacao/CardapioController.java` + `Presenters/*` | Controller + Presenter pattern (§6.5) |
| P1 | `Dominio/Dados/CardapioRepository.java` + `Adaptadores/Dados/CardapioRepositoryJDBC.java` | Repository port/adapter + JdbcTemplate (§6.2) |
| P1 | `Dominio/Servicos/CardapioService.java` | Domain service pattern (§6.3) |
| P1 | `Dominio/Entidades/Pedido.java`, `ItemPedido.java`, `Cliente.java`, `Produto.java` | Entity shapes for the order cycle + auth |
| P1 | `Dominio/Servicos/CozinhaService.java` + `ICozinhaService.java` | Simulated-service template (§6.7) |
| P2 | `src/test/.../Imposto/*Test.java` | Test conventions (§6.8) |
| ref | `schema.sql`, `data.sql` | Current DB (§11) |
| ref | `OrganizacaoEmPacotes.puml`, `DrgClasses4camadas.puml` | Architecture diagrams (§4) |

---

## 16. Division of labor (two-person split)

> **Added 2026-06-22.** This is the **ownership axis**, orthogonal to the **time/phase axis** in §9.
> Phases say *when*; this says *who*. Where the two cross is a **seam** — those four contracts
> (below) must be agreed before parallel work begins, or P2 serializes on integration and the
> "balanced commits" grading risk returns. Labels: **Pessoa 1 / Pessoa 2** (never `P1/P2`).

### Pessoa 1 — Pedidos e fluxo de negócio
Owns the order cycle end-to-end.

- **Entidades:** `Pedido`, `ItemPedido`.
- **Serviços:** `ServicoPedido`, `ServicoEstoque`, `IPagamentoService` + `PagamentoFake`. *(Pagamento is Pessoa 1's per the original split; if Pessoa 2 prefers to own all three sims, move it — note it under Seam #1.)*
- **Casos de uso:** UC6 (submeter), UC7 (status), UC8 (cancelar), **payment trigger of UC9** (POST `/pedidos/{id}/pagar` + orchestration), **UC10** (listar entregues — moved here per Seam #4).
- **Persistência:** `PedidoRepository`, `ItensPedidoRepository`, `HistoricoStatusRepository`. **Sole writer of `historico_status` (Seam #2).**
- **Banco:** `pedidos`, `itens_pedido`, `historico_status`.
- **Endpoints:** `POST /pedidos`, `GET /pedidos/{id}/status`, `POST /pedidos/{id}/cancelar`, `POST /pedidos/{id}/pagar`, `GET /pedidos/entregues`.
- **Testes (P6):** `SubmeterPedidoParaAprovacaoUCTest`, `ServicoPedido`, `ServicoEstoque`.
- **JPA (P3):** migrates own repos against Pessoa 2's framework decision (Seam #3).

### Pessoa 2 — Usuários, cardápio, descontos e infraestrutura
Owns everything that supports the system.

- **Entidades:** `Cliente` (+ `senha` column, P4).
- **Serviços:** `ServicoDesconto`, `ServicoImposto`, `CardapioService` *(all ✅ from P1)*; `ServicoCliente`; `ICozinhaService`/`CozinhaService`, `IEntregaService`/`EntregaService` (sims). *(Cozinha/Entrega persist status only by calling back into `ServicoPedido` — Seam #2.)*
- **Casos de uso:** UC1–UC5 *(✅ done)*; the **Cozinha + Entrega half of UC9** (sims driven by Pessoa 1's pay trigger); UC11 (cadastro); UC12 (login).
- **Persistência:** `ClienteRepository`; cardápio repos; desconto repo; **owns the JPA migration framework decision (Seam #3)** then migrates own repos.
- **Banco:** `clientes.senha`; `entregadores` (if the Entrega sim needs persisted couriers); any auth tables.
- **Endpoints:** `/cardapio/*`, `/descontos/*`, `POST /clientes`, `POST /auth/login`.
- **Testes (P6):** discount strategies, `ServicoDesconto`, `ServicoImposto`, auth.

### The four seams (where ownership crosses a phase dependency)

These are the only places the split frays. Each is recorded as a dated decision in §13 — agree them **before** the relevant phase starts.

| # | Seam | Phase | Risk if ignored | Resolution (see §13) |
|---|---|---|---|---|
| **#1** | Service interfaces `IPagamentoService` / `ICozinhaService` / `IEntregaService` | P2 | Pessoa 1 & Pessoa 2 can't work in parallel; P2 serializes | **Freeze signatures jointly before any P2 code.** 30-min sync; record final signatures in §13. |
| **#2** | `historico_status` writes (Pessoa 2's sims trigger transitions on Pessoa 1's table) | P2 | Two writers → merge conflicts + duplicated timestamp logic | **`ServicoPedido` is sole writer** via `registrarTransicao(...)`; sims call back into it. Also resolves OQ#3 (annotate `CozinhaService @Service`, inject `ServicoPedido`). |
| **#3** | JPA migration touches both people's repos at once | P3 | A shared refactor breaks both features simultaneously | **Pessoa 2 owns the framework decision** (OQ#2: annotate vs. separate model) + `ddl-auto`/init-mode; **each migrates own repos** on a branch; no simultaneous entity edits. |
| **#4** | UC10 reads `pedidos` but was assigned to Pessoa 2 | P4 | Cross-ownership query, unclear who owns the read path | Query `pedidosEntreguesEntre(ini, fim)` lives on `PedidoRepository` (Pessoa 1). **Recommended: Pessoa 1 owns all of UC10.** Team to confirm. |

### Load balancing over time (grading note)
P2 is critical-path and almost all Pessoa 1; Pessoa 2's heavy work (auth, users) is later and their P1 work is done — so naïve phase-order leaves Pessoa 2 idle during P2, then front-loads grading risk. **Interleave instead:** during P2, Pessoa 2 builds `Cliente`+`senha` / `ServicoCliente` / auth skeleton (independent of the order cycle) **plus** the Cozinha/Entrega/Pagamento sims against the Seam #1 interfaces; once P2 ships, Pessoa 1 takes JPA-of-own-repos + UC10. This keeps commits/PRs balanced (spec grades provable per-member contribution) and is also the schedule-recovery plan given P2/P3 are already past their cronograma dates (§9).
