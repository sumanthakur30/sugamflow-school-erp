#!/usr/bin/env bash
# Start clinic / OPD demo services (doctor + appointments + queue).
# Ensures retail core is up first.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

ensure_core
echo "== Clinic / OPD START =="
sf_compose up -d "${CLINIC_SERVICES[@]}"
sf_compose ps "${CLINIC_SERVICES[@]}"
echo "Done. Clinic demo services are up."
