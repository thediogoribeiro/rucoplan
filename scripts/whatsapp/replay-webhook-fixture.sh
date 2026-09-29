#!/usr/bin/env bash
set -euo pipefail

: "${WHATSAPP_APP_SECRET:?Set WHATSAPP_APP_SECRET}"

fixture="${1:-}"
endpoint="${2:-http://localhost:8082/api/v1/integrations/whatsapp/webhook}"

if [[ -z "$fixture" || ! -f "$fixture" ]]; then
  echo "Usage: $0 <fixture-json-file> [webhook-url]" >&2
  exit 1
fi

signature="sha256=$(openssl dgst -sha256 -hmac "$WHATSAPP_APP_SECRET" -binary "$fixture" | xxd -p -c 256)"

curl -fsS \
  -X POST "$endpoint" \
  -H "Content-Type: application/json" \
  -H "X-Hub-Signature-256: ${signature}" \
  --data-binary "@${fixture}"

echo
echo "Fixture replayed against ${endpoint}."
