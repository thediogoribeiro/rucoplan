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
else
  TELEGRAM_ACTIVE_BOT_TOKEN="${TELEGRAM_TEST_BOT_TOKEN:-}"
fi

: "${TELEGRAM_ACTIVE_BOT_TOKEN:?Telegram bot token is required for the active environment}"

curl --fail --silent --show-error \
  --request POST \
  --form "drop_pending_updates=false" \
  "https://api.telegram.org/bot${TELEGRAM_ACTIVE_BOT_TOKEN}/deleteWebhook" >/tmp/rucodel-telegram-webhook-delete-response.json

echo "Webhook Telegram removido no ambiente ${APP_ENVIRONMENT_EFFECTIVE}."
echo "Resposta guardada em /tmp/rucodel-telegram-webhook-delete-response.json."
