-- School ERP local / shared Postgres bootstrap
-- Pattern matches SugamFlow: one database + owner role per microservice.
--
-- DBeaver note: CREATE DATABASE cannot run inside a transaction.
-- Run CREATE USER block first, then each CREATE DATABASE with Execute Statement
-- (Ctrl+Enter), one at a time if needed.
--
-- Prefer: docker compose up (auto-runs via docker-entrypoint-initdb.d)
-- Or:     .\infra\postgres\init-local.ps1

-- Roles (CREATE USER = CREATE ROLE ... LOGIN)
DO $$
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
$$;

-- Databases (run outside a transaction if tools wrap scripts)
SELECT 'CREATE DATABASE school_settings_db OWNER school_settings'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'school_settings_db')\gexec

SELECT 'CREATE DATABASE school_subscription_db OWNER school_subscription'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'school_subscription_db')\gexec

SELECT 'CREATE DATABASE school_forms_db OWNER school_forms'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'school_forms_db')\gexec

SELECT 'CREATE DATABASE school_workflow_db OWNER school_workflow'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'school_workflow_db')\gexec

SELECT 'CREATE DATABASE school_rules_db OWNER school_rules'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'school_rules_db')\gexec

SELECT 'CREATE DATABASE school_reports_db OWNER school_reports'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'school_reports_db')\gexec

SELECT 'CREATE DATABASE school_notif_cfg_db OWNER school_notif_cfg'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'school_notif_cfg_db')\gexec

SELECT 'CREATE DATABASE school_audit_db OWNER school_audit'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'school_audit_db')\gexec
