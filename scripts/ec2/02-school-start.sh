#!/usr/bin/env bash
# Batch 2 — start SCHOOL domain microservices.
# Requires common platform UP (gateway :9090 + Eureka).
set -euo pipefail

SCHOOL_DIR="${SCHOOL_DIR:-/opt/school}"
COMPOSE_FILE="${SCHOOL_COMPOSE:-docker-compose.school.ec2-rds.yml}"
ENV_FILE="${SCHOOL_ENV:-.env.school.production}"

cd "$SCHOOL_DIR"

# Order: foundation → domains → ops → comms
SCHOOL_SERVICES=(
  school-settings-service
  subscription-service
  form-builder-service
  workflow-service
  academic-structure-service
  staff-service
  student-service
  admission-service
  fee-service
  attendance-service
  exam-service
  payroll-service
  library-service
  hostel-service
  transport-service
  school-notification-config-service
)

echo "== School stack START =="
echo "dir=$SCHOOL_DIR file=$COMPOSE_FILE env=$ENV_FILE"
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d "${SCHOOL_SERVICES[@]}"
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" ps

echo ""
echo "Smoke (via gateway):"
for path in /api/student/health /api/library/bootstrap /api/hostel/bootstrap /api/transport/bootstrap /api/school/notification-config/comms/bootstrap; do
  code=$(curl -sS -o /dev/null -w "%{http_code}" "http://127.0.0.1:9090$path" || echo "err")
  echo "  $path -> $code"
done
echo "Done."
