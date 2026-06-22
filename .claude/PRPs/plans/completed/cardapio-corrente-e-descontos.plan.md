# Plan: P1 — Cardápio corrente + Serviço de Descontos (UC1–UC5)

## Summary
Implement PRD phase **P1 (10/06/2026)**: a persisted "current menu" (UC2/UC5) and a
runtime-switchable, persisted discount-policy service (UC3/UC4) that mirrors the existing
Imposto strategy service but diverges on three points the codebase has **no precedent**
for — a JDBC **write path**, an HTTP **400-on-unknown-code** contract, and a factory that
reads its active strategy from a **repository at runtime** instead of from startup config.

## User Story
As a pizzaria **administrator**, I want to set the current menu and switch the active
discount policy at runtime (and have both persist), so that customers always load the
right menu and orders are priced under the discount rule I chose — without a redeploy.

## Problem → Solution
- **Now:** menu list (UC1) + per-id menu work; Imposto is the only strategy service; there is
  no "current menu", no discount service, and no way to persist/switch either.
- **After:** `GET /cardapio/corrente` returns the menu set by `PUT /cardapio/corrente/{id}`;
  `GET /descontos/politicas` lists ≥3 codes + the current one; `PUT /descontos/corrente/{codigo}`
  switches the active policy (persisted, `400` on unknown code). `ServicoDesconto` computes the
  discount **amount** for the UC6 cost formula `custoFinal = (Σ itens − desconto) + imposto`.

## Metadata
- **Complexity:** Medium (≈19 files: 14 new, 5 edited; ~450 new lines)
- **Source PRD:** `.claude/PRPs/tele-pizza-backend.prd.md`
- **PRD Phase:** P1 (10/06) — Cardápio corrente + Descontos
- **Base package:** `com.bcopstein.ex4_lancheriaddd_v1`  ·  **Java 21 · Spring Boot 3.5.4 · JDBC + H2**

---

## UX Design

Internal REST backend — no GUI. "UX" = the HTTP contract.

### Before
```
GET  /cardapio/lista     → [ {id,titulo}, ... ]          (UC1 ✅)
GET  /cardapio/{id}      → { titulo, itens[...] }         (✅)
GET  /                   → "Bem Vindo a Pizzaria ECA"     (✅)
(no current-menu, no discounts)
```

### After
```
GET  /cardapio/lista              → [ {id,titulo}, ... ]                    (UC1, unchanged)
GET  /cardapio/{id}               → { titulo, itens[...] }                  (unchanged)
PUT  /cardapio/corrente/{id}      → { titulo, itens[...] }   sets+returns   (UC2)  400 if id unknown
GET  /cardapio/corrente           → { titulo, itens[...] }   the active menu (UC5)
GET  /descontos/politicas         → { politicas:[...], corrente:"SemDesconto" }      (UC3)
PUT  /descontos/corrente/{codigo} → { politicas:[...], corrente:"<codigo>" }         (UC4)  400 if unknown
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Current menu | none | `PUT/GET /cardapio/corrente[/{id}]` | id persisted in `configuracao` k/v table |
| Discount policy | none | `GET /descontos/politicas`, `PUT /descontos/corrente/{codigo}` | code persisted; runtime switch |
| Error on bad input | HTTP 500 (default) | **HTTP 400** via `@RestControllerAdvice` | new global mapping |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `Dominio/Servicos/Imposto/FabricaEstrategiaImposto.java` | 16-47 | Canonical factory to mirror — **but drop the `ImpostoProperties` field** (see Gotcha G5) |
| P0 | `Dominio/Servicos/Imposto/Lei0412de2022.java` | 6-23 | Strategy class shape: `@Component`, `public static final CODIGO`, `private static final` rate, validate negative |
| P0 | `Dominio/Servicos/ServicoImposto.java` | 12-28 | `@Service` facade — `ServicoDesconto` mirrors it but reads current code from a **repo**, not config |
| P0 | `Adaptadores/Dados/CardapioRepositoryJDBC.java` | 16-59 | `@Component` + `JdbcTemplate`, 1-based `ps.setLong`, inline lambda RowMapper, `.getFirst()`-or-null, repo-composes-repo |
| P0 | `Adaptadores/Apresentacao/CardapioController.java` | 1-56 | `@RestController`/`@RequestMapping`, `@CrossOrigin` **per-method**, `@PathVariable`, Response→Presenter in controller |
| P1 | `Aplicacao/RecuperarCardapioUC.java` + `RecuperaListaCardapiosUC.java` | all | UC = `@Component`, single `run(...)`, `@Autowired` constructor, returns a Response DTO |
| P1 | `Dominio/Servicos/CardapioService.java` | 14-33 | Thin `@Service`, pure delegation, Portuguese method names |
| P1 | `src/test/.../Imposto/FabricaEstrategiaImpostoTest.java` | 14-62 | Private helper-builder `fabricaCom(...)`, `assertSame`, `assertThrows`, no Spring |
| P1 | `src/test/.../Imposto/Lei0412de2022Test.java` | 1-36 | `DELTA=1e-9`, direct instantiation, descriptive Portuguese names |
| ref | `src/main/resources/schema.sql` | 1-65 | DDL style (`create table if not exists`, 2-space indent) — append `configuracao` after L65 |
| ref | `src/main/resources/data.sql` | 1-68 | Seed INSERT style; cardápios id 1 ("Agosto") & 2 ("Setembro") exist |
| ref | `src/main/resources/application.yaml` | 1-28 | `spring.sql.init.mode: always` → schema+data reload every boot |

## External Documentation
| Topic | Source | Key Takeaway |
|---|---|---|
| H2 `MERGE` upsert | H2 SQL grammar | `MERGE INTO t (k,v) VALUES (?,?)` uses the **PK as the merge key** → insert-or-update in one statement. No SELECT-then-branch needed. |
| `JdbcTemplate.update` | Spring Framework JDBC | `update(String sql, PreparedStatementSetter pss)` mirrors the existing `query(sql, pss, rowMapper)` lambda style for writes. |
| `@RestControllerAdvice` | Spring Web | Centralizes exception→HTTP mapping; `@ExceptionHandler(IllegalArgumentException.class)` → `ResponseEntity` 400. |

> No third-party libraries added. Everything below uses dependencies already in `pom.xml`
> (`spring-boot-starter-jdbc`, `-web`, `h2`, `-test`). Mockito is present transitively but
> **deliberately not used** — the repo's tests are pure-POJO; we keep that culture.

---

## Patterns to Mirror

### STRATEGY_CLASS  — mirror exactly (`@Component` + static CODIGO + validate)
```java
// SOURCE: Dominio/Servicos/Imposto/Lei0412de2022.java:6-23
@Component
public class Lei0412de2022 implements IEstrategiaCalculoImposto {
    public static final String CODIGO = "0412/2022";
    private static final double ALIQUOTA = 0.10;
    @Override public String getCodigoLei() { return CODIGO; }
    @Override public double calcular(double valorVenda) {
        if (valorVenda < 0) {
            throw new IllegalArgumentException("Valor da venda nao pode ser negativo: " + valorVenda);
        }
        return valorVenda * ALIQUOTA;
    }
}
```

### FACTORY  — mirror the Map build + error handling, **DROP the config field** (G5)
```java
// SOURCE: Dominio/Servicos/Imposto/FabricaEstrategiaImposto.java:18-47
private final Map<String, IEstrategiaCalculoImposto> porCodigo;          // keep
public FabricaEstrategiaImposto(List<IEstrategiaCalculoImposto> estrategias, ImpostoProperties properties) {
    this.porCodigo = estrategias.stream()
        .collect(Collectors.toUnmodifiableMap(IEstrategiaCalculoImposto::getCodigoLei, Function.identity()));
    this.properties = properties;                                        // <-- REMOVE for Desconto
}
public IEstrategiaCalculoImposto criar(String codigoLei) {              // keep this overload
    if (codigoLei == null || codigoLei.isBlank())
        throw new IllegalStateException("Lei vigente nao configurada ...");
    IEstrategiaCalculoImposto e = porCodigo.get(codigoLei);
    if (e == null) throw new IllegalArgumentException(
        "Nenhuma estrategia registrada para a lei: " + codigoLei + ". Leis disponiveis: " + porCodigo.keySet());
    return e;
}
```

### SERVICE_FACADE
```java
// SOURCE: Dominio/Servicos/ServicoImposto.java:12-24
@Service
public class ServicoImposto {
    private final FabricaEstrategiaImposto fabrica;
    public ServicoImposto(FabricaEstrategiaImposto fabrica) { this.fabrica = fabrica; }
    public double calcularImposto(double valorVenda) { return fabrica.criar().calcular(valorVenda); }
}
```

### USE_CASE
```java
// SOURCE: Aplicacao/RecuperarCardapioUC.java:13-26
@Component
public class RecuperarCardapioUC {
    private CardapioService cardapioService;
    @Autowired public RecuperarCardapioUC(CardapioService cardapioService){ this.cardapioService = cardapioService; }
    public CardapioResponse run(long idCardapio){
        Cardapio cardapio = cardapioService.recuperaCardapio(idCardapio);
        List<Produto> sugestoes = cardapioService.recuperaSugestoesDoChef();
        return new CardapioResponse(cardapio, sugestoes);
    }
}
```

### CONTROLLER  (note `@CrossOrigin` repeated per-method, `@PathVariable(value="..")`)
```java
// SOURCE: Adaptadores/Apresentacao/CardapioController.java:32-45
@GetMapping("/{id}")
@CrossOrigin("*")
public CardapioPresenter recuperaCardapio(@PathVariable(value="id")long id){
    CardapioResponse cardapioResponse = recuperaCardapioUC.run(id);
    Set<Long> conjIdSugestoes = new HashSet<>(cardapioResponse.getSugestoesDoChef().stream()
        .map(produto->produto.getId()).toList());
    CardapioPresenter cardapioPresenter = new CardapioPresenter(cardapioResponse.getCardapio().getCabecalhoCardapio().titulo());
    for(Produto produto:cardapioResponse.getCardapio().getProdutos()){
        boolean sugestao = conjIdSugestoes.contains(produto.getId());
        cardapioPresenter.insereItem(produto.getId(), produto.getDescricao(), produto.getPreco(), sugestao);
    }
    return cardapioPresenter;
}
```

### REPOSITORY_READ  (1-based param, lambda RowMapper, `.getFirst()`-or-null)
```java
// SOURCE: Adaptadores/Dados/CardapioRepositoryJDBC.java:28-42 (abridged)
List<Cardapio> cardapios = this.jdbcTemplate.query(sql,
    ps -> ps.setLong(1, id),
    (rs, rowNum) -> new Cardapio(new CabecalhoCardapio(rs.getLong("id"), rs.getString("titulo")), null));
if (cardapios.isEmpty()) return null;
Cardapio cardapio = cardapios.getFirst();
```

### TEST_HELPER_BUILDER  (no Spring, no Mockito)
```java
// SOURCE: src/test/.../Imposto/FabricaEstrategiaImpostoTest.java:14-22
private final Lei0412de2022 lei0412 = new Lei0412de2022();
private FabricaEstrategiaImposto fabricaCom(String leiVigente) {
    ImpostoProperties props = new ImpostoProperties(); props.setLeiVigente(leiVigente);
    return new FabricaEstrategiaImposto(List.of(lei0412, lei5762), props);
}
```

### DDL_STYLE  /  DML_STYLE
```sql
-- SOURCE: schema.sql:9-12 — 'create table if not exists', 2-space indent, bigint PK, not null
create table if not exists ingredientes ( id bigint primary key, descricao varchar(255) not null );
-- SOURCE: data.sql:59 — explicit column list, single-quoted values, section comment
INSERT INTO cardapios (id,titulo) VALUES(1,'Cardapio de Agosto');
```

---

## NEW patterns (NO precedent in repo — verified by grep). Introduce these deliberately.

### N1 — JDBC WRITE via H2 `MERGE` (the literal "key difference from Imposto")
```java
// NEW. No jdbcTemplate.update / MERGE / INSERT exists anywhere in src/main/java (grep: NONE).
String sql = "MERGE INTO configuracao (chave, valor) VALUES (?, ?)"; // PK 'chave' = merge key
jdbcTemplate.update(sql, ps -> { ps.setString(1, chave); ps.setString(2, valor); });
```

### N2 — HTTP 400 contract via global advice (no `ResponseEntity`/advice exists; grep: NONE)
```java
// NEW: Adaptadores/Apresentacao/RestExceptionHandler.java
@RestControllerAdvice
public class RestExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException ex){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> serverError(IllegalStateException ex){
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
    }
}
```

### N3 — runtime-switchable factory: current code comes from a **repo**, not config (see G5).
### N4 — strategy loyalty context as an explicit value object (no pedidos persistence exists; G4).
### N5 — `ServicoDesconto` unit test uses a **hand-rolled fake repo** (no Mockito in repo; G6).

---

## Files to Change

### CREATE (16 main + 5 test)
| File | Purpose |
|---|---|
| `Dominio/Servicos/Desconto/IEstrategiaCalculoDesconto.java` | Strategy port: `getCodigo()` + `calcular(subtotal, ContextoDesconto)` |
| `Dominio/Servicos/Desconto/ContextoDesconto.java` | `record ContextoDesconto(int pedidosUltimos20Dias)` — loyalty signal (N4) |
| `Dominio/Servicos/Desconto/SemDesconto.java` | Strategy `"SemDesconto"` → 0% |
| `Dominio/Servicos/Desconto/FidelidadeFrequente.java` | Strategy `"Fidelidade7"` → 7% if `pedidos>3` |
| `Dominio/Servicos/Desconto/PromocaoVerao.java` | Strategy `"PromocaoVerao"` → flat 5% |
| `Dominio/Servicos/Desconto/FabricaEstrategiaDesconto.java` | Factory (mirror Imposto, **no config bean**) (N3) |
| `Dominio/Servicos/ServicoDesconto.java` | `@Service` facade; reads current code from `DescontoRepository` |
| `Dominio/Dados/DescontoRepository.java` | Port: `politicaCorrente()` / `definePolitica(codigo)` |
| `Adaptadores/Dados/ConfiguracaoRepositoryJDBC.java` | Shared k/v read+write helper (N1) — the ONLY write path |
| `Adaptadores/Dados/DescontoRepositoryJDBC.java` | Impl of `DescontoRepository` over `configuracao` |
| `Aplicacao/ListarPoliticasDescontoUC.java` + `DefinirPoliticaDescontoUC.java` | UC3 / UC4 |
| `Aplicacao/Responses/PoliticasDescontoResponse.java` | `record(List<String> codigos, String corrente)` |
| `Aplicacao/DefinirCardapioCorrenteUC.java` + `RecuperarCardapioCorrenteUC.java` | UC2 / UC5 |
| `Adaptadores/Apresentacao/DescontoController.java` | `/descontos` endpoints |
| `Adaptadores/Apresentacao/Presenters/PoliticasDescontoPresenter.java` | `record(List<String> politicas, String corrente)` |
| `Adaptadores/Apresentacao/RestExceptionHandler.java` | 400/500 mapping (N2) |

> Test files (5) listed under **Testing Strategy**.

### UPDATE (6)
| File | Change |
|---|---|
| `src/main/resources/schema.sql` | Append `configuracao(chave pk, valor)` table |
| `src/main/resources/data.sql` | Seed `cardapio.corrente='1'`, `desconto.corrente='SemDesconto'` |
| `Dominio/Dados/CardapioRepository.java` | Add `defineCorrente(long)` + `recuperaCorrente()` |
| `Adaptadores/Dados/CardapioRepositoryJDBC.java` | Inject `ConfiguracaoRepositoryJDBC`; implement the 2 new methods |
| `Dominio/Servicos/CardapioService.java` | Add `defineCardapioCorrente(long)` (validates existence) + `recuperaCardapioCorrente()` |
| `Adaptadores/Apresentacao/CardapioController.java` | Inject UC2/UC5; add `PUT /corrente/{id}` + `GET /corrente`; extract `montarPresenter(...)` helper |

## NOT Building (out of scope for P1)
- ❌ Any `pedidos`/`itens_pedido` tables, `PedidoRepository`, or order-history queries — **UC6+ (P2)**.
  `FidelidadeFrequente` therefore receives the order count via `ContextoDesconto` (caller-supplied), it does **not** query it.
- ❌ Applying discount/tax inside an actual order (`SubmeterPedidoUC`) — P2.
- ❌ JPA migration (P3), auth (P5), `senha` column (P4).
- ❌ `@RequestBody` request DTOs — UC4 uses a `@PathVariable` `{codigo}` (decision D4).
- ❌ Cross-restart durability of "corrente" state — DB reloads each boot; defaults re-seeded (decision D2).

---

## Step-by-Step Tasks

### Task 1 — `configuracao` table + seed defaults
- **ACTION:** Append to `schema.sql` (after line 65) and `data.sql` (end).
- **IMPLEMENT:**
  ```sql
  -- schema.sql
  -- Tabela chave/valor para estado corrente (cardapio corrente, politica de desconto corrente)
  create table if not exists configuracao (
    chave varchar(50) not null primary key,
    valor varchar(100) not null
  );
  ```
  ```sql
  -- data.sql
  -- Configuracao corrente (re-seed a cada boot: spring.sql.init.mode=always)
  INSERT INTO configuracao (chave, valor) VALUES ('cardapio.corrente', '1');
  INSERT INTO configuracao (chave, valor) VALUES ('desconto.corrente', 'SemDesconto');
  ```
- **MIRROR:** DDL_STYLE / DML_STYLE.
- **GOTCHA G2:** `mode: always` reloads both files every boot — these seeds are how "corrente" has a defined value after restart; runtime changes do **not** survive restart (acceptable for P1, revisit in P3).
- **VALIDATE:** `./mvnw spring-boot:run` boots clean; H2 console `SELECT * FROM configuracao` shows 2 rows.

### Task 2 — `ConfiguracaoRepositoryJDBC` (the write path, N1)
- **ACTION:** Create `Adaptadores/Dados/ConfiguracaoRepositoryJDBC.java`.
- **IMPLEMENT:**
  ```java
  @Component
  public class ConfiguracaoRepositoryJDBC {
      private JdbcTemplate jdbcTemplate;
      @Autowired public ConfiguracaoRepositoryJDBC(JdbcTemplate jdbcTemplate){ this.jdbcTemplate = jdbcTemplate; }

      public String valor(String chave){
          String sql = "SELECT valor FROM configuracao WHERE chave = ?";
          List<String> vs = jdbcTemplate.query(sql, ps -> ps.setString(1, chave), (rs, n) -> rs.getString("valor"));
          return vs.isEmpty() ? null : vs.getFirst();
      }
      public void define(String chave, String valor){
          String sql = "MERGE INTO configuracao (chave, valor) VALUES (?, ?)";
          jdbcTemplate.update(sql, ps -> { ps.setString(1, chave); ps.setString(2, valor); });
      }
  }
  ```
- **IMPORTS:** `java.util.List`, `org.springframework.beans.factory.annotation.Autowired`, `org.springframework.jdbc.core.JdbcTemplate`, `org.springframework.stereotype.Component`.
- **MIRROR:** REPOSITORY_READ for `valor(...)`; N1 for `define(...)`.
- **GOTCHA:** This is the single home for the write idiom (DRY, decision D3) — both Cardápio-corrente and Desconto-corrente delegate here. It is an **infrastructure helper** (no domain port) because it speaks k/v, not domain, language.
- **VALIDATE:** compiles; covered indirectly by Task 9 + the boot in Task 1.

### Task 3 — `CardapioRepository` corrente methods (UC2/UC5 persistence)
- **ACTION:** Add to port `Dominio/Dados/CardapioRepository.java`; implement in `CardapioRepositoryJDBC`.
- **IMPLEMENT (port):** `void defineCorrente(long id);`  ·  `Cardapio recuperaCorrente();`
- **IMPLEMENT (adapter):** add field `ConfiguracaoRepositoryJDBC configuracaoRepository` to the constructor (now 3 args), plus:
  ```java
  public static final String CHAVE_CORRENTE = "cardapio.corrente";
  @Override public void defineCorrente(long id){ configuracaoRepository.define(CHAVE_CORRENTE, String.valueOf(id)); }
  @Override public Cardapio recuperaCorrente(){
      String v = configuracaoRepository.valor(CHAVE_CORRENTE);
      return v == null ? null : recuperaPorId(Long.parseLong(v));
  }
  ```
- **MIRROR:** the existing `recuperaPorId` is reused for hydration (repo-composes-itself).
- **GOTCHA:** keep the `@Component` stereotype already on `CardapioRepositoryJDBC` (don't switch to `@Repository` — mixing exists but this class is `@Component`). Constructor-injection change is wired automatically by Spring.
- **VALIDATE:** `./mvnw compile`; `GET /cardapio/corrente` later returns menu 1.

### Task 4 — `CardapioService` corrente methods
- **ACTION:** Add two methods to `Dominio/Servicos/CardapioService.java`.
- **IMPLEMENT:**
  ```java
  public void defineCardapioCorrente(long id){
      if (cardapioRepository.recuperaPorId(id) == null) {
          throw new IllegalArgumentException("Cardapio inexistente: " + id); // -> HTTP 400 (N2)
      }
      cardapioRepository.defineCorrente(id);
  }
  public Cardapio recuperaCardapioCorrente(){ return cardapioRepository.recuperaCorrente(); }
  ```
- **MIRROR:** SERVICE_FACADE — thin delegation; the existence check is the one bit of guard logic.
- **VALIDATE:** unit-testable via a fake `CardapioRepository`; PUT unknown id → 400.

### Task 5 — UC2 + UC5 use cases
- **ACTION:** Create `DefinirCardapioCorrenteUC` and `RecuperarCardapioCorrenteUC` in `Aplicacao/`.
- **IMPLEMENT:**
  ```java
  @Component public class DefinirCardapioCorrenteUC {
      private CardapioService cardapioService;
      @Autowired public DefinirCardapioCorrenteUC(CardapioService s){ this.cardapioService = s; }
      public long run(long idCardapio){ cardapioService.defineCardapioCorrente(idCardapio); return idCardapio; }
  }
  @Component public class RecuperarCardapioCorrenteUC {
      private CardapioService cardapioService;
      @Autowired public RecuperarCardapioCorrenteUC(CardapioService s){ this.cardapioService = s; }
      public CardapioResponse run(){
          Cardapio cardapio = cardapioService.recuperaCardapioCorrente();
          List<Produto> sugestoes = cardapioService.recuperaSugestoesDoChef();
          return new CardapioResponse(cardapio, sugestoes);
      }
  }
  ```
- **MIRROR:** USE_CASE. `RecuperarCardapioCorrenteUC` returns the **same `CardapioResponse`** as `RecuperarCardapioUC` so the controller reuses one presenter path.
- **IMPORTS (UC5):** `java.util.List`, `...Responses.CardapioResponse`, `...Entidades.{Cardapio,Produto}`, `...Servicos.CardapioService`, Spring `Component`/`Autowired`.
- **VALIDATE:** `./mvnw compile`.

### Task 6 — `CardapioController`: add corrente endpoints + extract presenter helper
- **ACTION:** Inject the two new UCs (constructor → 4 args); refactor GET `/{id}` to call a new private `montarPresenter(CardapioResponse)`; add `PUT /corrente/{id}` and `GET /corrente`.
- **IMPLEMENT:**
  ```java
  private CardapioPresenter montarPresenter(CardapioResponse cardapioResponse){
      Set<Long> conjIdSugestoes = new HashSet<>(cardapioResponse.getSugestoesDoChef().stream()
          .map(produto->produto.getId()).toList());
      CardapioPresenter p = new CardapioPresenter(cardapioResponse.getCardapio().getCabecalhoCardapio().titulo());
      for(Produto produto:cardapioResponse.getCardapio().getProdutos()){
          p.insereItem(produto.getId(), produto.getDescricao(), produto.getPreco(),
                       conjIdSugestoes.contains(produto.getId()));
      }
      return p;
  }
  @PutMapping("/corrente/{id}") @CrossOrigin("*")
  public CardapioPresenter defineCardapioCorrente(@PathVariable(value="id") long id){
      definirCardapioCorrenteUC.run(id);
      return montarPresenter(recuperarCardapioCorrenteUC.run());
  }
  @GetMapping("/corrente") @CrossOrigin("*")
  public CardapioPresenter recuperaCardapioCorrente(){
      return montarPresenter(recuperarCardapioCorrenteUC.run());
  }
  ```
- **IMPORTS:** add `org.springframework.web.bind.annotation.PutMapping`, the two UC types.
- **MIRROR:** CONTROLLER (note `@CrossOrigin` per-method, `@PathVariable(value="..")`).
- **GOTCHA G1 (routing):** `GET /cardapio/corrente` must resolve to `recuperaCardapioCorrente`, not `recuperaCardapio(@PathVariable long id)`. Spring's `PathPattern` matches the **literal** `/corrente` ahead of the `/{id}` template, and `"corrente"` cannot bind to `long` anyway — so it's unambiguous. Keep the literal mapping.
- **GOTCHA:** GET `/{id}` behavior must stay byte-identical after the helper extraction.
- **VALIDATE:** `GET /cardapio/1` unchanged; `PUT /cardapio/corrente/2` then `GET /cardapio/corrente` returns "Cardapio de Setembro"; `PUT /cardapio/corrente/999` → 400.

### Task 7 — Descontos strategy package (3 strategies + context + interface)
- **ACTION:** Create the 5 files in `Dominio/Servicos/Desconto/`.
- **IMPLEMENT:**
  ```java
  // IEstrategiaCalculoDesconto.java
  public interface IEstrategiaCalculoDesconto {
      String getCodigo();
      double calcular(double subtotalItens, ContextoDesconto contexto); // returns the discount AMOUNT
  }
  // ContextoDesconto.java
  public record ContextoDesconto(int pedidosUltimos20Dias) { }
  // SemDesconto.java  (CODIGO "SemDesconto")  -> validate negative; return 0.0
  // PromocaoVerao.java (CODIGO "PromocaoVerao", PERCENTUAL 0.05) -> subtotal*PERCENTUAL
  // FidelidadeFrequente.java:
  @Component public class FidelidadeFrequente implements IEstrategiaCalculoDesconto {
      public static final String CODIGO = "Fidelidade7";
      private static final double PERCENTUAL = 0.07;
      private static final int MINIMO_PEDIDOS = 3;            // spec: MAIS de 3 pedidos / 20 dias
      @Override public String getCodigo(){ return CODIGO; }
      @Override public double calcular(double subtotalItens, ContextoDesconto contexto){
          if (subtotalItens < 0) throw new IllegalArgumentException("Subtotal nao pode ser negativo: " + subtotalItens);
          return (contexto != null && contexto.pedidosUltimos20Dias() > MINIMO_PEDIDOS)
              ? subtotalItens * PERCENTUAL : 0.0;
      }
  }
  ```
- **MIRROR:** STRATEGY_CLASS. Each strategy is `@Component`, `public static final String CODIGO`, `private static final` rate, validates negative subtotal (same message shape as Imposto).
- **GOTCHA G4:** `calcular` returns the **discount amount** (parallel to `ServicoImposto.calcularImposto` returning the tax amount), so UC6's `custoFinal = (Σ itens − desconto) + imposto` subtracts it directly. The loyalty count is **supplied** via `ContextoDesconto`, never queried (no pedidos table in P1).
- **VALIDATE:** Task 11 unit tests.

### Task 8 — `FabricaEstrategiaDesconto` (N3 — runtime, no config bean)
- **ACTION:** Create the factory.
- **IMPLEMENT:**
  ```java
  @Component
  public class FabricaEstrategiaDesconto {
      private final Map<String, IEstrategiaCalculoDesconto> porCodigo;
      public FabricaEstrategiaDesconto(List<IEstrategiaCalculoDesconto> estrategias){
          this.porCodigo = estrategias.stream()
              .collect(Collectors.toUnmodifiableMap(IEstrategiaCalculoDesconto::getCodigo, Function.identity()));
      }
      public IEstrategiaCalculoDesconto criar(String codigo){
          if (codigo == null || codigo.isBlank())
              throw new IllegalStateException("Politica de desconto corrente nao configurada");
          IEstrategiaCalculoDesconto e = porCodigo.get(codigo);
          if (e == null) throw new IllegalArgumentException(
              "Nenhuma estrategia registrada para a politica: " + codigo + ". Politicas disponiveis: " + porCodigo.keySet());
          return e;
      }
      public Set<String> codigosDisponiveis(){ return porCodigo.keySet(); }
  }
  ```
- **IMPORTS:** `java.util.{List,Map,Set}`, `java.util.function.Function`, `java.util.stream.Collectors`, `org.springframework.stereotype.Component`.
- **MIRROR:** FACTORY — identical Map build + error handling.
- **GOTCHA G5 (the trap):** do **NOT** inject an `ImpostoProperties`-style config bean and do **NOT** add a no-arg `criar()`. The active policy is owned by `DescontoRepository` and read at call time (UC4 switches it at runtime). Mirroring the config coupling would re-introduce startup-fixed behavior and break UC4. Method is `getCodigo()` here, not `getCodigoLei()`.
- **VALIDATE:** Task 11 `FabricaEstrategiaDescontoTest`.

### Task 9 — `DescontoRepository` (port) + `DescontoRepositoryJDBC` + `ServicoDesconto`
- **ACTION:** Create port, adapter, and `@Service` facade.
- **IMPLEMENT:**
  ```java
  // Dominio/Dados/DescontoRepository.java
  public interface DescontoRepository { String politicaCorrente(); void definePolitica(String codigo); }

  // Adaptadores/Dados/DescontoRepositoryJDBC.java
  @Component public class DescontoRepositoryJDBC implements DescontoRepository {
      public static final String CHAVE_CORRENTE = "desconto.corrente";
      private ConfiguracaoRepositoryJDBC configuracaoRepository;
      @Autowired public DescontoRepositoryJDBC(ConfiguracaoRepositoryJDBC c){ this.configuracaoRepository = c; }
      @Override public String politicaCorrente(){ return configuracaoRepository.valor(CHAVE_CORRENTE); }
      @Override public void definePolitica(String codigo){ configuracaoRepository.define(CHAVE_CORRENTE, codigo); }
  }

  // Dominio/Servicos/ServicoDesconto.java
  @Service public class ServicoDesconto {
      private final FabricaEstrategiaDesconto fabrica;
      private final DescontoRepository descontoRepository;
      public ServicoDesconto(FabricaEstrategiaDesconto fabrica, DescontoRepository descontoRepository){
          this.fabrica = fabrica; this.descontoRepository = descontoRepository;
      }
      public double calcularDesconto(double subtotalItens, ContextoDesconto contexto){
          return fabrica.criar(descontoRepository.politicaCorrente()).calcular(subtotalItens, contexto);
      }
      public Set<String> listarPoliticas(){ return fabrica.codigosDisponiveis(); }
      public String getPoliticaCorrente(){ return descontoRepository.politicaCorrente(); }
      public void definirPolitica(String codigo){
          fabrica.criar(codigo);                 // validates -> IllegalArgumentException (HTTP 400) if unknown
          descontoRepository.definePolitica(codigo);
      }
  }
  ```
- **MIRROR:** SERVICE_FACADE; port/adapter split exactly like `DescontoRepository ◄ DescontoRepositoryJDBC`.
- **GOTCHA:** `definirPolitica` validates **before** persisting (call `fabrica.criar(codigo)` first) so an unknown code never gets stored and surfaces as 400.
- **VALIDATE:** Task 11 `ServicoDescontoTest`; boot wiring (`contextLoads`).

### Task 10 — UC3/UC4 + Response + Presenter + `DescontoController` + `RestExceptionHandler`
- **ACTION:** Create the two UCs, the Response record, the Presenter record, the controller, and the advice (N2).
- **IMPLEMENT:**
  ```java
  // Aplicacao/Responses/PoliticasDescontoResponse.java
  public record PoliticasDescontoResponse(List<String> codigos, String corrente) { }
  // Aplicacao/ListarPoliticasDescontoUC.java  -> run(): new PoliticasDescontoResponse(List.copyOf(servico.listarPoliticas()), servico.getPoliticaCorrente())
  // Aplicacao/DefinirPoliticaDescontoUC.java   -> run(String codigo): servico.definirPolitica(codigo); return codigo;
  // Adaptadores/Apresentacao/Presenters/PoliticasDescontoPresenter.java
  public record PoliticasDescontoPresenter(List<String> politicas, String corrente) { }

  @RestController @RequestMapping("/descontos")
  public class DescontoController {
      private ListarPoliticasDescontoUC listarPoliticasUC;
      private DefinirPoliticaDescontoUC definirPoliticaUC;
      public DescontoController(ListarPoliticasDescontoUC a, DefinirPoliticaDescontoUC b){ this.listarPoliticasUC=a; this.definirPoliticaUC=b; }

      @GetMapping("/politicas") @CrossOrigin("*")
      public PoliticasDescontoPresenter listarPoliticas(){
          PoliticasDescontoResponse r = listarPoliticasUC.run();
          return new PoliticasDescontoPresenter(r.codigos(), r.corrente());
      }
      @PutMapping("/corrente/{codigo}") @CrossOrigin("*")
      public PoliticasDescontoPresenter definirPolitica(@PathVariable(value="codigo") String codigo){
          definirPoliticaUC.run(codigo);
          PoliticasDescontoResponse r = listarPoliticasUC.run();
          return new PoliticasDescontoPresenter(r.codigos(), r.corrente());
      }
  }
  ```
- **MIRROR:** USE_CASE, CONTROLLER, record-presenter (`CabecalhoCardapioPresenter`).
- **GOTCHA D4:** UC4 carries the code as a **`@PathVariable`** (`PUT /descontos/corrente/{codigo}`), not a JSON body — there is no `@RequestBody` precedent and discount codes are path-safe (no slashes). Diverges from the PRD's `{codigo}`-body suggestion; recorded as decision D4.
- **GOTCHA N2:** Without `RestExceptionHandler`, the `IllegalArgumentException` from an unknown code returns **HTTP 500**, failing the UC4 acceptance (400). The advice is mandatory.
- **VALIDATE:** `GET /descontos/politicas` → 3 codes + `SemDesconto`; `PUT /descontos/corrente/PromocaoVerao` → corrente switches; `PUT /descontos/corrente/Nada` → **400**.

### Task 11 — Unit tests (P1 subset; full comment-form drivers land in P6)
- **ACTION:** Create 5 test files (mirror Imposto tests).
- **IMPLEMENT:** strategy tests + factory test (POJO) + `ServicoDescontoTest` with a fake repo:
  ```java
  // ServicoDescontoTest.java — fake repo (N5), no Mockito
  private static class FakeDescontoRepository implements DescontoRepository {
      private String corrente;
      FakeDescontoRepository(String inicial){ this.corrente = inicial; }
      public String politicaCorrente(){ return corrente; }
      public void definePolitica(String codigo){ this.corrente = codigo; }
  }
  private ServicoDesconto servicoCom(String inicial){
      FabricaEstrategiaDesconto f = new FabricaEstrategiaDesconto(
          List.of(new SemDesconto(), new FidelidadeFrequente(), new PromocaoVerao()));
      return new ServicoDesconto(f, new FakeDescontoRepository(inicial));
  }
  ```
- **MIRROR:** TEST_HELPER_BUILDER; `DELTA=1e-9`; `assertEquals`/`assertSame`/`assertThrows`.
- **VALIDATE:** `./mvnw test` green (existing 4 Imposto test classes + 5 new + `contextLoads`).

---

## Testing Strategy

### Unit tests (new files under `src/test/.../Dominio/Servicos/`)
| Test file | Input | Expected | Edge? |
|---|---|---|---|
| `Desconto/SemDescontoTest` | `calcular(100, ctx(9))` | `0.0` (±DELTA) | `calcular(-1,..)`→`IllegalArgumentException` |
| `Desconto/PromocaoVeraoTest` | `calcular(100, ctx(0))` | `5.0` | negative→throws |
| `Desconto/FidelidadeFrequenteTest` | `ctx(4)`→`100`; `ctx(3)`→`100`; `ctx(0)` | `7.0`; `0.0`; `0.0` | boundary `>3` (3=no, 4=yes); negative→throws |
| `Desconto/FabricaEstrategiaDescontoTest` | `criar("PromocaoVerao")`; `criar("x")`; `criar("  ")` | `assertSame` strategy; `IllegalArgumentException`; `IllegalStateException` | unknown vs blank |
| `ServicoDescontoTest` | `servicoCom("SemDesconto")`; switch then recompute; `definirPolitica("x")` | amounts per policy; `getPoliticaCorrente()` reflects switch; unknown→`IllegalArgumentException` | fake repo (N5) |

### Edge Cases Checklist
- [x] Unknown discount code → `IllegalArgumentException` → **HTTP 400** (factory + advice)
- [x] Blank/null current policy → `IllegalStateException` → HTTP 500
- [x] Loyalty boundary: `pedidos == 3` → no discount; `== 4` → 7%
- [x] Negative subtotal → `IllegalArgumentException`
- [x] Unknown cardápio id in `PUT /cardapio/corrente/{id}` → 400
- [x] `GET /cardapio/corrente` route not shadowed by `/{id}` (G1)
- [x] DB reset on boot → defaults re-seeded (G2)

---

## Validation Commands

### Static / build
```bash
./mvnw -q compile            # EXPECT: BUILD SUCCESS, no errors
```
### Unit + integration
```bash
./mvnw -q test               # EXPECT: all pass (4 Imposto classes + 5 new + contextLoads)
```
### Boot + smoke (separate shell)
```bash
./mvnw spring-boot:run       # EXPECT: starts :8080; schema.sql+data.sql load; configuracao seeded
curl -s localhost:8080/cardapio/lista                  # unchanged (UC1)
curl -s -X PUT localhost:8080/cardapio/corrente/2      # UC2 -> "Cardapio de Setembro"
curl -s localhost:8080/cardapio/corrente               # UC5 -> same menu
curl -s localhost:8080/descontos/politicas             # UC3 -> {politicas:[...3...],corrente:"SemDesconto"}
curl -s -X PUT localhost:8080/descontos/corrente/PromocaoVerao   # UC4 -> corrente switched
curl -s -o /dev/null -w "%{http_code}" -X PUT localhost:8080/descontos/corrente/NaoExiste  # EXPECT: 400
```
### DB
H2 console `http://localhost:8080/h2` (`jdbc:h2:mem:pizzadb`, `sa`, no pwd) → `SELECT * FROM configuracao;` shows current keys.

---

## Acceptance Criteria
- [ ] `GET /descontos/politicas` lists ≥3 codes + the current one
- [ ] `PUT /descontos/corrente/{codigo}` switches + persists; unknown code → **400**
- [ ] `GET /cardapio/corrente` returns the menu set by `PUT /cardapio/corrente/{id}`; unknown id → 400
- [ ] Existing Imposto + menu behavior unchanged (`GET /cardapio/1`, `/cardapio/lista`)
- [ ] `./mvnw test` green; no type/compile errors
- [ ] New strategies behind `IEstrategiaCalculoDesconto` (≥3); factory auto-discovers them

## Completion Checklist
- [ ] Follows STRATEGY_CLASS / FACTORY / SERVICE_FACADE / USE_CASE / CONTROLLER patterns
- [ ] Error handling: `IllegalArgumentException` (bad input/unknown) + `IllegalStateException` (config), mapped by advice
- [ ] Tests follow TEST_HELPER_BUILDER, `DELTA`, Portuguese names; fake repo (no Mockito)
- [ ] No mutation of shared state; records for value objects/DTOs
- [ ] No hardcoded values beyond named constants (`CODIGO`, `PERCENTUAL`, `CHAVE_CORRENTE`)
- [ ] PRD §7/§8/§9 tables + §13 decisions + §14 Progress Log updated
- [ ] Self-contained — implemented without further codebase searching

---

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **No write-path precedent** — `MERGE`/`update` new | Med | High | N1 documented with exact H2 `MERGE` syntax + Task 2 isolates it in one helper |
| **400 contract** naively returns 500 | High (if advice skipped) | High | N2 `@RestControllerAdvice` is a mandatory task; explicit curl `%{http_code}` check |
| **Factory config-coupling trap** (G5) | Med | High | Plan explicitly forbids the properties bean + no-arg `criar()`; current code from repo |
| **Loyalty data unavailable** (G4) | High | Med | `ContextoDesconto` param supplied by caller; real counting deferred to P2 (NOT Building) |
| **Route shadowing** `/corrente` vs `/{id}` (G1) | Low | Med | Literal-over-template precedence; `long` can't bind "corrente"; smoke test covers it |
| **Global advice changes existing 500→400** | Low | Low | Intentional & semantically correct (bad input = 400); recorded as decision D5 |
| DB reset wipes runtime switches (G2) | Certain | Low | Accepted for P1; defaults re-seeded; revisit in P3 (JPA) |

## Notes — Decisions made by this plan (record into PRD §13)
- **D2:** "corrente" state persists only within a JVM session (`mode: always` reloads `data.sql`); defaults re-seeded. Accept for P1.
- **D3:** Single shared `ConfiguracaoRepositoryJDBC` (k/v) owns the write idiom; `CardapioRepositoryJDBC` and `DescontoRepositoryJDBC` delegate (DRY). It's an adapter-layer infra helper, not a domain port.
- **D4:** UC4 uses `PUT /descontos/corrente/{codigo}` (path var), not a JSON body — no `@RequestBody` precedent; codes are path-safe.
- **D5:** Global `@RestControllerAdvice` maps `IllegalArgumentException`→400, `IllegalStateException`→500 (affects all endpoints; desirable).
- **Resolves PRD open questions:** #1 (corrente vs DB reset → D2) and #4 (discount `calcular` signature → `(double subtotal, ContextoDesconto)`).

> **Confidence: 9/10** for single-pass implementation. The one residual unknown is H2 `MERGE`
> exact behavior under `spring.sql.init` — de-risked because defaults are pre-seeded, so
> `define(...)` always hits an existing row, and `MERGE` also covers the INSERT path.
