#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

if [[ ! -f .env ]]; then
  cp .env.example .env
  chmod 600 .env
  echo "Created .env from .env.example."
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required but was not found in PATH." >&2
  exit 1
fi

set -a
source .env
set +a

echo "Starting PostgreSQL on localhost:${PRODUCTION_PLANNING_POSTGRES_PORT:-15433}..."
docker compose up -d postgres >/dev/null

printf "Waiting for PostgreSQL"
for _ in {1..60}; do
  health="$(docker compose ps --format '{{.Health}}' postgres 2>/dev/null | head -n 1 || true)"
  if [[ "${health}" == "healthy" ]]; then
    echo
    echo "PostgreSQL is ready."
    exit 0
  fi
  printf "."
  sleep 2
done

echo
echo "PostgreSQL did not become healthy in time." >&2
docker compose ps
exit 1
