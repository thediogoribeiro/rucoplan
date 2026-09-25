#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

YES=false

for arg in "$@"; do
  case "${arg}" in
    --yes|-y)
      YES=true
      ;;
    --help|-h)
      cat <<'USAGE'
Usage: scripts/clean-database.sh [--yes]

Cleans all RucoPlan application data from the configured PostgreSQL database.
The schema and flyway_schema_history table are preserved, so migrations remain applied.

Options:
  -y, --yes   Skip the interactive confirmation prompt.
  -h, --help  Show this help message.
USAGE
      exit 0
      ;;
    *)
      echo "Unknown argument: ${arg}" >&2
      exit 1
      ;;
  esac
done

if [[ ! -f .env ]]; then
  echo ".env was not found. Create it from .env.example first." >&2
  exit 1
fi

set -a
source .env
set +a

DB_URL="${PRODUCTION_PLANNING_DB_URL:-}"
DB_USER="${PRODUCTION_PLANNING_DB_USERNAME:-}"
DB_PASSWORD="${PRODUCTION_PLANNING_DB_PASSWORD:-}"

if [[ -z "${DB_URL}" || -z "${DB_USER}" || -z "${DB_PASSWORD}" ]]; then
  echo "Missing database configuration. Check PRODUCTION_PLANNING_DB_URL, PRODUCTION_PLANNING_DB_USERNAME and PRODUCTION_PLANNING_DB_PASSWORD in .env." >&2
  exit 1
fi

if [[ ! "${DB_URL}" =~ ^jdbc:postgresql://([^:/?]+)(:([0-9]+))?/([^?]+) ]]; then
  echo "Unsupported PRODUCTION_PLANNING_DB_URL format: ${DB_URL}" >&2
  exit 1
fi

DB_HOST="${BASH_REMATCH[1]}"
DB_PORT="${BASH_REMATCH[3]:-5432}"
DB_NAME="${BASH_REMATCH[4]}"

if [[ "${YES}" != true ]]; then
  cat <<EOF
This will permanently delete all application data from:

  Host:     ${DB_HOST}
  Port:     ${DB_PORT}
  Database: ${DB_NAME}
  User:     ${DB_USER}

The schema and flyway_schema_history will be preserved.
EOF
  printf "Type the database name (%s) to continue: " "${DB_NAME}"
  read -r confirmation
  if [[ "${confirmation}" != "${DB_NAME}" ]]; then
    echo "Cancelled."
    exit 0
  fi
fi

SQL=$(cat <<'SQL'
DO $$
DECLARE
    truncate_statement TEXT;
BEGIN
    SELECT 'TRUNCATE TABLE ' || string_agg(format('%I.%I', schemaname, tablename), ', ') || ' RESTART IDENTITY CASCADE'
    INTO truncate_statement
    FROM pg_tables
    WHERE schemaname = 'public'
      AND tablename <> 'flyway_schema_history';

    IF truncate_statement IS NOT NULL THEN
        EXECUTE truncate_statement;
    END IF;

    IF to_regclass('public.driver_code_seq') IS NOT NULL THEN
        PERFORM setval('public.driver_code_seq', 1, false);
    END IF;

    IF to_regclass('public.customer_code_seq') IS NOT NULL THEN
        PERFORM setval('public.customer_code_seq', 1, false);
    END IF;

    IF to_regclass('public.customer_number_seq') IS NOT NULL THEN
        PERFORM setval('public.customer_number_seq', 1000, false);
    END IF;
END $$;
SQL
)

run_with_local_psql() {
  PGPASSWORD="${DB_PASSWORD}" psql \
    -h "${DB_HOST}" \
    -p "${DB_PORT}" \
    -U "${DB_USER}" \
    -d "${DB_NAME}" \
    -v ON_ERROR_STOP=1 \
    -q \
    -c "${SQL}"
}

run_with_docker_psql() {
  docker compose up -d postgres >/dev/null
  docker compose exec -T postgres psql \
    -U "${DB_USER}" \
    -d "${DB_NAME}" \
    -v ON_ERROR_STOP=1 \
    -q \
    -c "${SQL}"
}

if command -v psql >/dev/null 2>&1; then
  run_with_local_psql
elif command -v docker >/dev/null 2>&1 && [[ "${DB_HOST}" == "localhost" || "${DB_HOST}" == "127.0.0.1" ]]; then
  run_with_docker_psql
else
  echo "psql was not found. Install PostgreSQL client tools or run the local Docker postgres service." >&2
  exit 1
fi

echo "Database cleaned successfully."
