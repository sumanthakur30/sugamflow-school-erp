#!/usr/bin/env bash
# Batch 1 — start SHARED platform (SugamFlow + School both need these).
# Run from /opt/sugamflow (or set SUGAMFLOW_DIR).
set -euo pipefail

SUGAMFLOW_DIR="${SUGAMFLOW_DIR:-/opt/sugamflow}"
COMPOSE_FILE="${SUGAMFLOW_COMPOSE:-docker-compose.ec2-rds.yml}"
ENV_FILE="${SUGAMFLOW_ENV:-.env.production}"

cd "$SUGAMFLOW_DIR"

COMMON_SERVICES=(
  redis
  config-service
  discovery-service
  auth-service
  shop-service
  user-service
  notification-service
  gateway-service
)

echo "== Common platform START =="
echo "dir=$SUGAMFLOW_DIR file=$COMPOSE_FILE env=$ENV_FILE"
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d "${COMMON_SERVICES[@]}"
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" ps "${COMMON_SERVICES[@]}"

echo ""
echo "Smoke:"
curl -fsS -o /dev/null -w "gateway health HTTP %{http_code}\n" http://127.0.0.1:9090/actuator/health || true
echo "Done. Next: school and/or sugamflow batch scripts."
