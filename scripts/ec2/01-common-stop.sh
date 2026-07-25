#!/usr/bin/env bash
# Batch 1 — stop SHARED platform.
# WARNING: stops login/gateway for BOTH School and SugamFlow UIs.
# Stop school + sugamflow app batches first.
set -euo pipefail

SUGAMFLOW_DIR="${SUGAMFLOW_DIR:-/opt/sugamflow}"
COMPOSE_FILE="${SUGAMFLOW_COMPOSE:-docker-compose.ec2-rds.yml}"
ENV_FILE="${SUGAMFLOW_ENV:-.env.production}"

cd "$SUGAMFLOW_DIR"

COMMON_SERVICES=(
  gateway-service
  notification-service
  user-service
  shop-service
  auth-service
  discovery-service
  config-service
  redis
)

echo "== Common platform STOP =="
echo "WARNING: School + Shop APIs will go down."
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" stop "${COMMON_SERVICES[@]}"
echo "Done."
