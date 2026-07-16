-- Phase 1: database wiring baseline for school-settings-service.
-- Domain tables (design theme, menus, localization) land in Phase 2.

CREATE TABLE IF NOT EXISTS school_schema_meta (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    service_name    VARCHAR(100) NOT NULL,
    note            VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO school_schema_meta (service_name, note)
SELECT 'school-settings-service', 'Phase 1 datasource wired'
WHERE NOT EXISTS (
    SELECT 1 FROM school_schema_meta WHERE service_name = 'school-settings-service'
);
