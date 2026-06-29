# Plan: P5 — Autenticação & Autorização (token filter leve + admin via config)

## Summary
Enforce login + role-based authorization on the pizzaria backend **without adding Spring Security**. Today every endpoint is open. This plan adds a tiny hand-rolled auth layer: `POST /auth/login` issues an opaque token (UUID) stored in memory against the caller's identity + role; a Spring MVC `HandlerInterceptor` validates the `Authorization: Bearer <token>` header on protected routes, returns **401** when the token is missing/unknown and **403** when an admin-only route is hit by a customer. Customers authenticate against `ClienteRepository` (role `CLIENTE`); a single **config-based admin** (`app.admin.*`) gets role `ADMIN`.

## User Story
As the pizzaria operator, I want admin-only and customer-only endpoints to require the correct authenticated role, so that anonymous callers cannot submit/cancel/pay orders or change menu/discount policy, satisfying the spec's `(Adm)`/`(A)` actor distinction.

## Problem → Solution
**Current:** `POST /pedidos`, `POST /pedidos/{id}/pagar`, `PUT /descontos/corrente/{codigo}`, etc. accept any unauthenticated request. `POST /auth/login` is a stateless credential check that issues nothing and protects nothing. No role concept exists.
**Desired:** Login issues a Bearer token; an interceptor rejects unauthenticated calls (401) and wrong-role calls (403). No new Maven dependency — pure Spring MVC. Keeps auth as a clean adapter concern in the project's deliberately minimal hand-rolled style.

## Metadata
- **Complexity**: Medium (8 created/updated source files; no new framework/dependency)
- **Source PRD**: `.claude/PRPs/tele-pizza-backend.prd.md`
- **PRD Phase**: P5 (24/06/2026) — Autenticação · Owner: Pessoa 2 · resolves OQ#5
- **Estimated Files**: 7 created, 3 updated (+ PRD)

> **Approach change (vs first draft):** the Spring Security + HTTP Basic design was tuned down to this **lightweight token interceptor** per user direction ("the simpler one"). Role model unchanged: **config-based single admin**.

---

## UX Design

### Before
```
Client ─────────────────────────────► POST /pedidos            → 201 (anyone)
Client ─────────────────────────────► PUT /descontos/corrente  → 200 (anyone)
Client ──(POST /auth/login ok)──────► (token? none) ── nothing protected
```

### After
```
Anon ──► POST /auth/login {email,senha} ──► 200 { token, papel, cpf, nome, email }   (UC12)
Anon ──► POST /pedidos                  ──► 401 (no/!valid token)
Anon ──► POST /clientes (cadastro)      ──► 201 (public, UC11)

Cliente: Authorization: Bearer <token-CLIENTE>
   GET  /cardapio/corrente   → 200
   POST /pedidos             → 201
   GET  /descontos/politicas → 403 (admin-only)

Admin (login admin@pizzaria.com): Authorization: Bearer <token-ADMIN>
   PUT  /descontos/corrente/PromocaoVerao → 200
   PUT  /cardapio/corrente/1              → 200
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| `POST /auth/login` (UC12) | returns `{cpf,nome,email}` | returns `{token,papel,cpf,nome,email}` | now issues a Bearer token; admin can also log in here |
| Protected call w/o token | 200/201 | **401** | interceptor `preHandle` |
| Customer calls admin route | 200 | **403** | token `papel != ADMIN` |
| Auth transport | none | `Authorization: Bearer <token>` per request | opaque UUID, in-memory store (resets on restart — acceptable, like the DB) |
| Admin identity | n/a | `app.admin.email` / `app.admin.senha` | admin is **not** a `clientes` row |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `src/main/java/.../Adaptadores/Config/CorsConfig.java` | all | The `WebMvcConfigurer` pattern to mirror for registering the interceptor (`addInterceptors`) |
| P0 | `src/main/java/.../Adaptadores/Config/ImpostoProperties.java` | all | `@ConfigurationProperties` pattern → `AdminProperties`. **Precedent:** `FabricaEstrategiaImposto` (Dominio.Servicos) already injects this Adaptadores.Config bean — so injecting `AdminProperties` into `LoginUC` (Aplicacao) is consistent, not a new deviation |
| P0 | `src/main/java/.../Aplicacao/LoginUC.java` | all | UC to extend: add token issuance + admin branch |
| P0 | `src/main/java/.../Aplicacao/Responses/LoginResponse.java` | all | Add `token` + `papel`; built from `Cliente` today |
| P0 | `src/main/java/.../Dominio/Servicos/ServicoCliente.java` | 68–81 | `autenticar(email,senha)` → throws `CredenciaisInvalidasException` (→401 via advice); customer branch reuses this unchanged |
| P0 | `src/main/java/.../Dominio/Entidades/Cliente.java` | 51–73 | `getCpf/getNome/getEmail` used to fill `LoginResponse` |
| P1 | `src/main/java/.../Adaptadores/Apresentacao/RestExceptionHandler.java` | 35–39 | `CredenciaisInvalidasException → 401` already wired; the interceptor's own 401/403 are written directly to the response (it runs before `@RestControllerAdvice`) |
| P1 | `src/main/java/.../Adaptadores/Apresentacao/PedidoController.java` | 53–87 | `/pedidos/**` routes → `authenticated` |
| P1 | `src/main/resources/application.yaml` | all | Add `app.admin.*`; H2 console at `/h2`; `imposto.lei-vigente` shows yaml shape |
| P1 | `src/main/resources/data.sql` | clientes block | Seeded CLIENTE fixtures: `huguinho.pato@email.com`/`senha123` (cpf 9001), `zezinho.pato@email.com`/`senha456` (cpf 9002) |
| P2 | `src/test/java/.../Adaptadores/Dados/PedidoRepositoryEntreguesTest.java` | 1–30 | `@SpringBootTest` shape + in-file `Casos de teste` block for the new IT |

## External Documentation
| Topic | Source | Key Takeaway |
|---|---|---|
| Spring MVC `HandlerInterceptor` | Spring ref | `preHandle` returns `false` to short-circuit; write status to `HttpServletResponse` directly (no exception advice runs for non-handler rejections) |
| `WebMvcConfigurer#addInterceptors` | Spring ref | register via `InterceptorRegistry`; `addPathPatterns`/`excludePathPatterns` use Ant patterns; runs **after** CORS preflight handling for properly-configured CORS |
No external research needed beyond confirming the interceptor/registration API — everything else uses established internal patterns.

```
KEY_INSIGHT: A HandlerInterceptor rejection writes the status itself; @RestControllerAdvice does NOT intercept it.
APPLIES_TO: AutenticacaoInterceptor 401/403 paths
GOTCHA: set response.setStatus(...) AND return false; optionally write a short body. Do not throw — that would 500.

KEY_INSIGHT: CORS preflight OPTIONS may still reach the interceptor.
APPLIES_TO: AutenticacaoInterceptor.preHandle
GOTCHA: allow OPTIONS through (return true) so browser preflight isn't 401'd.
```

---

## Patterns to Mirror

### CONFIGURATION_PROPERTIES (for AdminProperties)
```java
// SOURCE: Adaptadores/Config/ImpostoProperties.java:10-22
@Component
@ConfigurationProperties(prefix = "imposto")
public class ImpostoProperties {
    private String leiVigente;
    public String getLeiVigente() { return leiVigente; }
    public void setLeiVigente(String leiVigente) { this.leiVigente = leiVigente; }
}
```

### WEBMVCCONFIGURER (for WebConfig that registers the interceptor)
```java
// SOURCE: Adaptadores/Config/CorsConfig.java:10-23
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    private final String[] origensPermitidas;
    public CorsConfig(@Value("${app.cors.allowed-origins:*}") String[] origensPermitidas) { ... }
    @Override public void addCorsMappings(CorsRegistry registry) { ... }
}
```

### USE_CASE (LoginUC shape — @Component, single run())
```java
// SOURCE: Aplicacao/LoginUC.java:11-28
@Component
public class LoginUC {
    private final ServicoCliente servicoCliente;
    @Autowired public LoginUC(ServicoCliente servicoCliente){ this.servicoCliente = servicoCliente; }
    public LoginResponse run(LoginRequest request){
        if (request == null) throw new IllegalArgumentException("Requisicao de login nao pode ser nula.");
        Cliente cliente = servicoCliente.autenticar(request.email(), request.senha());
        return LoginResponse.de(cliente);
    }
}
```

### TEST_STRUCTURE (integration test, with the spec's in-file `Casos de teste` block — §6.8)
```java
// SOURCE: src/test/.../Adaptadores/Dados/PedidoRepositoryEntreguesTest.java:1-30 (shape)
@SpringBootTest
class PedidoRepositoryEntreguesTest {
    // ───────────────────────────────────────────────────────────────
    // Casos de teste (driver de integracao):
    //  1. ...
    // ───────────────────────────────────────────────────────────────
    @Autowired ...;  @Test void ...(){ ... }
}
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `Dominio/Entidades/Papel.java` | CREATE | enum `{ADMIN, CLIENTE}` — role value (domain concept) |
| `Aplicacao/Portas/Sessao.java` | CREATE | record `(String principal, Papel papel)` — the resolved caller |
| `Aplicacao/Portas/RepositorioSessao.java` | CREATE | port: `String criar(String principal, Papel papel)`, `Sessao validar(String token)` (DIP — impl in Adaptadores) |
| `Adaptadores/Seguranca/RepositorioSessaoMemoria.java` | CREATE | `@Component` in-memory `ConcurrentHashMap<token,Sessao>`, `UUID.randomUUID()` tokens |
| `Adaptadores/Config/AdminProperties.java` | CREATE | bind `app.admin.email`/`app.admin.senha` (mirror `ImpostoProperties`) |
| `Adaptadores/Seguranca/AutenticacaoInterceptor.java` | CREATE | `HandlerInterceptor` — 401/403 + sets `sessao` request attribute |
| `Adaptadores/Config/WebConfig.java` | CREATE | `WebMvcConfigurer` registering the interceptor (path matrix) |
| `Aplicacao/LoginUC.java` | UPDATE | add admin branch + token issuance via `RepositorioSessao`; inject `AdminProperties` |
| `Aplicacao/Responses/LoginResponse.java` | UPDATE | add `token` + `papel` fields |
| `src/main/resources/application.yaml` | UPDATE | add `app.admin.email`/`app.admin.senha` |
| `src/test/.../Adaptadores/Apresentacao/AutenticacaoIntegrationTest.java` | CREATE | MockMvc token-flow matrix, with `Casos de teste` block |
| `.claude/PRPs/tele-pizza-backend.prd.md` | UPDATE | P5 status/notes + Progress Log + §13 decision on completion |

## NOT Building
- **Spring Security / HTTP Basic** — replaced by the hand-rolled interceptor (the whole point of this tune-down).
- **Password hashing** — plaintext per PRD D16 (`senha.equals(...)` reused from `ServicoCliente`).
- **JWT / signed tokens / expiry / refresh / logout** — opaque UUID, no expiry; in-memory store resets on restart (consistent with the DB-resets-on-boot posture). A `/auth/logout` is optional and out of scope.
- **`papel` column / roles table / multiple admins** — single config admin (no schema change).
- **Per-customer ownership checks** on `/pedidos/*` and UC10 — any authenticated `CLIENTE` may call customer routes (deferred; PRD already notes UC10 per-customer scoping deferred).
- **Locking down H2 console / DEBUG logging** — left open for the demo (PRD OQ#6 e-sec); `/h2/**` is excluded from the interceptor.

---

## Step-by-Step Tasks

### Task 1: Papel enum
- **ACTION**: Create `Dominio/Entidades/Papel.java`.
- **IMPLEMENT**: `public enum Papel { ADMIN, CLIENTE }`.
- **MIRROR**: existing domain enum `Pedido.Status` style (plain enum, no framework).
- **VALIDATE**: compiles; referenced by Sessao/LoginResponse/interceptor.

### Task 2: Sessao + RepositorioSessao (port)
- **ACTION**: Create `Aplicacao/Portas/Sessao.java` and `Aplicacao/Portas/RepositorioSessao.java`.
- **IMPLEMENT**:
  ```java
  public record Sessao(String principal, Papel papel) {}      // principal = cpf (CLIENTE) ou email (ADMIN)

  public interface RepositorioSessao {
      String criar(String principal, Papel papel);            // returns the opaque token
      Sessao validar(String token);                           // null if unknown
  }
  ```
- **MIRROR**: port-interface convention (like `Dominio.Dados` ports — domain language, no Spring). New `Aplicacao/Portas` package is the natural home (a use-case-level port).
- **IMPORTS**: `Papel`.
- **GOTCHA**: `validar` returns `null` on miss (matches `ClienteRepository.recuperaPorEmail` null-convention), interceptor maps null→401.
- **VALIDATE**: compiles.

### Task 3: RepositorioSessaoMemoria (adapter)
- **ACTION**: Create `Adaptadores/Seguranca/RepositorioSessaoMemoria.java`.
- **IMPLEMENT**:
  ```java
  @Component
  public class RepositorioSessaoMemoria implements RepositorioSessao {
      private final Map<String, Sessao> sessoes = new ConcurrentHashMap<>();
      @Override public String criar(String principal, Papel papel) {
          String token = UUID.randomUUID().toString();
          sessoes.put(token, new Sessao(principal, papel));
          return token;
      }
      @Override public Sessao validar(String token) {
          return token == null ? null : sessoes.get(token);
      }
  }
  ```
- **MIRROR**: `@Component` adapter implementing an inner-layer port (like `*RepositoryJPA`).
- **IMPORTS**: `java.util.{Map,UUID}`, `java.util.concurrent.ConcurrentHashMap`, `org.springframework.stereotype.Component`, the port/`Sessao`/`Papel`.
- **GOTCHA**: `ConcurrentHashMap` — the Cozinha/Entrega sims run on other threads, and login is concurrent. No expiry by design.
- **VALIDATE**: bean discovered (component scan `com.bcopstein`).

### Task 4: AdminProperties
- **ACTION**: Create `Adaptadores/Config/AdminProperties.java`.
- **IMPLEMENT**: `@Component @ConfigurationProperties(prefix = "app.admin")` with `String email; String senha;` + getters/setters.
- **MIRROR**: `ImpostoProperties`.
- **GOTCHA**: prefix `app.admin` ↔ yaml `app.admin.email`/`app.admin.senha`; env `APP_ADMIN_SENHA` overrides for a real demo.
- **VALIDATE**: binds to yaml from Task 8.

### Task 5: LoginUC — admin branch + token issuance
- **ACTION**: Update `Aplicacao/LoginUC.java`.
- **IMPLEMENT**:
  ```java
  @Component
  public class LoginUC {
      private final ServicoCliente servicoCliente;
      private final AdminProperties admin;
      private final RepositorioSessao repositorioSessao;
      @Autowired public LoginUC(ServicoCliente servicoCliente, AdminProperties admin, RepositorioSessao repositorioSessao) {
          this.servicoCliente = servicoCliente; this.admin = admin; this.repositorioSessao = repositorioSessao;
      }
      public LoginResponse run(LoginRequest request) {
          if (request == null) throw new IllegalArgumentException("Requisicao de login nao pode ser nula.");
          // Admin via config (nao e um cliente do banco).
          if (admin.getEmail() != null && admin.getEmail().equalsIgnoreCase(request.email())) {
              if (!admin.getSenha().equals(request.senha()))
                  throw new CredenciaisInvalidasException("Credenciais inválidas.");
              String token = repositorioSessao.criar(admin.getEmail(), Papel.ADMIN);
              return new LoginResponse(token, null, "Administrador", admin.getEmail(), Papel.ADMIN.name());
          }
          // Cliente via dominio (reaproveita autenticar -> CredenciaisInvalidasException -> 401).
          Cliente c = servicoCliente.autenticar(request.email(), request.senha());
          String token = repositorioSessao.criar(c.getCpf(), Papel.CLIENTE);
          return new LoginResponse(token, c.getCpf(), c.getNome(), c.getEmail(), Papel.CLIENTE.name());
      }
  }
  ```
- **MIRROR**: USE_CASE pattern; `AdminProperties` injection mirrors `FabricaEstrategiaImposto(ImpostoProperties)` precedent.
- **IMPORTS**: `CredenciaisInvalidasException`, `AdminProperties`, `RepositorioSessao`, `Papel`, existing `Cliente`/`ServicoCliente`/`LoginRequest`/`LoginResponse`.
- **GOTCHA**: admin uses the **same** `CredenciaisInvalidasException` (uniform 401, no enumeration). Order: admin-email check first so a customer can never shadow the admin address.
- **VALIDATE**: login tests in Task 9.

### Task 6: LoginResponse — add token + papel
- **ACTION**: Update `Aplicacao/Responses/LoginResponse.java`.
- **IMPLEMENT**:
  ```java
  public record LoginResponse(String token, String cpf, String nome, String email, String papel) {}
  ```
  Remove the old `de(Cliente)` factory (callers now construct directly in `LoginUC`). **`senha` still never present.**
- **GOTCHA**: this changes the record's shape — grep for `LoginResponse.de(` / field access and fix (only `LoginUC` should reference it; verify no test asserts the old shape — if one does, update it).
- **VALIDATE**: compiles; `senha` absent.

### Task 7: AutenticacaoInterceptor
- **ACTION**: Create `Adaptadores/Seguranca/AutenticacaoInterceptor.java implements HandlerInterceptor`.
- **IMPLEMENT**:
  ```java
  @Component
  public class AutenticacaoInterceptor implements HandlerInterceptor {
      private final RepositorioSessao repositorioSessao;
      public AutenticacaoInterceptor(RepositorioSessao r) { this.repositorioSessao = r; }

      @Override
      public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
          if ("OPTIONS".equalsIgnoreCase(req.getMethod())) return true;      // CORS preflight
          String header = req.getHeader("Authorization");
          String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
          Sessao sessao = repositorioSessao.validar(token);
          if (sessao == null) { res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Autenticacao requerida."); return false; }
          if (exigeAdmin(req.getRequestURI()) && sessao.papel() != Papel.ADMIN) {
              res.sendError(HttpServletResponse.SC_FORBIDDEN, "Acesso restrito ao administrador."); return false;
          }
          req.setAttribute("sessao", sessao);
          return true;
      }
      // ADMIN: /descontos/** e /cardapio/** (exceto o GET /cardapio/corrente, que e do cliente).
      private boolean exigeAdmin(String path) {
          if (path.startsWith("/descontos")) return true;
          if (path.equals("/cardapio/corrente")) return false;   // UC5 (cliente)
          return path.startsWith("/cardapio");                   // UC1/UC2 + GET /cardapio/{id}
      }
  }
  ```
- **MIRROR**: `@Component` adapter depending on an inner port.
- **IMPORTS**: `org.springframework.web.servlet.HandlerInterceptor`, `jakarta.servlet.http.{HttpServletRequest,HttpServletResponse}`, `org.springframework.stereotype.Component`, port/`Sessao`/`Papel`.
- **GOTCHA**: `/pedidos/**` and `GET /cardapio/corrente` need only a valid token (any role) — handled by "not exigeAdmin → just authenticated". `sendError` + `return false` (don't throw). `req.getRequestURI()` is context-relative ("/pedidos/1/status").
- **VALIDATE**: matrix tests in Task 9.

### Task 8: WebConfig (register interceptor) + application.yaml
- **ACTION**: Create `Adaptadores/Config/WebConfig.java`; append admin creds to `application.yaml`.
- **IMPLEMENT (WebConfig)**:
  ```java
  @Configuration
  public class WebConfig implements WebMvcConfigurer {
      private final AutenticacaoInterceptor autenticacaoInterceptor;
      public WebConfig(AutenticacaoInterceptor a) { this.autenticacaoInterceptor = a; }
      @Override public void addInterceptors(InterceptorRegistry registry) {
          registry.addInterceptor(autenticacaoInterceptor)
              .addPathPatterns("/**")
              .excludePathPatterns("/", "/auth/login", "/clientes", "/h2/**", "/error");
      }
  }
  ```
- **IMPLEMENT (yaml — sibling of `imposto:`)**:
  ```yaml
  app:
    admin:
      email: "admin@pizzaria.com"
      senha: "admin123"        # demo apenas; sobrescreva via APP_ADMIN_SENHA
  ```
- **MIRROR**: `CorsConfig` (WebMvcConfigurer); `imposto` yaml block.
- **IMPORTS**: `org.springframework.context.annotation.Configuration`, `org.springframework.web.servlet.config.annotation.{WebMvcConfigurer,InterceptorRegistry}`.
- **GOTCHA**: keep `CorsConfig` separate (CORS) from `WebConfig` (interceptor) — two focused `WebMvcConfigurer`s coexist fine. `POST /clientes` is the only `/clientes` route, so excluding the path (not method) is safe.
- **VALIDATE**: public routes stay open; protected routes 401 without token.

### Task 9: AutenticacaoIntegrationTest (MockMvc)
- **ACTION**: Create `src/test/.../Adaptadores/Apresentacao/AutenticacaoIntegrationTest.java` (`@SpringBootTest @AutoConfigureMockMvc`).
- **IMPLEMENT**: with an in-file `Casos de teste` block (§6.8). Helper: POST `/auth/login` with JSON, parse `token` from the response. Cases:
  - `GET /pedidos/1/status` no token → `401`
  - login `huguinho.pato@email.com:senha123` → 200, body has `token`, `papel:"CLIENTE"`, no `senha`
  - customer token → `GET /cardapio/corrente` → not 401/403
  - customer token → `GET /pedidos/1/status` → not 401/403 (404 ok)
  - customer token → `GET /descontos/politicas` → `403`
  - login admin `admin@pizzaria.com:admin123` → 200, `papel:"ADMIN"`
  - admin token → `GET /descontos/politicas` → `200`
  - admin token → `PUT /cardapio/corrente/1` → not 401/403
  - bad token `Bearer nope` → `401`
  - login wrong password → `401` (`CredenciaisInvalidasException`)
  - `POST /clientes` (no token, valid body) → not 401 (public); `POST /auth/login` (no token) → not 401
- **MIRROR**: TEST_STRUCTURE; seeded customers from `data.sql`.
- **IMPORTS**: `@SpringBootTest`, `@AutoConfigureMockMvc`, `MockMvc`, static `MockMvcRequestBuilders.*`/`MockMvcResultMatchers.*`; `com.fasterxml.jackson.databind.ObjectMapper` (already on classpath) to read the token.
- **GOTCHA**: assert customer-on-/pedidos is **not** 401/403 (decouple from whether pedido 1 exists). Token store is in-memory in the same context — login then reuse the returned token within the test.
- **VALIDATE**: class green under JDK 21.

### Task 10: Regression + PRD update
- **ACTION**: Run full suite (expect the 135 existing still green — none drive a secured MVC endpoint). Update PRD.
- **IMPLEMENT**: PRD P5 `🟡 in-progress → ✅` (once verified) keeping the plan link; flip UC-table notes for protected UCs; append a Progress Log entry; record §13 decision (OQ#5 → token interceptor + config admin + opaque UUID, in-memory) + the authorization matrix.
- **GOTCHA**: the new IT also advances P6 (Pessoa 2 auth driver with comment-form spec).
- **VALIDATE**: full suite green; live smoke below.

---

## Testing Strategy

### Integration Tests
| Test | Input | Expected | Edge? |
|---|---|---|---|
| anon protected | `GET /pedidos/1/status` no token | 401 | — |
| login customer | `POST /auth/login` 9001 creds | 200 + token, papel CLIENTE, no senha | — |
| customer UC5 | customer token → `GET /cardapio/corrente` | not 401/403 | — |
| customer UC6+ | customer token → `GET /pedidos/1/status` | not 401/403 (404 ok) | order absent |
| customer→admin | customer token → `GET /descontos/politicas` | 403 | role boundary |
| login admin | `POST /auth/login` admin creds | 200 + papel ADMIN | — |
| admin UC3 | admin token → `GET /descontos/politicas` | 200 | — |
| admin UC2 | admin token → `PUT /cardapio/corrente/1` | not 401/403 | — |
| bad token | `Bearer nope` → protected | 401 | — |
| wrong password | login wrong senha | 401 | enumeration-safe |
| public open | `POST /clientes`, `POST /auth/login` no token | not 401 | — |

### Edge Cases Checklist
- [x] Missing/garbage token → 401
- [x] Valid token, wrong role → 403
- [x] Public routes unaffected (`/`, `/auth/login`, `/clientes`, `/h2`)
- [x] `GET /cardapio/corrente` is customer (not admin-only)
- [ ] CORS preflight `OPTIONS` on a protected route → passes (interceptor lets OPTIONS through)

---

## Validation Commands

### Build + tests
```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw -q -o test
```
EXPECT: BUILD SUCCESS; **≥136** tests (135 + new IT cases), 0 failures/errors. No new dependency → `-o` works offline.

### Live smoke (separate shell; free port)
```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 SERVER_PORT=8099 ./mvnw spring-boot:run
# anon protected
curl -s -o /dev/null -w "%{http_code}\n" localhost:8099/pedidos/1/status                       # 401
# customer login -> capture token
TOK=$(curl -s -X POST localhost:8099/auth/login -H 'Content-Type: application/json' \
      -d '{"email":"huguinho.pato@email.com","senha":"senha123"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $TOK" localhost:8099/cardapio/corrente    # 200
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $TOK" localhost:8099/descontos/politicas  # 403
# admin login -> token
ADM=$(curl -s -X POST localhost:8099/auth/login -H 'Content-Type: application/json' \
      -d '{"email":"admin@pizzaria.com","senha":"admin123"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $ADM" localhost:8099/descontos/politicas  # 200
curl -s -o /dev/null -w "%{http_code}\n" localhost:8099/                                                      # 200 (public)
```
EXPECT: 401 / 200 / 403 / 200 / 200.

### Manual Validation
- [ ] `POST /auth/login` returns a token + `papel`, never `senha`; wrong password → 401.
- [ ] New cadastro (`POST /clientes`) then login → token works on `/pedidos`.

---

## Acceptance Criteria
- [ ] Anonymous/invalid-token calls to `/pedidos/**`, `GET /cardapio/corrente`, `/cardapio/**` (admin), `/descontos/**` → 401.
- [ ] Customer token blocked from admin routes → 403; admin token allowed → 200.
- [ ] `/`, `POST /auth/login`, `POST /clientes`, `/h2/**` remain public.
- [ ] `POST /auth/login` returns `{token, papel, ...}` (no `senha`).
- [ ] Full suite green under JDK 21 (no regression on the 135 existing); new IT carries a `Casos de teste` block.

## Completion Checklist
- [ ] Follows `@ConfigurationProperties` / `WebMvcConfigurer` / UC / port-adapter patterns.
- [ ] `RepositorioSessao` is a port (DIP); impl in Adaptadores.
- [ ] Uniform auth-failure message (no enumeration).
- [ ] No hardcoded secret in source (admin creds in yaml, env-overridable).
- [ ] No new Maven dependency.
- [ ] PRD §9/§13/§14 updated; OQ#5 resolved.

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Interceptor matrix wrong (UC5 admin-only, or a public route locked) | Medium | UC breaks | `exigeAdmin` rule + exclude list, covered by tests |
| `LoginResponse` shape change breaks a caller/test | Medium | Compile/test fail | grep `LoginResponse.de(`/usages; only `LoginUC` should reference it |
| Interceptor 401s CORS preflight | Low | Browser clients fail | `OPTIONS → return true` |
| Throwing in `preHandle` → 500 instead of 401/403 | Low | Wrong status | use `res.sendError(...)` + `return false`, never throw |
| In-memory tokens lost on restart | Expected | Re-login needed | by design (matches DB-resets posture); documented |

## Notes
- **Decisions (record in PRD §13 on impl):** OQ#5 resolved → **lightweight token interceptor** (no Spring Security), **opaque UUID Bearer tokens**, **in-memory `RepositorioSessao`** (no expiry, resets on boot), **config-based single admin** (`app.admin.*`), uniform `CredenciaisInvalidasException`→401, plaintext senha (D16). UC12 `/auth/login` now issues a token and also serves admin login.
- **Authorization matrix (source of truth):** public = `/`, `POST /auth/login`, `POST /clientes`, `/h2/**`, `/error`; ADMIN = `/descontos/**` + `/cardapio/**` (except `GET /cardapio/corrente`); authenticated(any) = `GET /cardapio/corrente` (UC5) + `/pedidos/**` (UC6–UC10).
- **Layering note:** `LoginUC` (Aplicacao) injects `AdminProperties` (Adaptadores.Config) — consistent with the existing `FabricaEstrategiaImposto`←`ImpostoProperties` precedent; `RepositorioSessao` is a proper Aplicacao port so the token store stays inverted.
- **Follow-ups (out of scope):** per-customer ownership scoping; token expiry/logout; password hashing; lock down H2 + DEBUG logging (OQ#6 e-sec).
- **P6 bonus:** `AutenticacaoIntegrationTest` is also Pessoa 2's auth driver — give it the comment-form `Casos de teste` block.
