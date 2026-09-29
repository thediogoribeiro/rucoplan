#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

APPLY=false
for arg in "$@"; do
  case "${arg}" in
    --apply) APPLY=true ;;
    --dry-run) APPLY=false ;;
    -h|--help)
      cat <<'USAGE'
Usage: scripts/reset-local-demo-data.sh [--dry-run|--apply]

Safely normalizes local demo data only. Dry-run is the default.
USAGE
      exit 0
      ;;
    *) echo "Unknown argument: ${arg}" >&2; exit 1 ;;
  esac
done

if [[ ! -f .env ]]; then
  echo ".env was not found. Create it from .env.example first." >&2
  exit 1
fi

set -a
source .env
set +a

APP_ENVIRONMENT="${APP_ENVIRONMENT:-local}"
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"
DB_URL="${PRODUCTION_PLANNING_DB_URL:-${JDBC_DATABASE_URL:-}}"
DB_USER="${PRODUCTION_PLANNING_DB_USERNAME:-${JDBC_DATABASE_USERNAME:-}}"
DB_PASSWORD="${PRODUCTION_PLANNING_DB_PASSWORD:-${JDBC_DATABASE_PASSWORD:-}}"

if [[ "${APP_ENVIRONMENT}" != "local" && "${SPRING_PROFILES_ACTIVE}" != *"local"* ]]; then
  echo "Refusing to reset demo data because APP_ENVIRONMENT/SPRING_PROFILES_ACTIVE is not local." >&2
  exit 1
fi

if [[ ! "${DB_URL}" =~ ^jdbc:postgresql://([^:/?]+)(:([0-9]+))?/([^?]+) ]]; then
  echo "Unsupported PostgreSQL JDBC URL." >&2
  exit 1
fi

DB_HOST="${BASH_REMATCH[1]}"
DB_PORT="${BASH_REMATCH[3]:-5432}"
DB_NAME="${BASH_REMATCH[4]}"

case "${DB_HOST}" in
  localhost|127.0.0.1|postgres) ;;
  *)
    echo "Refusing to reset demo data against non-local host: ${DB_HOST}" >&2
    exit 1
    ;;
esac

SUMMARY_SQL=$(cat <<'SQL'
WITH teste AS (
    SELECT id FROM driver WHERE lower(name) = 'teste' ORDER BY created_at NULLS LAST, id LIMIT 1
)
SELECT 'drivers_to_remove=' || count(*) FROM driver WHERE id NOT IN (SELECT id FROM teste)
UNION ALL
SELECT 'requests_to_reassign=' || count(*) FROM wheel_intake_request WHERE driver_id IS NOT NULL AND driver_id NOT IN (SELECT id FROM teste)
UNION ALL
SELECT 'customers_to_rename=' || count(*) FROM customer_reference
UNION ALL
SELECT 'telegram_identity_preserved=' || count(*) FROM messaging_identity WHERE driver_id IN (SELECT id FROM teste);
SQL
)

APPLY_SQL=$(cat <<'SQL'
BEGIN;

WITH teste AS (
    SELECT id FROM driver WHERE lower(name) = 'teste' ORDER BY created_at NULLS LAST, id LIMIT 1
), reassigned AS (
    UPDATE wheel_intake_request
    SET driver_id = (SELECT id FROM teste), updated_by = 'LOCAL_DEMO_RESET'
    WHERE driver_id IS NOT NULL
      AND driver_id NOT IN (SELECT id FROM teste)
      AND EXISTS (SELECT 1 FROM teste)
    RETURNING id
), removed_driver_sites AS (
    DELETE FROM driver_production_site
    WHERE driver_id NOT IN (SELECT id FROM teste)
    RETURNING id
), removed_users AS (
    DELETE FROM app_user
    WHERE driver_id IS NOT NULL AND driver_id NOT IN (SELECT id FROM teste)
    RETURNING id
)
DELETE FROM driver
WHERE id NOT IN (SELECT id FROM teste);

WITH ranked AS (
    SELECT id,
           'loja de jantes de ' || repeat(chr(64 + row_number() OVER (PARTITION BY production_site_id ORDER BY created_at, id)::int), 4) AS demo_name
    FROM customer_reference
)
UPDATE customer_reference c
SET name = ranked.demo_name,
    normalized_name = lower(ranked.demo_name),
    external_system = NULL,
    external_customer_id = NULL,
    updated_by = 'LOCAL_DEMO_RESET'
FROM ranked
WHERE c.id = ranked.id;

WITH teste AS (
    SELECT id FROM driver WHERE lower(name) = 'teste' ORDER BY created_at NULLS LAST, id LIMIT 1
), sites AS (
    SELECT id FROM production_site WHERE code IN ('PT', 'LUX')
)
INSERT INTO driver_production_site (id, driver_id, production_site_id, active, association_source, associated_at, associated_by)
SELECT gen_random_uuid(), teste.id, sites.id, true, 'MIGRATION', now(), 'LOCAL_DEMO_RESET'
FROM teste CROSS JOIN sites
ON CONFLICT (driver_id, production_site_id) DO UPDATE
SET active = true,
    association_source = 'MIGRATION',
    associated_by = 'LOCAL_DEMO_RESET';

COMMIT;
SQL
)

echo "Local demo reset target:"
echo "  Host: ${DB_HOST}"
echo "  Port: ${DB_PORT}"
echo "  Database: ${DB_NAME}"
echo "  User: ${DB_USER}"
echo

run_psql() {
  local sql="$1"
  if command -v psql >/dev/null 2>&1; then
    PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -v ON_ERROR_STOP=1 -q -c "${sql}"
    return
  fi
  if command -v docker >/dev/null 2>&1 && [[ "${DB_HOST}" == "localhost" || "${DB_HOST}" == "127.0.0.1" || "${DB_HOST}" == "postgres" ]]; then
    docker compose exec -T postgres psql -U "${DB_USER}" -d "${DB_NAME}" -v ON_ERROR_STOP=1 -q -c "${sql}"
    return
  fi
  echo "Neither psql nor a local docker compose postgres service is available." >&2
  exit 1
}

echo "Dry-run summary:"
run_psql "${SUMMARY_SQL}"

if [[ "${APPLY}" != true ]]; then
  echo
  echo "Dry-run only. Re-run with --apply to change local demo data."
  exit 0
fi

run_psql "${APPLY_SQL}"
echo "Local demo data reset completed."
