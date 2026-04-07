#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="${ROOT_DIR}/target/smoke-logs"
mkdir -p "${LOG_DIR}"

MONOLITH_LOG="${LOG_DIR}/slidehub-service.log"

MONOLITH_PID=""

REDIS_CONTAINER="slidehub-redis-smoke"
MONGO_CONTAINER="slidehub-mongo-smoke"

KEEP_CONTAINERS="${KEEP_SMOKE_CONTAINERS:-false}"
SMOKE_PORT="${SMOKE_PORT:-8080}"
SMOKE_BASE_URL="http://localhost:${SMOKE_PORT}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[ERROR] Missing required command: $1" >&2
    exit 1
  fi
}

cleanup() {
  set +e

  if [[ -n "${MONOLITH_PID}" ]]; then kill "${MONOLITH_PID}" >/dev/null 2>&1; fi

  wait "${MONOLITH_PID:-}" >/dev/null 2>&1 || true

  if [[ "${KEEP_CONTAINERS}" != "true" ]]; then
    docker rm -f "${REDIS_CONTAINER}" "${MONGO_CONTAINER}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

wait_for_http() {
  local url="$1"
  local timeout_sec="${2:-120}"
  local elapsed=0

  until curl -fsS "$url" >/dev/null 2>&1; do
    sleep 1
    elapsed=$((elapsed + 1))
    if (( elapsed >= timeout_sec )); then
      echo "[ERROR] Timeout waiting for ${url}" >&2
      return 1
    fi
  done
}

assert_status() {
  local expected="$1"
  local url="$2"
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' "$url")"
  if [[ "$code" != "$expected" ]]; then
    echo "[ERROR] Expected ${expected} for ${url}, got ${code}" >&2
    return 1
  fi
  echo "[OK] ${url} -> ${code}"
}

assert_contains() {
  local needle="$1"
  local haystack="$2"
  local label="$3"
  if [[ "${haystack}" != *"${needle}"* ]]; then
    echo "[ERROR] ${label} did not contain '${needle}'" >&2
    echo "[DEBUG] ${label}: ${haystack}" >&2
    return 1
  fi
  echo "[OK] ${label} contains '${needle}'"
}

echo "[INFO] SlideHub smoke E2E (monolith) starting..."

require_cmd docker
require_cmd curl
require_cmd ./mvnw

cd "${ROOT_DIR}"

echo "[INFO] Starting Redis + Mongo containers"
docker rm -f "${REDIS_CONTAINER}" "${MONGO_CONTAINER}" >/dev/null 2>&1 || true
docker run -d --name "${REDIS_CONTAINER}" -p 6379:6379 redis:7-alpine >/dev/null
docker run -d --name "${MONGO_CONTAINER}" -p 27017:27017 mongo:7 >/dev/null

echo "[INFO] Starting slidehub-service on port ${SMOKE_PORT}"
PORT="${SMOKE_PORT}" \
DATABASE_URL='jdbc:h2:mem:slidehub;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE' \
DB_DRIVER=org.h2.Driver \
JPA_DIALECT=org.hibernate.dialect.H2Dialect \
SPRING_JPA_HIBERNATE_DDL_AUTO=update \
SPRING_FLYWAY_ENABLED=false \
SPRING_DEVTOOLS_RESTART_ENABLED=false \
MONGODB_URI='mongodb://localhost:27017/slidehub' \
REDIS_HOST='localhost' \
REDIS_PORT='6379' \
./mvnw -q spring-boot:run -pl slidehub-service >"${MONOLITH_LOG}" 2>&1 &
MONOLITH_PID="$!"

echo "[INFO] Waiting for services"
wait_for_http "${SMOKE_BASE_URL}/actuator/health"
wait_for_http "${SMOKE_BASE_URL}/showcase"

echo "[INFO] Running endpoint checks"
assert_status 200 "${SMOKE_BASE_URL}/actuator/health"
assert_status 200 "${SMOKE_BASE_URL}/showcase"
assert_status 200 "${SMOKE_BASE_URL}/api/ai/notes/health"

SLIDE_SET="$(curl -s -X POST "${SMOKE_BASE_URL}/api/slide" -H 'Content-Type: application/json' -d '{"slide":3,"totalSlides":12}')"
assert_contains '"slide":3' "${SLIDE_SET}" "state POST /api/slide"

SLIDE_GET="$(curl -s "${SMOKE_BASE_URL}/api/slide")"
assert_contains '"totalSlides":12' "${SLIDE_GET}" "state GET /api/slide"

HAPTIC_PUBLISH="$(curl -s -X POST "${SMOKE_BASE_URL}/api/haptics/events/publish" -H 'Content-Type: application/json' -d '{"participantToken":"smoke-token","pattern":"triple","message":"Solicitud de ayuda"}')"
assert_contains '"success":true' "${HAPTIC_PUBLISH}" "state POST /api/haptics/events/publish"

HAPTIC_NEXT="$(curl -s "${SMOKE_BASE_URL}/api/haptics/events/next?participantToken=smoke-token")"
assert_contains '"pattern":"triple"' "${HAPTIC_NEXT}" "state GET /api/haptics/events/next"

assert_status 200 "${SMOKE_BASE_URL}/status/api/checks"
assert_status 200 "${SMOKE_BASE_URL}/slides?presentationId=demo-pres"
assert_status 200 "${SMOKE_BASE_URL}/demo?presentationId=demo-pres"
assert_status 200 "${SMOKE_BASE_URL}/remote?presentationId=demo-pres&joinToken=demo-token"
assert_status 404 "${SMOKE_BASE_URL}/api/presentations/demo-pres/slides"
assert_status 400 "${SMOKE_BASE_URL}/api/presentations/demo-pres/meeting/join-options?joinToken=bad"

echo "[INFO] Smoke E2E completed successfully"
echo "[INFO] Logs: ${LOG_DIR}"
if [[ "${KEEP_CONTAINERS}" == "true" ]]; then
  echo "[INFO] Containers kept running (KEEP_SMOKE_CONTAINERS=true)"
fi
