#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

"${SCRIPT_DIR}/start-postgres.sh"

set -a
source .env
set +a

if [[ -x ./mvnw ]]; then
  MAVEN_CMD=("./mvnw")
elif command -v mvn >/dev/null 2>&1; then
  MAVEN_CMD=("mvn")
else
  echo "Maven was not found and this project has no executable mvnw." >&2
  exit 1
fi

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"
export SERVER_PORT="${SERVER_PORT:-8082}"

echo "Starting backend on http://localhost:${SERVER_PORT}/"
echo "Database: ${PRODUCTION_PLANNING_DB_URL}"
exec "${MAVEN_CMD[@]}" spring-boot:run
