#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

if [[ -f .env ]]; then
  set -a
  source .env
  set +a
fi

: "${TELEGRAM_BOT_TOKEN:?TELEGRAM_BOT_TOKEN is required}"

COMMANDS='[
  {"command":"start","description":"Iniciar ou retomar o registo"},
  {"command":"novo","description":"Criar um novo pedido"},
  {"command":"cancelar","description":"Cancelar o pedido em curso"},
  {"command":"ajuda","description":"Mostrar instruções"},
  {"command":"pedidos","description":"Ver pedidos recentes"}
]'

curl --fail --silent --show-error \
  --request POST \
  --header "Content-Type: application/json" \
  --data "{\"commands\":${COMMANDS}}" \
  "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/setMyCommands" >/tmp/rucodel-telegram-commands-response.json

echo "Comandos Telegram configurados."
echo "Resposta guardada em /tmp/rucodel-telegram-commands-response.json."
