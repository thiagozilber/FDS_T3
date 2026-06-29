# Request Flow — `POST /pedidos`

A walk through one vertical slice of the Tele Pizza backend, drawn to make the
**layer boundaries** (Clean Architecture) and the **port/adapter crossings**
(Hexagonal) visible. Pacote raiz: `com.bcopstein.ex4_lancheriaddd_v1`.

## Diagram

```
                          HTTP REQUEST: POST /pedidos
                          Authorization: Bearer <token>
                          { clienteCpf, enderecoEntrega, itens[] }
                                       │
═══════════════════════════════════════▼══════════════════════════════════ ADAPTADORES
                                                                         (outer ring)
   ┌──────────────────────────────────────────────────────────────────┐
   │  AutenticacaoInterceptor          validates token + papel        │
   │     │  401 if no/invalid token · 403 if wrong role               │
   │     ▼                                                            │
   │  PedidoController  (@RestController)                             │
   │     • @Valid on SubmeterPedidoRequest                            │
   │     • translates Domain → Response → Presenter                   │
   └─────┬──────────────────────────────────────────────────▲─────────┘
         │ calls .run(req)                 builds Presenter │ (JSON out)
═════════▼══════════════════════════════════════════════════╪════════════ APLICACAO
         │                                                  │      (use-case ring)
   ┌─────▼──────────────────────────────────────────────────┴─────────┐
   │  SubmeterPedidoParaAprovacaoUC.run(SubmeterPedidoRequest)        │ 
   │     • validates: non-empty, qty > 0                              │
   │     • aggregates qty per produto (avoids PK dup)                 │
   │     • rehydrates Produto via ProdutosRepository (port)           │
   │     • returns SubmeterPedidoResponse (DTO)                       │
   └─────┬─────────────────────────────────────────────────▲──────────┘
         │ servicoPedido.submeter(cliente, endereco, itens)│ Pedido
═════════▼═════════════════════════════════════════════════╪═════════════ DOMINIO
         │                                                 │   (inner ring — pure)
   ┌─────▼─────────────────────────────────────────────────┴──────────┐
   │  ServicoPedido.submeter(...)                                     │
   │     │                                                            │
   │     ├──► ServicoEstoque ........ verify + decrement stock        │
   │     │        └─ RECUSADO if insufficient                         │
   │     │                                                            │
   │     ├──► ServicoDesconto ....... strategy: PromocaoVerao / ...   │
   │     ├──► ServicoImposto ........ strategy: Lei0412de2022 / ...   │
   │     │        valorCobrado = (Σitens − desconto) + imposto        │
   │     │                                                            │
   │     ├──► registrarTransicao(NOVO → APROVADO)  [sole writer]      │
   │     │                                                            │
   │     ▼                                                            │
   │   PORTS (interfaces owned by the domain):                        │
   │     PedidoRepository.salvar(pedido)   HistoricoStatusRepository  │
   └─────┬──────────────────────────────────────┬─────────────────────┘
         │ (Dependency Inversion: domain        │
         │  declares port, adapter implements)  │
═════════▼══════════════════════════════════════▼══════════════════════════ ADAPTADORES
   ┌─────▼──────────────────────────────────────▼─────────────────────┐
   │  PedidoRepositoryJPA          HistoricoStatusRepositoryJPA       │
   │     (EntityManager)                (EntityManager)               │
   └─────┬────────────────────────────────────────────────────────────┘
         │ SQL
         ▼
   ┌─────────────────────────────────────────────────────────────────────┐
   │  H2 (in-memory)   tables: pedidos · itens_pedido · historico_status │
   └─────────────────────────────────────────────────────────────────────┘
```
## Files in this slice

| Layer | File |
|---|---|
| Adaptadores · Segurança | `Adaptadores/Seguranca/AutenticacaoInterceptor.java` |
| Adaptadores · Apresentação | `Adaptadores/Apresentacao/PedidoController.java` |
| Aplicação · Caso de uso | `Aplicacao/SubmeterPedidoParaAprovacaoUC.java` |
| Aplicação · DTOs | `Aplicacao/Requests/SubmeterPedidoRequest.java`, `Aplicacao/Responses/SubmeterPedidoResponse.java` |
| Domínio · Serviço | `Dominio/Servicos/ServicoPedido.java`, `ServicoEstoque.java`, `ServicoImposto.java`, `ServicoDesconto.java` |
| Domínio · Portas | `Dominio/Dados/PedidoRepository.java`, `HistoricoStatusRepository.java`, `ProdutosRepository.java` |
| Adaptadores · Dados | `Adaptadores/Dados/PedidoRepositoryJPA.java`, `HistoricoStatusRepositoryJPA.java` |