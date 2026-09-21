#!/usr/bin/env bash
# Diagnose FEATURE_ADMISSION for HCP-01 on EC2.
# Usage:  cd /opt/school && bash scripts/ec2/diagnose-admission-HCP-01.sh
set -euo pipefail

cd "${SCHOOL_DIR:-/opt/school}"
ENV_FILE="${SCHOOL_ENV:-.env.school.production}"
COMPOSE="${SCHOOL_COMPOSE:-docker-compose.school.ec2-rds.yml}"
ORG="${ORG_ID:-HCP-01}"

echo "== Paths =="
pwd
ls -la "$ENV_FILE" "$COMPOSE" 2>&1 | head -20

echo ""
echo "== SCHOOL_SUBSCRIPTION_DB_URL =="
grep -E '^SCHOOL_SUBSCRIPTION_DB_' "$ENV_FILE" | sed 's/PASSWORD=.*/PASSWORD=***/'

echo ""
echo "== Containers =="
docker compose -f "$COMPOSE" --env-file "$ENV_FILE" ps subscription-service admission-service

echo ""
echo "== Feature flag from subscription-service (X-Tenant-Id=$ORG) =="
cid=$(docker compose -f "$COMPOSE" --env-file "$ENV_FILE" ps -q subscription-service)
if [[ -z "$cid" ]]; then
  echo "subscription-service not running"
  exit 1
fi

# Prefer curl, fall back to wget
set +e
out=$(docker exec "$cid" sh -c \
  "if command -v curl >/dev/null 2>&1; then \
     curl -sS -H 'X-Tenant-Id: $ORG' http://127.0.0.1:8182/api/subscription/feature-flags/FEATURE_ADMISSION; \
   elif command -v wget >/dev/null 2>&1; then \
     wget -qO- --header='X-Tenant-Id: $ORG' http://127.0.0.1:8182/api/subscription/feature-flags/FEATURE_ADMISSION; \
   else echo 'NO_CURL_OR_WGET'; fi")
rc=$?
set -e
echo "$out"
echo "(exit=$rc)"

echo ""
echo "== Entitlements =="
docker exec "$cid" sh -c \
  "curl -sS -H 'X-Tenant-Id: $ORG' http://127.0.0.1:8182/api/subscription/tenants/current/entitlements 2>/dev/null \
   || wget -qO- --header='X-Tenant-Id: $ORG' http://127.0.0.1:8182/api/subscription/tenants/current/entitlements 2>/dev/null \
   || true" | head -c 2000
echo ""

echo ""
echo "== SQL inside subscription container (psql if present; else print JDBC hint) =="
# Use a one-shot postgres client on the docker network if available
set +e
# shellcheck disable=SC1090
set -a; source "$ENV_FILE"; set +a
jdbc="${SCHOOL_SUBSCRIPTION_DB_URL:-}"
user="${SCHOOL_SUBSCRIPTION_DB_USERNAME:-school_subscription}"
pass="${SCHOOL_SUBSCRIPTION_DB_PASSWORD:-}"
# parse jdbc:postgresql://host:5432/db?...
rest="${jdbc#jdbc:postgresql://}"
hostport="${rest%%/*}"
dbq="${rest#*/}"
db="${dbq%%\?*}"
host="${hostport%%:*}"
port="${hostport##*:}"
echo "JDBC host=$host port=$port db=$db user=$user"

docker run --rm --network sumanthakur30_default \
  -e PGPASSWORD="$pass" -e PGSSLMODE=require \
  postgres:15-alpine \
  psql -h "$host" -p "$port" -U "$user" -d "$db" -v ON_ERROR_STOP=1 -c \
  "SELECT ts.organization_id, ts.plan_id,
          sp.feature_flags_json->'FEATURE_ADMISSION' AS admission,
          jsonb_typeof(sp.feature_flags_json->'FEATURE_ADMISSION') AS typ
   FROM tenant_subscription ts
   JOIN subscription_plan sp ON sp.id = ts.plan_id
   WHERE ts.organization_id = '$ORG';" 2>&1
set -e

echo ""
echo "== Recent admission-service errors =="
docker compose -f "$COMPOSE" --env-file "$ENV_FILE" logs --tail=80 admission-service 2>&1 \
  | grep -iE 'FEATURE|subscription|8182|error|exception|HCP' || true

echo ""
echo "Done. If SQL admission=true but feature-flags API enabled=false → Redis/cache bug."
echo "If SQL has no row / admission false → fix DB (run enable-full-school-access-HCP-01.sql on THIS host/db)."
echo "If API enabled=true but UI still fails → recreate admission-service and hard-refresh browser."
