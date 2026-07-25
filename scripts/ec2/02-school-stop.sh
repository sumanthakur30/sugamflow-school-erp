#!/usr/bin/env bash
# Batch 2 — stop SCHOOL domain microservices only (shop stack untouched).
set -euo pipefail

SCHOOL_DIR="${SCHOOL_DIR:-/opt/school}"
COMPOSE_FILE="${SCHOOL_COMPOSE:-docker-compose.school.ec2-rds.yml}"
ENV_FILE="${SCHOOL_ENV:-.env.school.production}"

cd "$SCHOOL_DIR"

echo "== School stack STOP =="
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" stop
echo "Done. SugamFlow shop services are unchanged."
