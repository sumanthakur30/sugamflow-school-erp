-- P7: Compliance Import Center — gap-fill jobs (no master schema changes).

CREATE TABLE compliance_import_job (
    id                  BIGSERIAL PRIMARY KEY,
    organization_id     VARCHAR(100) NOT NULL,
    board_code          VARCHAR(40) NOT NULL,
    pack_key            VARCHAR(80),
    entity_type         VARCHAR(40) NOT NULL,
    file_name           VARCHAR(255),
    status              VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    fill_blank_only     BOOLEAN NOT NULL DEFAULT TRUE,
    total_rows          INT NOT NULL DEFAULT 0,
    ready_count         INT NOT NULL DEFAULT 0,
    error_count         INT NOT NULL DEFAULT 0,
    matched_count       INT NOT NULL DEFAULT 0,
    updated_count       INT NOT NULL DEFAULT 0,
    created_by          VARCHAR(120),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_compliance_import_job_org
    ON compliance_import_job (organization_id, created_at DESC);

CREATE TABLE compliance_import_job_row (
    id                  BIGSERIAL PRIMARY KEY,
    job_id              BIGINT NOT NULL REFERENCES compliance_import_job (id) ON DELETE CASCADE,
    row_no              INT NOT NULL,
    match_key           VARCHAR(120),
    entity_id           VARCHAR(100),
    status              VARCHAR(40) NOT NULL DEFAULT 'ERROR',
    payload_json        JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_compliance_import_job_row UNIQUE (job_id, row_no)
);

CREATE INDEX idx_compliance_import_job_row_job
    ON compliance_import_job_row (job_id, status);
