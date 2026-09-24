#!/usr/bin/env bash
# Batch 3 — start SUGAMFLOW-only shop microservices (not shared platform).
# Requires common platform UP (gateway + Eureka + auth/shop).
set -euo pipefail

SUGAMFLOW_DIR="${SUGAMFLOW_DIR:-/opt/sugamflow}"
COMPOSE_FILE="${SUGAMFLOW_COMPOSE:-docker-compose.ec2-rds.yml}"
ENV_FILE="${SUGAMFLOW_ENV:-.env.production}"

cd "$SUGAMFLOW_DIR"

SUGAMFLOW_APP_SERVICES=(
  product-service
  stock-service
  order-service
  payment-service
  reporting-service
  account-service
  fieldforce-service
  gst-service
  ledger-service
  doctor-service
  appointment-service
  queue-management-service
)

echo "== SugamFlow app stack START =="
echo "dir=$SUGAMFLOW_DIR file=$COMPOSE_FILE env=$ENV_FILE"
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d "${SUGAMFLOW_APP_SERVICES[@]}"
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" ps "${SUGAMFLOW_APP_SERVICES[@]}"
echo "Done. Shared platform (gateway/auth/shop) was not restarted."
