#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

MODE="${1:-}"
NGROK_LOG="${NGROK_LOG:-/tmp/rucoplan-ngrok.log}"
NGROK_API_URL="${NGROK_API_URL:-http://127.0.0.1:4040/api/tunnels}"
NGROK_PID=""
NGROK_CMD=()

choose_mode() {
  if [[ -n "${MODE}" ]]; then
    return
  fi

  if [[ ! -t 0 ]]; then
    MODE="local"
    return
  fi

  echo "Como queres correr a app?"
  echo "1) Localmente"
  echo "2) Localmente com ngrok para Telegram"
  read -r -p "Escolha [1-2]: " choice

  case "${choice}" in
    1|"") MODE="local" ;;
    2) MODE="ngrok" ;;
    *) echo "Opção inválida: ${choice}" >&2; exit 1 ;;
  esac
}

load_environment() {
  if [[ ! -f .env ]]; then
    cp .env.example .env
    chmod 600 .env
    echo "Created .env from .env.example."
  fi

  set -a
  source .env
  set +a
}

maven_command() {
  if [[ -x ./mvnw ]]; then
    MAVEN_CMD=("./mvnw")
  elif command -v mvn >/dev/null 2>&1; then
    MAVEN_CMD=("mvn")
  else
    echo "Maven was not found and this project has no executable mvnw." >&2
    exit 1
  fi
}

ngrok_command() {
  if command -v ngrok >/dev/null 2>&1; then
    NGROK_CMD=("ngrok")
    return
  fi

  if command -v npx >/dev/null 2>&1; then
    NGROK_CMD=("npx" "--yes" "ngrok")
    return
  fi

  echo "ngrok was not found in PATH and npx is not available." >&2
  echo >&2
  echo "Install ngrok first, then authenticate it:" >&2
  echo "  brew install ngrok/ngrok/ngrok" >&2
  echo "  ngrok config add-authtoken <your-ngrok-authtoken>" >&2
  echo >&2
  echo "You can get the authtoken from your ngrok account dashboard." >&2
  exit 1
}

start_backend() {
  maven_command
  export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"
  export SERVER_PORT="${SERVER_PORT:-8082}"

  echo "Starting backend on http://localhost:${SERVER_PORT}/"
  echo "Database: ${PRODUCTION_PLANNING_DB_URL}"
  "${MAVEN_CMD[@]}" spring-boot:run
}

cleanup_ngrok() {
  if [[ -n "${NGROK_PID}" ]] && kill -0 "${NGROK_PID}" >/dev/null 2>&1; then
    kill "${NGROK_PID}" >/dev/null 2>&1 || true
  fi
}

extract_ngrok_url() {
  local body="$1"

  if command -v jq >/dev/null 2>&1; then
    printf '%s' "${body}" | jq -r '.tunnels[]?.public_url | select(startswith("https://"))' | head -n 1
    return
  fi

  printf '%s' "${body}" | sed -n 's/.*"public_url"[[:space:]]*:[[:space:]]*"\(https:\/\/[^"]*\)".*/\1/p' | head -n 1
}

wait_for_ngrok_url() {
  local public_url=""

  printf "Waiting for ngrok HTTPS tunnel" >&2
  for _ in {1..30}; do
    if [[ -n "${NGROK_PID}" ]] && ! kill -0 "${NGROK_PID}" >/dev/null 2>&1; then
      echo >&2
      echo "ngrok stopped before exposing a tunnel. Recent log output:" >&2
      tail -n 20 "${NGROK_LOG}" >&2 || true
      exit 1
    fi

    local response
    response="$(curl --silent "${NGROK_API_URL}" 2>/dev/null || true)"
    public_url="$(extract_ngrok_url "${response}")"
    if [[ -n "${public_url}" ]]; then
      echo >&2
      printf '%s' "${public_url}"
      return
    fi
    printf "." >&2
    sleep 1
  done

  echo >&2
  echo "ngrok did not expose an HTTPS tunnel in time. Check ${NGROK_LOG}." >&2
  exit 1
}

register_telegram_webhook_if_ready() {
  if [[ "${TELEGRAM_ENABLED:-false}" != "true" ]]; then
    echo "Telegram is disabled. Set TELEGRAM_ENABLED=true in .env to register the webhook."
    return
  fi

  if [[ -z "${TELEGRAM_BOT_TOKEN:-}" || -z "${TELEGRAM_WEBHOOK_SECRET:-}" ]]; then
    echo "Telegram webhook was not registered because TELEGRAM_BOT_TOKEN or TELEGRAM_WEBHOOK_SECRET is missing."
    return
  fi

  TELEGRAM_WEBHOOK_BASE_URL="${TELEGRAM_WEBHOOK_BASE_URL}" "${SCRIPT_DIR}/register-telegram-webhook.sh"
}

start_with_ngrok() {
  ngrok_command

  "${SCRIPT_DIR}/start-postgres.sh"
  load_environment

  export SERVER_PORT="${SERVER_PORT:-8082}"
  : > "${NGROK_LOG}"

  NGROK_ARGS=("http" "${SERVER_PORT}" "--log=stdout")
  if [[ -n "${TELEGRAM_WEBHOOK_BASE_URL:-}" ]]; then
    NGROK_ARGS+=("--url" "${TELEGRAM_WEBHOOK_BASE_URL%/}")
    echo "Starting ngrok tunnel for http://localhost:${SERVER_PORT} using TELEGRAM_WEBHOOK_BASE_URL from .env..."
  else
    echo "Starting ngrok tunnel for http://localhost:${SERVER_PORT} with a temporary public URL..."
  fi

  "${NGROK_CMD[@]}" "${NGROK_ARGS[@]}" >"${NGROK_LOG}" 2>&1 &
  NGROK_PID="$!"
  trap cleanup_ngrok EXIT INT TERM

  TELEGRAM_WEBHOOK_BASE_URL="$(wait_for_ngrok_url)"
  export TELEGRAM_WEBHOOK_BASE_URL

  echo "ngrok public URL: ${TELEGRAM_WEBHOOK_BASE_URL}"
  register_telegram_webhook_if_ready
  start_backend
}

choose_mode

case "${MODE}" in
  local)
    exec "${SCRIPT_DIR}/start-backend.sh"
    ;;
  ngrok)
    start_with_ngrok
    ;;
  *)
    echo "Usage: $0 [local|ngrok]" >&2
    exit 1
    ;;
esac
