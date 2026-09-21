-- Phase 1: database wiring baseline for report-builder-service.

CREATE TABLE IF NOT EXISTS school_schema_meta (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    service_name    VARCHAR(100) NOT NULL,
    note            VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO school_schema_meta (service_name, note)
SELECT 'report-builder-service', 'Phase 1 datasource wired'
WHERE NOT EXISTS (
    SELECT 1 FROM school_schema_meta WHERE service_name = 'report-builder-service'
);