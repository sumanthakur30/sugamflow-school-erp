#!/usr/bin/env bash
# Start fieldforce / CRM field demo service.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

ensure_core
echo "== Fieldforce START =="
sf_compose up -d "${FIELDFORCE_SERVICES[@]}"
sf_compose ps "${FIELDFORCE_SERVICES[@]}"
echo "Done. Fieldforce demo service is up."
