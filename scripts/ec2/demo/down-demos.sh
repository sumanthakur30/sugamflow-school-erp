#!/usr/bin/env bash
# Stop demo-only stacks; keep always-on common + retail running.
#
# Stops: school (all phases), clinic, IPD, fieldforce
# Keeps: redis/config/discovery/auth/shop/user/notification/gateway + retail apps
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

echo "== School STOP =="
if [[ -d "$SCHOOL_DIR" ]]; then
  SCHOOL_DIR="$SCHOOL_DIR" bash "$EC2_SCRIPTS_DIR/02-school-stop.sh" || true
  # website/cms profile may not be covered by phase-b stop alone
  school_compose --profile phase-b --profile website stop 2>/dev/null || true
else
  echo "skip: $SCHOOL_DIR missing"
fi

echo "== Clinic / IPD / Fieldforce STOP =="
sf_compose_ipd stop "${IPD_SERVICES[@]}" 2>/dev/null || true
sf_compose stop "${CLINIC_SERVICES[@]}" "${FIELDFORCE_SERVICES[@]}" 2>/dev/null || true

echo ""
echo "Always-on left running (common + retail)."
sf_compose ps "${COMMON_SERVICES[@]}" "${RETAIL_SERVICES[@]}" || true
echo "Done."
