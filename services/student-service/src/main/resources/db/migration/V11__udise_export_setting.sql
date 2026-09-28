CREATE TABLE IF NOT EXISTS udise_export_setting (
    organization_id VARCHAR(64) PRIMARY KEY,
    column_keys     JSONB       NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
