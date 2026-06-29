# Tele Pizza — Backend

Backend de uma pizzaria com pedidos online (Trabalho Final FDS). REST API em **Java 21 / Spring Boot 3.5**
seguindo **Clean Architecture** (Domínio → Aplicação → Adaptadores), cobrindo o ciclo completo do pedido
(cardápio, descontos, estoque, pagamento, cozinha/entrega simuladas) com persistência **JPA/H2** e autenticação por token.

## Requisitos

- **JDK 21** (o build falha em versões anteriores). O wrapper Maven (`./mvnw`) já está incluso — não precisa instalar o Maven.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # ajuste para o seu caminho do JDK 21
```

## Compilar

```bash
./mvnw compile
```

## Testar

```bash
./mvnw test
```

Roda os testes unitários e de integração (JUnit 5 + Spring Test) e gera o relatório de cobertura JaCoCo em
`target/site/jacoco/index.html`.

## Executar

```bash
./mvnw spring-boot:run
```

A aplicação sobe em **http://localhost:8080** (use `SERVER_PORT=8099 ./mvnw spring-boot:run` se a porta estiver ocupada).

- Healthcheck: `curl localhost:8080/` → `Bem Vindo a Pizzaria ECA`
- Console H2: http://localhost:8080/h2 — JDBC `jdbc:h2:mem:pizzadb`, usuário `sa`, sem senha
- O banco é **in-memory** e é recriado a cada boot a partir de `schema.sql` + `data.sql`.

## Demonstração

Rode o script abaixo para verificar um demo pré-pronto do projeto:

```bash
./mvnw spring-boot:run          # num terminal
./demo.sh                       # noutro terminal (BASE=http://localhost:8099 ./demo.sh para outra porta)
```
