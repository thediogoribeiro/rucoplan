#!/usr/bin/env bash
set -euo pipefail

: "${WHATSAPP_ACCESS_TOKEN:?Set WHATSAPP_ACCESS_TOKEN}"
: "${WHATSAPP_PHONE_NUMBER_ID:?Set WHATSAPP_PHONE_NUMBER_ID}"
: "${WHATSAPP_GRAPH_API_VERSION:=v26.0}"

to="${1:-}"
message="${2:-Teste RucoPlan WhatsApp Cloud API}"

if [[ -z "$to" ]]; then
  echo "Usage: $0 <recipient-wa-id-with-country-code> [message]" >&2
  exit 1
fi

read -r -p "Send a real WhatsApp message to ****${to: -4}? Type SEND to continue: " confirmation
if [[ "$confirmation" != "SEND" ]]; then
  echo "Cancelled."
  exit 0
fi

curl -fsS \
  -X POST "https://graph.facebook.com/${WHATSAPP_GRAPH_API_VERSION}/${WHATSAPP_PHONE_NUMBER_ID}/messages" \
  -H "Authorization: Bearer ${WHATSAPP_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  --data "$(jq -n --arg to "$to" --arg body "$message" '{
    messaging_product: "whatsapp",
    to: $to,
    type: "text",
    text: { preview_url: false, body: $body }
  }')"

echo
echo "Message request sent. Token was not printed."
