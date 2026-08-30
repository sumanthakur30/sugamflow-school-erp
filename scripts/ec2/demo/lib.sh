#!/usr/bin/env bash
# Shared helpers for demo start/stop scripts.
set -euo pipefail

SUGAMFLOW_DIR="${SUGAMFLOW_DIR:-/opt/sugamflow}"
SCHOOL_DIR="${SCHOOL_DIR:-/opt/school}"
SUGAMFLOW_COMPOSE="${SUGAMFLOW_COMPOSE:-docker-compose.ec2-rds.yml}"
SUGAMFLOW_IPD_COMPOSE="${SUGAMFLOW_IPD_COMPOSE:-docker-compose.ec2-ipd.yml}"
SUGAMFLOW_ENV="${SUGAMFLOW_ENV:-.env.production}"
SCHOOL_COMPOSE="${SCHOOL_COMPOSE:-docker-compose.school.ec2-rds.yml}"
SCHOOL_ENV="${SCHOOL_ENV:-.env.school.production}"
EC2_SCRIPTS_DIR="${EC2_SCRIPTS_DIR:-/opt/school/scripts/ec2}"

# Always-on for retail / login / Super Admin basics
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

RETAIL_SERVICES=(
  product-service
  stock-service
  order-service
  payment-service
  reporting-service
  account-service
  gst-service
  ledger-service
)

CLINIC_SERVICES=(
  doctor-service
  appointment-service
  queue-management-service
)

IPD_SERVICES=(
  ipd-service
  accommodation-service
)

FIELDFORCE_SERVICES=(
  fieldforce-service
)

sf_compose() {
  docker compose -f "$SUGAMFLOW_DIR/$SUGAMFLOW_COMPOSE" --env-file "$SUGAMFLOW_DIR/$SUGAMFLOW_ENV" "$@"
}

sf_compose_ipd() {
  docker compose \
    -f "$SUGAMFLOW_DIR/$SUGAMFLOW_COMPOSE" \
    -f "$SUGAMFLOW_DIR/$SUGAMFLOW_IPD_COMPOSE" \
    --env-file "$SUGAMFLOW_DIR/$SUGAMFLOW_ENV" "$@"
}

school_compose() {
  docker compose -f "$SCHOOL_DIR/$SCHOOL_COMPOSE" --env-file "$SCHOOL_DIR/$SCHOOL_ENV" "$@"
}

ensure_core() {
  echo "== Ensure always-on core (common + retail) =="
  sf_compose up -d "${COMMON_SERVICES[@]}" "${RETAIL_SERVICES[@]}"
}
