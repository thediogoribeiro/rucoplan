#!/usr/bin/env bash
set -euo pipefail

required=(
  WHATSAPP_ENABLED
  WHATSAPP_APP_SECRET
  WHATSAPP_VERIFY_TOKEN
  WHATSAPP_PHONE_NUMBER_ID
  WHATSAPP_BUSINESS_ACCOUNT_ID
  WHATSAPP_GRAPH_API_VERSION
  WHATSAPP_WEBHOOK_PUBLIC_URL
)

missing=0
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    echo "missing: ${name}" >&2
    missing=1
  else
    echo "ok: ${name}"
  fi
done

if [[ "${WHATSAPP_ENABLED:-false}" == "true" && -z "${WHATSAPP_ACCESS_TOKEN:-}" ]]; then
  echo "missing: WHATSAPP_ACCESS_TOKEN is required for sending messages" >&2
  missing=1
fi

if [[ "$missing" -ne 0 ]]; then
  echo "WhatsApp configuration is incomplete." >&2
  exit 1
fi

echo "WhatsApp configuration looks complete. Secrets were not printed."
