#!/usr/bin/env bash
# Batch 2 - start SCHOOL domain microservices.
# Requires common platform UP (gateway :9090 + Eureka).
#
# Usage:
#   bash 02-school-start.sh              # Phase A only (default)
#   PHASE=b bash 02-school-start.sh      # Phase A + Phase B
#   PHASE=website bash 02-school-start.sh  # Phase A + website-service + cms-service
#   PHASE=all bash 02-school-start.sh    # Phase A + B + website
# HCP public site full steps: docs/HCP_EC2_WEBSITE_DEPLOY.md
set -euo pipefail

SCHOOL_DIR="${SCHOOL_DIR:-/home/ec2-user/opt/school}"
COMPOSE_FILE="${SCHOOL_COMPOSE:-docker-compose.school.ec2-rds.yml}"
ENV_FILE="${SCHOOL_ENV:-.env.school.production}"
PHASE="${PHASE:-a}"

cd "$SCHOOL_DIR"

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

WEBSITE=(
  website-service
  cms-service
)

SERVICES=("${PHASE_A[@]}")
PROFILES_ARGS=()
if [[ "$PHASE" == "b" || "$PHASE" == "phase-b" ]]; then
  SERVICES+=("${PHASE_B[@]}")
  PROFILES_ARGS=(--profile phase-b)
elif [[ "$PHASE" == "website" ]]; then
  SERVICES+=("${WEBSITE[@]}")
  PROFILES_ARGS=(--profile website)
elif [[ "$PHASE" == "all" ]]; then
  SERVICES+=("${PHASE_B[@]}" "${WEBSITE[@]}")
  PROFILES_ARGS=(--profile phase-b --profile website)
fi

echo "== School stack START (PHASE=$PHASE) =="
echo "dir=$SCHOOL_DIR file=$COMPOSE_FILE env=$ENV_FILE"
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "${PROFILES_ARGS[@]}" up -d "${SERVICES[@]}"
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "${PROFILES_ARGS[@]}" ps

echo ""
echo "Smoke (via gateway):"
for path in /api/student/health /api/config/design-studio/theme; do
  code=$(curl -sS -o /dev/null -w "%{http_code}" "http://127.0.0.1:9090$path" || echo "err")
  echo "  $path -> $code"
done
if [[ "$PHASE" == "website" || "$PHASE" == "all" ]]; then
  code=$(curl -sS -o /dev/null -w "%{http_code}" \
    "http://127.0.0.1:9090/api/website/public/resolve?host=hcpschool.com" || echo "err")
  echo "  /api/website/public/resolve?host=hcpschool.com -> $code"
fi
echo "Done."
