#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

ENV_TELEGRAM_BOT_TOKEN="${TELEGRAM_BOT_TOKEN:-}"
ENV_TELEGRAM_WEBHOOK_SECRET="${TELEGRAM_WEBHOOK_SECRET:-}"
ENV_TELEGRAM_WEBHOOK_BASE_URL="${TELEGRAM_WEBHOOK_BASE_URL:-}"

if [[ -f .env ]]; then
  set -a
  source .env
  set +a
fi

if [[ -n "${ENV_TELEGRAM_BOT_TOKEN}" ]]; then
  TELEGRAM_BOT_TOKEN="${ENV_TELEGRAM_BOT_TOKEN}"
fi
if [[ -n "${ENV_TELEGRAM_WEBHOOK_SECRET}" ]]; then
  TELEGRAM_WEBHOOK_SECRET="${ENV_TELEGRAM_WEBHOOK_SECRET}"
fi
if [[ -n "${ENV_TELEGRAM_WEBHOOK_BASE_URL}" ]]; then
  TELEGRAM_WEBHOOK_BASE_URL="${ENV_TELEGRAM_WEBHOOK_BASE_URL}"
fi

: "${TELEGRAM_BOT_TOKEN:?TELEGRAM_BOT_TOKEN is required}"
: "${TELEGRAM_WEBHOOK_SECRET:?TELEGRAM_WEBHOOK_SECRET is required}"
: "${TELEGRAM_WEBHOOK_BASE_URL:?TELEGRAM_WEBHOOK_BASE_URL is required}"

WEBHOOK_URL="${TELEGRAM_WEBHOOK_BASE_URL%/}/api/v1/integrations/telegram/webhook"

curl --fail --silent --show-error \
  --request POST \
  --form "url=${WEBHOOK_URL}" \
  --form "secret_token=${TELEGRAM_WEBHOOK_SECRET}" \
  "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/setWebhook" >/tmp/rucodel-telegram-webhook-response.json

echo "Webhook Telegram registado para ${WEBHOOK_URL}."
echo "Resposta guardada em /tmp/rucodel-telegram-webhook-response.json."
