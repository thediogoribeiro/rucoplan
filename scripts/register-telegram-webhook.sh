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

APP_ENVIRONMENT_EFFECTIVE="${APP_ENV:-${APP_ENVIRONMENT:-local}}"
if [[ "${APP_ENVIRONMENT_EFFECTIVE}" == "production" ]]; then
  TELEGRAM_ACTIVE_BOT_TOKEN="${TELEGRAM_PRODUCTION_BOT_TOKEN:-}"
  TELEGRAM_ACTIVE_WEBHOOK_SECRET="${TELEGRAM_PRODUCTION_WEBHOOK_SECRET:-}"
  TELEGRAM_ACTIVE_WEBHOOK_BASE_URL="${TELEGRAM_PRODUCTION_WEBHOOK_URL:-${TELEGRAM_PRODUCTION_WEBHOOK_BASE_URL:-${PUBLIC_BASE_URL:-}}}"
else
  TELEGRAM_ACTIVE_BOT_TOKEN="${TELEGRAM_TEST_BOT_TOKEN:-}"
  TELEGRAM_ACTIVE_WEBHOOK_SECRET="${TELEGRAM_TEST_WEBHOOK_SECRET:-}"
  TELEGRAM_ACTIVE_WEBHOOK_BASE_URL="${TELEGRAM_TEST_WEBHOOK_URL:-${TELEGRAM_TEST_WEBHOOK_BASE_URL:-}}"
fi

: "${TELEGRAM_ACTIVE_BOT_TOKEN:?Telegram bot token is required for the active environment}"
: "${TELEGRAM_ACTIVE_WEBHOOK_SECRET:?Telegram webhook secret is required for the active environment}"
: "${TELEGRAM_ACTIVE_WEBHOOK_BASE_URL:?Telegram webhook URL is required for the active environment}"

WEBHOOK_URL="${TELEGRAM_ACTIVE_WEBHOOK_BASE_URL%/}/api/v1/integrations/telegram/webhook"

curl --fail --silent --show-error \
  --request POST \
  --form "url=${WEBHOOK_URL}" \
  --form "secret_token=${TELEGRAM_ACTIVE_WEBHOOK_SECRET}" \
  --form "drop_pending_updates=false" \
  "https://api.telegram.org/bot${TELEGRAM_ACTIVE_BOT_TOKEN}/setWebhook" >/tmp/rucodel-telegram-webhook-response.json

echo "Webhook Telegram registado para ${WEBHOOK_URL} no ambiente ${APP_ENVIRONMENT_EFFECTIVE}."
echo "Resposta guardada em /tmp/rucodel-telegram-webhook-response.json."
