#!/usr/bin/env bash
set -euo pipefail

: "${WHATSAPP_WEBHOOK_PUBLIC_URL:?Set WHATSAPP_WEBHOOK_PUBLIC_URL}"
: "${WHATSAPP_VERIFY_TOKEN:?Set WHATSAPP_VERIFY_TOKEN}"

challenge="rucoplan-$(date +%s)"
url="${WHATSAPP_WEBHOOK_PUBLIC_URL}?hub.mode=subscribe&hub.verify_token=${WHATSAPP_VERIFY_TOKEN}&hub.challenge=${challenge}"

response="$(curl -fsS "$url")"
if [[ "$response" != "$challenge" ]]; then
  echo "Webhook verification failed: challenge was not echoed." >&2
  exit 1
fi

echo "Webhook verification endpoint echoed the challenge successfully."
