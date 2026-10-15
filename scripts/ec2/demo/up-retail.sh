#!/usr/bin/env bash
# Start always-on core + retail shop apps (GEN/MED/CLO/GRO demos).
# Does NOT start school / clinic / IPD / fieldforce.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

ensure_core
echo ""
sf_compose ps "${COMMON_SERVICES[@]}" "${RETAIL_SERVICES[@]}"
echo ""
curl -fsS -o /dev/null -w "gateway health HTTP %{http_code}\n" http://127.0.0.1:9090/actuator/health || true
echo "Done. Retail demos ready (GEN-DEMO-01, MED-DEMO-01, …)."
