#!/usr/bin/env bash
# Start School ERP demo stack.
# Default PHASE=all (A + B + website/cms). Override: PHASE=a|b|website|all
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

PHASE="${PHASE:-all}"
export SCHOOL_DIR
export SUGAMFLOW_DIR

echo "== Ensure shared platform (login/gateway) =="
bash "$EC2_SCRIPTS_DIR/01-common-start.sh"

echo "== School demo START (PHASE=$PHASE) =="
PHASE="$PHASE" SCHOOL_DIR="$SCHOOL_DIR" bash "$EC2_SCRIPTS_DIR/02-school-start.sh"
echo "Done. School demo ready (school.sugamflow.com)."
