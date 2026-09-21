-- =============================================================================
-- Website + CMS databases (matches application.properties defaults)
-- =============================================================================
-- cms-service:
--   spring.datasource.url=${SCHOOL_CMS_DB_URL:jdbc:postgresql://localhost:5432/school_cms_db}
--   spring.datasource.username=${SCHOOL_CMS_DB_USERNAME:school_cms}
--   spring.datasource.password=${SCHOOL_CMS_DB_PASSWORD:school_cms}
--
-- website-service:
--   spring.datasource.url=${SCHOOL_WEBSITE_DB_URL:jdbc:postgresql://localhost:5432/school_website_db}
--   spring.datasource.username=${SCHOOL_WEBSITE_DB_USERNAME:school_website}
--   spring.datasource.password=${SCHOOL_WEBSITE_DB_PASSWORD:school_website}
--
-- Naming rule (same as other school services):
--   ROLE / LOGIN  = school_<service>     e.g. school_cms
--   DATABASE      = school_<service>_db  e.g. school_cms_db
--   Do NOT use school_cms_db as a username.
--
-- Run as Postgres admin (local: postgres / RDS master) against database "postgres".
-- CREATE DATABASE cannot run inside a transaction — execute statements one-by-one
-- in DBeaver if needed (Ctrl+Enter).
--
-- Local defaults below. For RDS/EC2: replace passwords with strong values and
-- keep the same usernames/DB names, then put those passwords in
-- .env.school.production (SCHOOL_CMS_DB_PASSWORD / SCHOOL_WEBSITE_DB_PASSWORD).
-- =============================================================================

-- 1) Roles (LOGIN users)
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_cms') THEN
    CREATE USER school_cms WITH PASSWORD 'school_cms';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'school_website') THEN
    CREATE USER school_website WITH PASSWORD 'school_website';
  END IF;
END
$$;

-- 2) RDS only — master must inherit role to CREATE DATABASE ... OWNER
--    Skip on local Postgres if not needed. Replace "postgres" with your RDS master.
GRANT school_cms TO postgres;
GRANT school_website TO postgres;

-- 3) Databases (owner = login role from step 1)
--    Skip CREATE if DB already exists.
SELECT 'CREATE DATABASE school_cms_db OWNER school_cms'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'school_cms_db')\gexec

SELECT 'CREATE DATABASE school_website_db OWNER school_website'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'school_website_db')\gexec

-- If your client does not support \gexec (DBeaver), run instead:
--   CREATE DATABASE school_cms_db OWNER school_cms;
--   CREATE DATABASE school_website_db OWNER school_website;

-- 4) Optional: privileges inside each DB (owner already has full rights)
-- \c school_cms_db
-- GRANT ALL ON SCHEMA public TO school_cms;
-- \c school_website_db
-- GRANT ALL ON SCHEMA public TO school_website;
