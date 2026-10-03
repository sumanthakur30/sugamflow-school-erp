#!/usr/bin/env bash
# Start hospital IPD demo (ipd + accommodation overlay).
# Also starts clinic services (appointments often used with IPD demos).
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

ensure_core
echo "== Clinic deps for IPD =="
sf_compose up -d "${CLINIC_SERVICES[@]}"
echo "== IPD overlay START =="
sf_compose_ipd up -d "${IPD_SERVICES[@]}"
sf_compose_ipd ps "${IPD_SERVICES[@]}" "${CLINIC_SERVICES[@]}"
echo "Done. IPD demo services are up."
