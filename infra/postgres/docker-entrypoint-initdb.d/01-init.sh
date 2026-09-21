#!/bin/bash
set -euo pipefail

# Runs only on first container volume init (empty data dir).
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_settings') THEN
      CREATE USER school_settings WITH PASSWORD 'school_settings';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_subscription') THEN
      CREATE USER school_subscription WITH PASSWORD 'school_subscription';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_forms') THEN
      CREATE USER school_forms WITH PASSWORD 'school_forms';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_workflow') THEN
      CREATE USER school_workflow WITH PASSWORD 'school_workflow';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_rules') THEN
      CREATE USER school_rules WITH PASSWORD 'school_rules';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_reports') THEN
      CREATE USER school_reports WITH PASSWORD 'school_reports';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_notif_cfg') THEN
      CREATE USER school_notif_cfg WITH PASSWORD 'school_notif_cfg';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_audit') THEN
      CREATE USER school_audit WITH PASSWORD 'school_audit';
    END IF;
  END
  \$\$;
EOSQL

for pair in \
  "school_settings_db:school_settings" \
  "school_subscription_db:school_subscription" \
  "school_forms_db:school_forms" \
  "school_workflow_db:school_workflow" \
  "school_rules_db:school_rules" \
  "school_reports_db:school_reports" \
  "school_notif_cfg_db:school_notif_cfg" \
  "school_audit_db:school_audit"
do
  db="${pair%%:*}"
  owner="${pair##*:}"
  exists="$(psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -tAc "SELECT 1 FROM pg_database WHERE datname='${db}'")"
  if [ "$exists" != "1" ]; then
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -c "CREATE DATABASE ${db} OWNER ${owner};"
  fi
done
