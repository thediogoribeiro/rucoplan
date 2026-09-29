#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

ENV_TELEGRAM_BOT_TOKEN="${TELEGRAM_BOT_TOKEN:-}"

if [[ -f .env ]]; then
  set -a
  source .env
  set +a
fi

if [[ -n "${ENV_TELEGRAM_BOT_TOKEN}" ]]; then
  TELEGRAM_BOT_TOKEN="${ENV_TELEGRAM_BOT_TOKEN}"
fi

: "${TELEGRAM_BOT_TOKEN:?TELEGRAM_BOT_TOKEN is required}"

curl --fail --silent --show-error \
  --request POST \
  --form "drop_pending_updates=false" \
  "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/deleteWebhook" >/tmp/rucodel-telegram-webhook-delete-response.json

echo "Webhook Telegram removido."
echo "Resposta guardada em /tmp/rucodel-telegram-webhook-delete-response.json."
