#!/usr/bin/env bash
# Batch 2 - stop SCHOOL domain microservices only (shop stack untouched).
set -euo pipefail

SCHOOL_DIR="${SCHOOL_DIR:-/home/ec2-user/opt/school}"
COMPOSE_FILE="${SCHOOL_COMPOSE:-docker-compose.school.ec2-rds.yml}"
ENV_FILE="${SCHOOL_ENV:-.env.school.production}"

cd "$SCHOOL_DIR"

echo "== School stack STOP =="
# Include phase-b so optional services are stopped too when they were started
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" --profile phase-b stop
echo "Done. SugamFlow shop services are unchanged."
