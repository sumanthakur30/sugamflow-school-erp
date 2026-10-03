#!/usr/bin/env bash
# Show which demo groups are up vs stopped.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

group_status() {
  local title="$1"
  shift
  local names=("$@")
  echo "--- $title ---"
  local name cname state
  for name in "${names[@]}"; do
    # match compose project prefixes: sumanthakur30-* or school-*
    cname=$(docker ps -a --format '{{.Names}}' | grep -E "^(sumanthakur30-|school-)${name}-1$" | head -n1 || true)
    if [[ -z "$cname" ]]; then
      printf "  %-40s %s\n" "$name" "missing"
      continue
    fi
    state=$(docker inspect -f '{{.State.Status}}' "$cname" 2>/dev/null || echo unknown)
    printf "  %-40s %s (%s)\n" "$name" "$state" "$cname"
  done
}

group_status "ALWAYS-ON common" "${COMMON_SERVICES[@]}"
group_status "ALWAYS-ON retail" "${RETAIL_SERVICES[@]}"
group_status "DEMO clinic" "${CLINIC_SERVICES[@]}"
group_status "DEMO ipd" "${IPD_SERVICES[@]}"
group_status "DEMO fieldforce" "${FIELDFORCE_SERVICES[@]}"

echo "--- DEMO school (containers) ---"
docker ps -a --format '{{.Names}} {{.Status}}' | grep '^school-' | sort || echo "  (none)"
