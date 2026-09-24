-- =============================================================================
-- Website + CMS — DBeaver / RDS paste version (no \gexec)
-- Matches cms-service + website-service application.properties
-- =============================================================================
-- ROLE = school_cms / school_website
-- DB   = school_cms_db / school_website_db
--
-- For production: change PASSWORD values, then set the same in
-- .env.school.production:
--   SCHOOL_CMS_DB_USERNAME=school_cms
--   SCHOOL_CMS_DB_PASSWORD=<same>
--   SCHOOL_WEBSITE_DB_USERNAME=school_website
--   SCHOOL_WEBSITE_DB_PASSWORD=<same>
-- =============================================================================

-- Skip any line that errors with "already exists"

CREATE USER school_cms WITH PASSWORD 'school_cms';
CREATE USER school_website WITH PASSWORD 'school_website';

-- RDS: required so master can CREATE DATABASE ... OWNER <role>
GRANT school_cms TO postgres;
GRANT school_website TO postgres;

CREATE DATABASE school_cms_db OWNER school_cms;
CREATE DATABASE school_website_db OWNER school_website;
