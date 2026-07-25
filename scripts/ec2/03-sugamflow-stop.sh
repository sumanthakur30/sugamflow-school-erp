#!/usr/bin/env bash
# Batch 3 — stop SUGAMFLOW-only shop microservices (keep common platform).
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

echo "== SugamFlow app stack STOP =="
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" stop "${SUGAMFLOW_APP_SERVICES[@]}"
echo "Done. Common platform (redis/config/discovery/auth/shop/user/notification/gateway) still running."
