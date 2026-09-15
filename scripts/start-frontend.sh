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

APP_PORT="${SERVER_PORT:-8082}"
HEALTH_URL="http://127.0.0.1:${APP_PORT}/actuator/health"

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required but was not found in PATH." >&2
  exit 1
fi

response="$(curl --fail --silent --show-error --max-time 2 "${HEALTH_URL}" 2>/dev/null || true)"
if [[ "${response}" == *'"status":"UP"'* ]]; then
  echo "Frontend is served by the Spring Boot backend."
  echo "Login: http://localhost:${APP_PORT}/login.html"
  echo "Admin: http://localhost:${APP_PORT}/admin.html"
  echo "Driver: http://localhost:${APP_PORT}/driver.html"
else
  echo "Backend is not healthy at ${HEALTH_URL}. Start it with ./scripts/start-backend.sh." >&2
  exit 1
fi
