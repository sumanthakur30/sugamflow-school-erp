#!/usr/bin/env bash
# Pull ALL School images from Docker Hub and recreate containers on EC2.
#
# Prerequisites:
#   - /opt/school/docker-compose.school.ec2-rds.yml
#   - /opt/school/.env.school.production with IMAGE_TAG matching what you pushed
#
# Usage:
#   cd /opt/school
#   bash scripts/ec2/04-pull-recreate-all-school.sh          # Phase A + B
#   PHASE=a bash scripts/ec2/04-pull-recreate-all-school.sh  # Phase A only
#   PHASE=b bash scripts/ec2/04-pull-recreate-all-school.sh  # A+B (default)
set -euo pipefail

SCHOOL_DIR="${SCHOOL_DIR:-/opt/school}"
COMPOSE_FILE="${SCHOOL_COMPOSE:-docker-compose.school.ec2-rds.yml}"
ENV_FILE="${SCHOOL_ENV:-.env.school.production}"
PHASE="${PHASE:-b}"

cd "$SCHOOL_DIR"

if [[ ! -f "$COMPOSE_FILE" || ! -f "$ENV_FILE" ]]; then
  echo "Missing $COMPOSE_FILE or $ENV_FILE in $SCHOOL_DIR" >&2
  exit 1
fi

echo "== IMAGE_TAG from env =="
grep -E '^IMAGE_TAG=|^IMAGE_PREFIX=' "$ENV_FILE" || true

PHASE_A=(
  school-settings-service
  subscription-service
  form-builder-service
  workflow-service
  academic-structure-service
  staff-service
  student-service
  admission-service
  fee-service
)

PHASE_B=(
  attendance-service
  exam-service
  payroll-service
  library-service
  hostel-service
  transport-service
  school-notification-config-service
)

SERVICES=("${PHASE_A[@]}")
PROFILES_ARGS=()
if [[ "$PHASE" == "b" || "$PHASE" == "all" || "$PHASE" == "phase-b" ]]; then
  SERVICES+=("${PHASE_B[@]}")
  PROFILES_ARGS=(--profile phase-b)
fi

echo ""
echo "== Pull (${#SERVICES[@]} services, PHASE=$PHASE) =="
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "${PROFILES_ARGS[@]}" pull "${SERVICES[@]}"

echo ""
echo "== Recreate =="
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "${PROFILES_ARGS[@]}" \
  up -d --force-recreate "${SERVICES[@]}"

echo ""
echo "== Status =="
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "${PROFILES_ARGS[@]}" ps

echo ""
echo "== Spot-check subscription (wait ~45s if just started) =="
sleep 15
cid=$(docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" ps -q subscription-service || true)
if [[ -n "$cid" ]]; then
  st=$(docker inspect -f '{{.State.Status}}' "$cid" 2>/dev/null || echo unknown)
  echo "subscription-service state=$st"
  if [[ "$st" == "running" ]]; then
    docker exec "$cid" sh -c 'wc -c < /app/app.jar; curl -sf http://127.0.0.1:8182/actuator/health || true'
  else
    echo "Not running — logs:"
    docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" logs --tail=80 subscription-service || true
  fi
else
  echo "subscription-service container id not found"
fi

echo ""
echo "Done. If subscription was Restarting due to Flyway V5 conflict, run on RDS school_subscription_db:"
echo "  DELETE FROM flyway_schema_history_school_subscription WHERE version='5' AND success=false;"
echo "  DROP TABLE IF EXISTS tenant_subscription_lifecycle CASCADE;"
echo "Then: docker compose ... up -d --force-recreate subscription-service"
