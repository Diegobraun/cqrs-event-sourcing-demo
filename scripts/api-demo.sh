#!/usr/bin/env bash
set -u

BASE_URL="${BASE_URL:-http://localhost:8080}"

call() {
  local method="$1" path="$2" body="${3:-}"
  echo
  echo ">>> $method $path ${body}"
  if [ -n "$body" ]; then
    curl -s -i -X "$method" "$BASE_URL$path" -H 'Content-Type: application/json' -d "$body"
  else
    curl -s -i -X "$method" "$BASE_URL$path"
  fi
  echo
}

echo "=== COMMANDS (write side) ==="
call POST /accounts '{"accountId":"ACC-1","owner":"Diego"}'
call POST /accounts/ACC-1/deposits '{"amount":1000}'
call POST /accounts/ACC-1/withdrawals '{"amount":300}'
call POST /accounts/ACC-1/withdrawals '{"amount":5000}'
call POST /accounts/ACC-1/close
call POST /accounts '{"accountId":"ACC-1","owner":"Outro"}'
call POST /accounts/ACC-9/deposits '{"amount":10}'
call POST /accounts/ACC-1/deposits '{"amount":"abc"}'

echo
echo "=== READ-YOUR-WRITES: pedir versao que ainda pode nao estar projetada ==="
call POST /accounts/ACC-1/deposits '{"amount":50}'
call GET "/accounts/ACC-1?minVersion=999"
sleep 0.2

echo
echo "=== QUERIES (read side) ==="
call GET "/accounts/ACC-1?minVersion=4"
call GET /accounts/ACC-1/statement
call GET /accounts/ACC-1/events
call GET "/rankings/balance?limit=5"
call GET /accounts/ACC-404

echo
echo "=== ADMIN: estado e rebuild das projecoes ==="
call GET /admin/projections
call POST /admin/projections/statement/rebuild
sleep 0.2
call GET /admin/projections
call GET /accounts/ACC-1/statement

echo
echo "=== SAGA: transferencias entre contas ==="
call POST /accounts '{"accountId":"ACC-2","owner":"Maria"}'
call POST /accounts '{"accountId":"ACC-3","owner":"Joao"}'
call POST /accounts/ACC-3/close

echo
echo "--- 1. sucesso: ACC-1 -> ACC-2 ---"
call POST /transfers '{"transferId":"TR-1","sourceAccountId":"ACC-1","targetAccountId":"ACC-2","amount":100}'
sleep 0.5
call GET /transfers/TR-1

echo
echo "--- 2. falha no debito (saldo insuficiente): ACC-2 -> ACC-1 ---"
call POST /transfers '{"transferId":"TR-2","sourceAccountId":"ACC-2","targetAccountId":"ACC-1","amount":99999}'
sleep 0.5
call GET /transfers/TR-2

echo
echo "--- 3. falha no credito (destino encerrado) => estorno: ACC-1 -> ACC-3 ---"
call POST /transfers '{"transferId":"TR-3","sourceAccountId":"ACC-1","targetAccountId":"ACC-3","amount":50}'
sleep 0.5
call GET /transfers/TR-3
call GET /accounts/ACC-1/statement

echo
echo "--- 4. timeout: saga pausada, transferencia fica parada e expira (bank.transfer-timeout=10s) ---"
call POST /admin/projections/transfer-saga/pause
call POST /transfers '{"transferId":"TR-4","sourceAccountId":"ACC-1","targetAccountId":"ACC-2","amount":10}'
sleep 0.5
call GET /transfers/TR-4
echo "aguardando o prazo de 10s..."
sleep 12
call GET /transfers/TR-4
call POST /admin/projections/transfer-saga/resume
sleep 0.5
call GET /transfers/TR-4
call GET /admin/projections
