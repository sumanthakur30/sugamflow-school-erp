#!/usr/bin/env bash
# Full EC2 deploy (pull + start batches) and Flyway version check via psql.
#
# Prerequisites on EC2:
#   - Docker Compose
#   - Empty RDS DBs already created (see infra SQL scripts)
#   - /home/ec2-user/opt/sugamflow/.env.production
#   - /home/ec2-user/opt/school/.env.school.production
#   - This folder scripts/ec2/*.sh are executable
#
# Usage (on EC2):
#   cd /home/ec2-user/opt/school
#   bash scripts/ec2/00-full-deploy-and-flyway-check.sh
#
# Options (env vars):
#   SKIP_PULL=1          skip docker compose pull
#   SKIP_START=1         only run Flyway checks (no up -d)
#   CHECK_ONLY=1         alias for SKIP_PULL=1 SKIP_START=1
#   WITH_IPD=1           also start IPD overlay (needs docker-compose.ec2-ipd.yml)
#   SCHOOL_PHASE=a|b     default a; use b for Phase A+B
#   WAIT_SECONDS=90      sleep after start before Flyway queries
#   SUGAMFLOW_DIR=...    default /home/ec2-user/opt/sugamflow
#   SCHOOL_DIR=...       default /home/ec2-user/opt/school
#   PSQL_IMAGE=postgres:15-alpine
#
# Exit: 0 if all expected Flyway max versions match; 1 if any miss/fail.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SUGAMFLOW_DIR="${SUGAMFLOW_DIR:-/home/ec2-user/opt/sugamflow}"
SCHOOL_DIR="${SCHOOL_DIR:-/home/ec2-user/opt/school}"
SUGAMFLOW_COMPOSE="${SUGAMFLOW_COMPOSE:-docker-compose.ec2-rds.yml}"
SUGAMFLOW_IPD_COMPOSE="${SUGAMFLOW_IPD_COMPOSE:-docker-compose.ec2-ipd.yml}"
SUGAMFLOW_ENV="${SUGAMFLOW_ENV:-.env.production}"
SCHOOL_COMPOSE="${SCHOOL_COMPOSE:-docker-compose.school.ec2-rds.yml}"
SCHOOL_ENV="${SCHOOL_ENV:-.env.school.production}"
SCHOOL_PHASE="${SCHOOL_PHASE:-a}"
WAIT_SECONDS="${WAIT_SECONDS:-90}"
PSQL_IMAGE="${PSQL_IMAGE:-postgres:15-alpine}"
WITH_IPD="${WITH_IPD:-0}"
SKIP_PULL="${SKIP_PULL:-0}"
SKIP_START="${SKIP_START:-0}"

if [[ "${CHECK_ONLY:-0}" == "1" ]]; then
  SKIP_PULL=1
  SKIP_START=1
fi

RED=$'\033[0;31m'
GRN=$'\033[0;32m'
YLW=$'\033[0;33m'
NC=$'\033[0m'

fail_count=0
pass_count=0
skip_count=0

die() { echo "${RED}ERROR:${NC} $*" >&2; exit 1; }

need_file() {
  [[ -f "$1" ]] || die "Missing file: $1"
}

load_env_file() {
  local f="$1" line key val
  need_file "$f"
  # Do NOT `source` the file: unquoted JAVA_TOOL_OPTIONS=-Xms… breaks bash
  # ("-Xms256m: command not found"). Parse KEY=VALUE with value = rest of line.
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line//[[:space:]]/}" ]] && continue
    [[ "$line" =~ ^[[:space:]]*# ]] && continue
    [[ "$line" != *=* ]] && continue
    key="${line%%=*}"
    val="${line#*=}"
    key="${key%"${key##*[![:space:]]}"}"
    key="${key#"${key%%[![:space:]]*}"}"
    [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    if [[ "$val" == \"*\" && "$val" == *\" ]]; then
      val="${val:1:${#val}-2}"
    elif [[ "$val" == \'*\' && "$val" == *\' ]]; then
      val="${val:1:${#val}-2}"
    fi
    printf -v "$key" '%s' "$val"
    export "$key"
  done < "$f"
}

# jdbc:postgresql://host:5432/dbname?sslmode=require  →  host port dbname
parse_jdbc() {
  local url="$1"
  local rest hostport dbq db host port
  rest="${url#jdbc:postgresql://}"
  hostport="${rest%%/*}"
  dbq="${rest#*/}"
  db="${dbq%%\?*}"
  if [[ "$hostport" == *:* ]]; then
    host="${hostport%%:*}"
    port="${hostport##*:}"
  else
    host="$hostport"
    port="5432"
  fi
  printf '%s\t%s\t%s' "$host" "$port" "$db"
}

# Run one SQL query against RDS using dockerized psql (ssl).
psql_q() {
  local host="$1" port="$2" db="$3" user="$4" pass="$5" sql="$6"
  docker run --rm --network host \
    -e PGPASSWORD="$pass" \
    -e PGSSLMODE=require \
    "$PSQL_IMAGE" \
    psql -h "$host" -p "$port" -U "$user" -d "$db" -v ON_ERROR_STOP=1 -Atc "$sql" 2>/dev/null
}

check_flyway() {
  local label="$1" jdbc_url="$2" user="$3" pass="$4" history_table="$5" expect_max="$6"
  # Fall back password = username when env omits password (compose local default pattern).
  if [[ -z "${pass:-}" && -n "${user:-}" ]]; then
    pass="$user"
  fi
  if [[ -z "${jdbc_url:-}" || -z "${user:-}" || -z "${pass:-}" ]]; then
    echo "  ${YLW}SKIP${NC}  $label (missing JDBC/user/pass in env)"
    skip_count=$((skip_count + 1))
    return 0
  fi

  local parsed host port db got
  parsed="$(parse_jdbc "$jdbc_url")"
  host="$(echo "$parsed" | cut -f1)"
  port="$(echo "$parsed" | cut -f2)"
  db="$(echo "$parsed" | cut -f3)"

  local sql
  sql="SELECT COALESCE(MAX(CAST(version AS INTEGER)), -1) FROM ${history_table} WHERE success = true AND version ~ '^[0-9]+\$';"

  if ! got="$(psql_q "$host" "$port" "$db" "$user" "$pass" "$sql")"; then
    echo "  ${RED}FAIL${NC}  $label  (cannot query ${db}.${history_table})"
    fail_count=$((fail_count + 1))
    return 0
  fi
  got="$(echo "$got" | tr -d '[:space:]')"
  if [[ -z "$got" ]]; then
    got="-1"
  fi

  if [[ "$got" == "$expect_max" ]]; then
    echo "  ${GRN}OK${NC}    $label  max=V${got} (expected V${expect_max})"
    pass_count=$((pass_count + 1))
  elif [[ "$got" == "-1" ]]; then
    echo "  ${RED}FAIL${NC}  $label  no successful Flyway rows (expected V${expect_max})"
    fail_count=$((fail_count + 1))
  elif [[ "$got" -lt "$expect_max" ]]; then
    echo "  ${RED}FAIL${NC}  $label  max=V${got} < expected V${expect_max} (image likely outdated)"
    fail_count=$((fail_count + 1))
  else
    echo "  ${YLW}WARN${NC}  $label  max=V${got} > expected V${expect_max} (repo checklist stale?)"
    pass_count=$((pass_count + 1))
  fi
}

echo "=============================================="
echo " SugamFlow + School — full deploy / Flyway check"
echo "=============================================="
echo "SUGAMFLOW_DIR=$SUGAMFLOW_DIR"
echo "SCHOOL_DIR=$SCHOOL_DIR"
echo "SCHOOL_PHASE=$SCHOOL_PHASE  WITH_IPD=$WITH_IPD"
echo "SKIP_PULL=$SKIP_PULL  SKIP_START=$SKIP_START  WAIT_SECONDS=$WAIT_SECONDS"
echo ""

need_file "$SUGAMFLOW_DIR/$SUGAMFLOW_COMPOSE"
need_file "$SUGAMFLOW_DIR/$SUGAMFLOW_ENV"
need_file "$SCHOOL_DIR/$SCHOOL_COMPOSE"
need_file "$SCHOOL_DIR/$SCHOOL_ENV"
need_file "$SCRIPT_DIR/01-common-start.sh"
need_file "$SCRIPT_DIR/03-sugamflow-start.sh"
need_file "$SCRIPT_DIR/02-school-start.sh"

# --- Pull ---
if [[ "$SKIP_PULL" != "1" ]]; then
  echo "== Pull SugamFlow images =="
  (
    cd "$SUGAMFLOW_DIR"
    docker compose -f "$SUGAMFLOW_COMPOSE" --env-file "$SUGAMFLOW_ENV" pull
    if [[ "$WITH_IPD" == "1" && -f "$SUGAMFLOW_IPD_COMPOSE" ]]; then
      docker compose -f "$SUGAMFLOW_COMPOSE" -f "$SUGAMFLOW_IPD_COMPOSE" --env-file "$SUGAMFLOW_ENV" pull
    fi
  )
  echo "== Pull School images =="
  (
    cd "$SCHOOL_DIR"
    profile_args=()
    if [[ "$SCHOOL_PHASE" == "b" || "$SCHOOL_PHASE" == "all" || "$SCHOOL_PHASE" == "phase-b" ]]; then
      profile_args=(--profile phase-b)
    fi
    docker compose -f "$SCHOOL_COMPOSE" --env-file "$SCHOOL_ENV" "${profile_args[@]}" pull
  )
else
  echo "== Skip pull =="
fi

# --- Start ---
if [[ "$SKIP_START" != "1" ]]; then
  echo ""
  echo "== Batch 1: common platform =="
  SUGAMFLOW_DIR="$SUGAMFLOW_DIR" SUGAMFLOW_COMPOSE="$SUGAMFLOW_COMPOSE" SUGAMFLOW_ENV="$SUGAMFLOW_ENV" \
    bash "$SCRIPT_DIR/01-common-start.sh"

  echo ""
  echo "== Batch 3: SugamFlow apps =="
  SUGAMFLOW_DIR="$SUGAMFLOW_DIR" SUGAMFLOW_COMPOSE="$SUGAMFLOW_COMPOSE" SUGAMFLOW_ENV="$SUGAMFLOW_ENV" \
    bash "$SCRIPT_DIR/03-sugamflow-start.sh"

  if [[ "$WITH_IPD" == "1" ]]; then
    echo ""
    echo "== Optional: IPD overlay =="
    need_file "$SUGAMFLOW_DIR/$SUGAMFLOW_IPD_COMPOSE"
    (
      cd "$SUGAMFLOW_DIR"
      docker compose -f "$SUGAMFLOW_COMPOSE" -f "$SUGAMFLOW_IPD_COMPOSE" --env-file "$SUGAMFLOW_ENV" up -d
    )
  fi

  echo ""
  echo "== Batch 2: School (PHASE=$SCHOOL_PHASE) =="
  SCHOOL_DIR="$SCHOOL_DIR" SCHOOL_COMPOSE="$SCHOOL_COMPOSE" SCHOOL_ENV="$SCHOOL_ENV" PHASE="$SCHOOL_PHASE" \
    bash "$SCRIPT_DIR/02-school-start.sh"

  echo ""
  echo "Waiting ${WAIT_SECONDS}s for Flyway / Eureka..."
  sleep "$WAIT_SECONDS"
else
  echo "== Skip start =="
fi

# --- Load envs for JDBC checks ---
echo ""
echo "== Flyway checks (SugamFlow) =="
load_env_file "$SUGAMFLOW_DIR/$SUGAMFLOW_ENV"

# Expected max versions = latest Vn in repo as of script authoring (2026-08).
# Label | JDBC | user | pass | history table | expected max
check_flyway "auth-service" \
  "${AUTH_DB_URL:-}" "${AUTH_DB_USERNAME:-authdb}" "${AUTH_DB_PASSWORD:-}" \
  "flyway_schema_history_auth" 12

check_flyway "shop-service" \
  "${SHOP_DB_URL:-}" "${SHOP_DB_USERNAME:-shopdb}" "${SHOP_DB_PASSWORD:-}" \
  "flyway_schema_history_shop" 17

check_flyway "user-service" \
  "${USER_DB_URL:-}" "${USER_DB_USERNAME:-userdb}" "${USER_DB_PASSWORD:-}" \
  "flyway_schema_history_user" 13

check_flyway "notification-service" \
  "${NOTIFICATION_DB_URL:-}" "${NOTIFICATION_DB_USERNAME:-notificationdb}" "${NOTIFICATION_DB_PASSWORD:-}" \
  "flyway_schema_history_notification" 4

check_flyway "product-service" \
  "${PRODUCT_DB_URL:-}" "${PRODUCT_DB_USERNAME:-productdb}" "${PRODUCT_DB_PASSWORD:-}" \
  "flyway_schema_history_product" 23

check_flyway "stock-service" \
  "${STOCK_DB_URL:-}" "${STOCK_DB_USERNAME:-stockdb}" "${STOCK_DB_PASSWORD:-}" \
  "flyway_schema_history_stock" 40

check_flyway "order-service" \
  "${ORDER_DB_URL:-}" "${ORDER_DB_USERNAME:-orderdb}" "${ORDER_DB_PASSWORD:-}" \
  "flyway_schema_history_order" 81

check_flyway "payment-service" \
  "${PAYMENT_DB_URL:-}" "${PAYMENT_DB_USERNAME:-paymentdb}" "${PAYMENT_DB_PASSWORD:-}" \
  "flyway_schema_history_payment" 1

check_flyway "reporting-service" \
  "${REPORTING_DB_URL:-}" "${REPORTING_DB_USERNAME:-reportingdb}" "${REPORTING_DB_PASSWORD:-}" \
  "flyway_schema_history_reporting" 1

check_flyway "account-service" \
  "${ACCOUNT_DB_URL:-}" "${ACCOUNT_DB_USERNAME:-accountdb}" "${ACCOUNT_DB_PASSWORD:-}" \
  "flyway_schema_history_account" 1

check_flyway "fieldforce-service" \
  "${FIELDFORCE_DB_URL:-}" "${FIELDFORCE_DB_USERNAME:-fieldforcedb}" "${FIELDFORCE_DB_PASSWORD:-}" \
  "flyway_schema_history_fieldforce" 7

check_flyway "gst-service" \
  "${GST_DB_URL:-}" "${GST_DB_USERNAME:-gstdb}" "${GST_DB_PASSWORD:-}" \
  "flyway_schema_history_gst" 6

check_flyway "ledger-service" \
  "${LEDGER_DB_URL:-}" "${LEDGER_DB_USERNAME:-ledgerdb}" "${LEDGER_DB_PASSWORD:-}" \
  "flyway_schema_history_ledger" 3

check_flyway "doctor-service" \
  "${DOCTOR_DB_URL:-}" "${DOCTOR_DB_USERNAME:-doctordb}" "${DOCTOR_DB_PASSWORD:-}" \
  "flyway_schema_history_doctor" 1

check_flyway "appointment-service" \
  "${APPOINTMENT_DB_URL:-}" "${APPOINTMENT_DB_USERNAME:-appointmentdb}" "${APPOINTMENT_DB_PASSWORD:-}" \
  "flyway_schema_history_appointment" 3

check_flyway "queue-management-service" \
  "${QUEUE_DB_URL:-}" "${QUEUE_DB_USERNAME:-queuedb}" "${QUEUE_DB_PASSWORD:-}" \
  "flyway_schema_history_queue" 1

if [[ "$WITH_IPD" == "1" || -n "${IPD_DB_URL:-}" ]]; then
  check_flyway "ipd-service" \
    "${IPD_DB_URL:-}" "${IPD_DB_USERNAME:-}" "${IPD_DB_PASSWORD:-}" \
    "flyway_schema_history_ipd" 18
  check_flyway "accommodation-service" \
    "${IPD_DB_URL:-}" "${IPD_DB_USERNAME:-}" "${IPD_DB_PASSWORD:-}" \
    "flyway_schema_history_accommodation" 2
fi

echo ""
echo "== Flyway checks (School) =="
load_env_file "$SCHOOL_DIR/$SCHOOL_ENV"

check_flyway "school-settings-service" \
  "${SCHOOL_SETTINGS_DB_URL:-}" "${SCHOOL_SETTINGS_DB_USERNAME:-school_settings}" "${SCHOOL_SETTINGS_DB_PASSWORD:-}" \
  "flyway_schema_history_school_settings" 5

check_flyway "subscription-service" \
  "${SCHOOL_SUBSCRIPTION_DB_URL:-}" "${SCHOOL_SUBSCRIPTION_DB_USERNAME:-school_subscription}" "${SCHOOL_SUBSCRIPTION_DB_PASSWORD:-}" \
  "flyway_schema_history_school_subscription" 18

check_flyway "form-builder-service" \
  "${SCHOOL_FORMS_DB_URL:-}" "${SCHOOL_FORMS_DB_USERNAME:-school_forms}" "${SCHOOL_FORMS_DB_PASSWORD:-}" \
  "flyway_schema_history_school_forms" 2

check_flyway "workflow-service" \
  "${SCHOOL_WORKFLOW_DB_URL:-}" "${SCHOOL_WORKFLOW_DB_USERNAME:-school_workflow}" "${SCHOOL_WORKFLOW_DB_PASSWORD:-}" \
  "flyway_schema_history_school_workflow" 2

check_flyway "academic-structure-service" \
  "${SCHOOL_ACADEMIC_DB_URL:-}" "${SCHOOL_ACADEMIC_DB_USERNAME:-school_academic}" "${SCHOOL_ACADEMIC_DB_PASSWORD:-}" \
  "flyway_schema_history_school_academic" 4

check_flyway "staff-service" \
  "${SCHOOL_STAFF_DB_URL:-}" "${SCHOOL_STAFF_DB_USERNAME:-school_staff}" "${SCHOOL_STAFF_DB_PASSWORD:-}" \
  "flyway_schema_history_school_staff" 2

check_flyway "student-service" \
  "${SCHOOL_STUDENT_DB_URL:-}" "${SCHOOL_STUDENT_DB_USERNAME:-school_student}" "${SCHOOL_STUDENT_DB_PASSWORD:-}" \
  "flyway_schema_history_school_student" 8

check_flyway "admission-service" \
  "${SCHOOL_ADMISSION_DB_URL:-}" "${SCHOOL_ADMISSION_DB_USERNAME:-school_admission}" "${SCHOOL_ADMISSION_DB_PASSWORD:-}" \
  "flyway_schema_history_school_admission" 3

check_flyway "fee-service" \
  "${SCHOOL_FEE_DB_URL:-}" "${SCHOOL_FEE_DB_USERNAME:-school_fee}" "${SCHOOL_FEE_DB_PASSWORD:-}" \
  "flyway_schema_history_school_fee" 8

# Phase B (SKIP if URL/password missing in env)
check_flyway "attendance-service" \
  "${SCHOOL_ATTENDANCE_DB_URL:-}" "${SCHOOL_ATTENDANCE_DB_USERNAME:-school_attendance}" "${SCHOOL_ATTENDANCE_DB_PASSWORD:-}" \
  "flyway_schema_history_school_attendance" 8

check_flyway "exam-service" \
  "${SCHOOL_EXAM_DB_URL:-}" "${SCHOOL_EXAM_DB_USERNAME:-school_exam}" "${SCHOOL_EXAM_DB_PASSWORD:-}" \
  "flyway_schema_history_school_exam" 5

check_flyway "payroll-service" \
  "${SCHOOL_PAYROLL_DB_URL:-}" "${SCHOOL_PAYROLL_DB_USERNAME:-school_payroll}" "${SCHOOL_PAYROLL_DB_PASSWORD:-}" \
  "flyway_schema_history_school_payroll" 4

check_flyway "library-service" \
  "${SCHOOL_LIBRARY_DB_URL:-}" "${SCHOOL_LIBRARY_DB_USERNAME:-school_library}" "${SCHOOL_LIBRARY_DB_PASSWORD:-}" \
  "flyway_schema_history_school_library" 5

check_flyway "hostel-service" \
  "${SCHOOL_HOSTEL_DB_URL:-}" "${SCHOOL_HOSTEL_DB_USERNAME:-school_hostel}" "${SCHOOL_HOSTEL_DB_PASSWORD:-}" \
  "flyway_schema_history_school_hostel" 4

check_flyway "transport-service" \
  "${SCHOOL_TRANSPORT_DB_URL:-}" "${SCHOOL_TRANSPORT_DB_USERNAME:-school_transport}" "${SCHOOL_TRANSPORT_DB_PASSWORD:-}" \
  "flyway_schema_history_school_transport" 4

check_flyway "school-notification-config-service" \
  "${SCHOOL_NOTIF_CFG_DB_URL:-}" "${SCHOOL_NOTIF_CFG_DB_USERNAME:-school_notif_cfg}" "${SCHOOL_NOTIF_CFG_DB_PASSWORD:-}" \
  "flyway_schema_history_school_notif_cfg" 4

echo ""
echo "== Gateway smoke =="
curl -fsS -o /dev/null -w "gateway health HTTP %{http_code}\n" http://127.0.0.1:9090/actuator/health || true

echo ""
echo "=============================================="
echo " RESULT: OK=$pass_count  FAIL=$fail_count  SKIP=$skip_count"
echo "=============================================="
echo ""
echo "Next (UI — run on your PC, then WinSCP):"
echo "  shop-management-ui: branch feature/platform-enterprise-controls → /var/www/sugamflow-ui"
echo "  school-ui: build → /var/www/school-ui"
echo "  sudo nginx -t && sudo systemctl reload nginx"
echo ""
echo "Re-check only later:"
echo "  CHECK_ONLY=1 bash scripts/ec2/00-full-deploy-and-flyway-check.sh"
echo ""

if [[ "$fail_count" -gt 0 ]]; then
  exit 1
fi
exit 0
