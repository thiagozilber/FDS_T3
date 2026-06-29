#!/usr/bin/env bash
#
# demo.sh — roteiro de demonstração ponta a ponta do backend da Tele Pizza.
#
# Uso:
#   ./mvnw spring-boot:run        # num terminal (reinicie antes da demo p/ ids/estado previsíveis)
#   ./demo.sh                     # noutro terminal
#   BASE=http://localhost:8099 ./demo.sh   # se estiver rodando em outra porta
#
# Não usa 'set -e' de propósito: várias chamadas retornam 4xx/409 de propósito (401/403/RECUSADO/400).
# Usa 'jq' para formatar o JSON se estiver instalado; caso contrário mostra o texto cru.

BASE="${BASE:-http://localhost:8080}"

# --- cores (desligam fora de terminal) -------------------------------------
if [ -t 1 ]; then C_H='\033[1;36m'; C_M='\033[33m'; C_OK='\033[32m'; C_NO='\033[31m'; C_0='\033[0m'
else C_H=''; C_M=''; C_OK=''; C_NO=''; C_0=''; fi

BODY="$(mktemp)"; trap 'rm -f "$BODY"' EXIT

say()   { printf "\n${C_H}== %s ==${C_0}\n" "$*"; }
pretty(){ if command -v jq >/dev/null 2>&1; then jq . 2>/dev/null <"$BODY" || cat "$BODY"; else cat "$BODY"; fi; }
getval(){ # $1 = chave json simples; lê de "$BODY"
  if command -v jq >/dev/null 2>&1; then jq -r ".$1 // empty" <"$BODY"
  else sed -E "s/.*\"$1\":\"?([^\",}]+)\"?.*/\1/" <"$BODY"; fi; }

# req METHOD PATH [curl-args...] -> imprime "METHOD PATH -> código" + corpo; deixa o corpo em $BODY
req() {
  local m="$1" p="$2"; shift 2
  local code; code=$(curl -s -o "$BODY" -w "%{http_code}" -X "$m" "$BASE$p" "$@")
  local color="$C_OK"; case "$code" in 4*|5*|000) color="$C_NO";; esac
  printf "${C_M}%-4s %-32s${C_0} -> ${color}%s${C_0}\n" "$m" "$p" "$code"
  pretty; echo
}
auth() { printf 'Authorization: Bearer %s' "$1"; }

# pedido_corpo CPF "ENDERECO" "itensJSON"  -> ecoa o corpo do POST /pedidos
pedido() { printf '{"clienteCpf":"%s","enderecoEntrega":"%s","itens":%s}' "$1" "$2" "$3"; }

# submeter_e_pagar ITENSJSON -> submete + paga em silêncio (acumula pedidos pagos p/ fidelidade)
submeter_e_pagar() {
  curl -s -o "$BODY" -X POST "$BASE/pedidos" -H "$(auth "$TOK")" -H 'Content-Type: application/json' \
       -d "$(pedido 9100 'Rua Nova, 42' "$1")"
  local id; id="$(getval id)"
  curl -s -o /dev/null -X POST "$BASE/pedidos/$id/pagar" -H "$(auth "$TOK")"
  echo "  pedido #$id submetido e pago"
}

# --- preflight -------------------------------------------------------------
if ! curl -s -o /dev/null --max-time 3 "$BASE/"; then
  printf "${C_NO}App não respondeu em %s — suba com ./mvnw spring-boot:run${C_0}\n" "$BASE"; exit 1
fi
say "0. Healthcheck ($BASE)"; req GET /

# --- 1. cadastro de novo cliente (UC11) ------------------------------------
say "1. UC11 cadastrar novo cliente — rota pública /clientes (sem token); a senha não volta na resposta"
req POST /clientes -H 'Content-Type: application/json' \
    -d '{"cpf":"9100","nome":"Margarida Pato","celular":"51990001122","endereco":"Rua Nova, 42","email":"margarida.pato@email.com","senha":"margarida123"}'
# (reexecutando sem reiniciar o app, retorna 400 "CPF já cadastrado" — esperado; o login abaixo segue funcionando)

# --- 2. autenticação -------------------------------------------------------
say "2. Auth: anônimo é bloqueado (espera 401)"
req GET /pedidos/1/status

say "2. UC12 o novo cliente faz login (usuário = e-mail) — token Bearer, sem senha na resposta"
req POST /auth/login -H 'Content-Type: application/json' \
    -d '{"email":"margarida.pato@email.com","senha":"margarida123"}'
TOK="$(getval token)"; printf "token cliente: %s…\n" "${TOK:0:8}"

say "2. Cliente em rota de admin (espera 403)"
req GET /descontos/politicas -H "$(auth "$TOK")"

# --- 3. admin administra a loja (UC1, UC2, UC3 + GET /cardapio/{id}) --------
say "3. Admin faz login"
req POST /auth/login -H 'Content-Type: application/json' \
    -d '{"email":"admin@pizzaria.com","senha":"admin123"}'
ADM="$(getval token)"; printf "token admin: %s…\n" "${ADM:0:8}"

say "3. UC1 listar cardápios disponíveis"
req GET /cardapio/lista -H "$(auth "$ADM")"
say "3. (bonus) GET /cardapio/{id} — detalha um cardápio específico (itens com descrição + preço)"
req GET /cardapio/1 -H "$(auth "$ADM")"
say "3. UC3 listar políticas de desconto"
req GET /descontos/politicas -H "$(auth "$ADM")"
say "3. UC2 definir cardápio corrente"
req PUT /cardapio/corrente/1 -H "$(auth "$ADM")"

# --- 4. cliente consulta cardápio e monta o pedido (UC5, UC6) --------------
say "4. UC5 carregar cardápio corrente — cada item traz DESCRIÇÃO e PREÇO unitário (em centavos)"
req GET /cardapio/corrente -H "$(auth "$TOK")"

say "4. UC6 submeter pedido com VÁRIOS itens + quantidades (2x Calabresa R\$55 + 1x Margherita R\$40)"
echo "   subtotal 150.00 + imposto 10% (15.00) - desconto 0 (SemDesconto) = custoFinal 165.00; status NOVO->APROVADO"
req POST /pedidos -H "$(auth "$TOK")" -H 'Content-Type: application/json' \
    -d "$(pedido 9100 'Rua Nova, 42' '[{"produtoId":1,"quantidade":2},{"produtoId":3,"quantidade":1}]')"
PID="$(getval id)"; printf "pedido criado: #%s\n" "$PID"

# --- 5. pagamento + ciclo do pedido (UC9, UC7) -----------------------------
say "5. UC9 pagar pedido #$PID -> PAGO (não cancelável); acompanhar o ciclo (Cozinha/Entrega assíncronas)"
req POST "/pedidos/$PID/pagar" -H "$(auth "$TOK")"
ultimo=""
for i in 1 2 3 4 5 6 7; do
  sleep 3
  curl -s -o "$BODY" "$BASE/pedidos/$PID/status" -H "$(auth "$TOK")"
  ultimo="$(getval statusAtual)"
  printf "  t+%2ds: %s\n" "$((i*3))" "$ultimo"
  [ "$ultimo" = "ENTREGUE" ] && break
done
say "5. UC7 histórico completo (com timestamps) do pedido #$PID — acompanhável pelo número do pedido"
req GET "/pedidos/$PID/status" -H "$(auth "$TOK")"

# --- 6. descontos: estratégia em runtime + regra de fidelidade -------------
say "6a. UC4 admin troca a política para PromocaoVerao (runtime, persistido) e o cliente refaz o pedido"
req PUT /descontos/corrente/PromocaoVerao -H "$(auth "$ADM")"
echo "   mesmo pedido, agora com desconto (5%): subtotal 150.00 - 7.50 + 15.00 = 157.50"
req POST /pedidos -H "$(auth "$TOK")" -H 'Content-Type: application/json' \
    -d "$(pedido 9100 'Rua Nova, 42' '[{"produtoId":1,"quantidade":2},{"produtoId":3,"quantidade":1}]')"

say "6b. Regra do enunciado: Fidelidade7 = 7% para clientes com MAIS de 3 pedidos pagos em 20 dias"
req PUT /descontos/corrente/Fidelidade7 -H "$(auth "$ADM")"
echo "   o cliente ainda não tem >3 pedidos pagos -> pedido sai SEM desconto:"
req POST /pedidos -H "$(auth "$TOK")" -H 'Content-Type: application/json' \
    -d "$(pedido 9100 'Rua Nova, 42' '[{"produtoId":1,"quantidade":1}]')"
echo "   pagando pedidos até o cliente ultrapassar 3 (já tem 1 pago: o #$PID)..."
submeter_e_pagar '[{"produtoId":1,"quantidade":1}]'
submeter_e_pagar '[{"produtoId":1,"quantidade":1}]'
submeter_e_pagar '[{"produtoId":1,"quantidade":1}]'
echo "   agora (>3 pagos) o próximo pedido recebe 7%: 1x Calabresa 55.00 - 3.85 + 5.50 = 56.65"
req POST /pedidos -H "$(auth "$TOK")" -H 'Content-Type: application/json' \
    -d "$(pedido 9100 'Rua Nova, 42' '[{"produtoId":1,"quantidade":1}]')"

# --- 7. casos de borda (UC8 + não-cancelável + estoque) --------------------
say "7. UC8 cancelar — cria um pedido novo e cancela ANTES de pagar (-> CANCELADO)"
req POST /pedidos -H "$(auth "$TOK")" -H 'Content-Type: application/json' \
    -d "$(pedido 9100 'Av. Central, 200' '[{"produtoId":3,"quantidade":1}]')"
CANC="$(getval id)"
req POST "/pedidos/$CANC/cancelar" -H "$(auth "$TOK")"

say "7. Pedido já pago NÃO pode mais ser cancelado (pedido #$PID) — espera 400"
req POST "/pedidos/$PID/cancelar" -H "$(auth "$TOK")"

say "7. Estoque insuficiente — 100x uma pizza: pedido RECUSADO destacando os itens indisponíveis"
req POST /pedidos -H "$(auth "$TOK")" -H 'Content-Type: application/json' \
    -d "$(pedido 9100 'x' '[{"produtoId":1,"quantidade":100}]')"

# --- 8. UC10 entregues entre datas -----------------------------------------
say "8. UC10 listar pedidos entregues entre datas (arquivados, associados ao cliente)"
req GET "/pedidos/entregues?ini=2026-06-01&fim=2026-12-31" -H "$(auth "$TOK")"

say "Fim. (Console H2: $BASE/h2 — jdbc:h2:mem:pizzadb, user sa, sem senha)"
