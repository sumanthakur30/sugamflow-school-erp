-- P5: AI WARN job tracking + board adapter job stubs; findings source tagging.

ALTER TABLE validation_finding
    ADD COLUMN IF NOT EXISTS source VARCHAR(20) NOT NULL DEFAULT 'RULE',
    ADD COLUMN IF NOT EXISTS confidence NUMERIC(5, 2);

CREATE INDEX IF NOT EXISTS idx_validation_finding_source
    ON validation_finding (organization_id, source, status);

CREATE TABLE adapter_job (
    id                  BIGSERIAL PRIMARY KEY,
    organization_id     VARCHAR(100) NOT NULL,
    campaign_id         BIGINT REFERENCES submission_campaign (id) ON DELETE SET NULL,
    board_code          VARCHAR(40) NOT NULL DEFAULT 'CBSE',
    channel             VARCHAR(40) NOT NULL,
    status              VARCHAR(40) NOT NULL DEFAULT 'QUEUED',
    external_ref        VARCHAR(255),
    request_note        TEXT,
    result_message      TEXT,
    artifact_count      INT NOT NULL DEFAULT 0,
    created_by          VARCHAR(120),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ
);

CREATE INDEX idx_adapter_job_org ON adapter_job (organization_id, created_at DESC);
CREATE INDEX idx_adapter_job_campaign ON adapter_job (campaign_id);

CREATE TABLE ai_scan_job (
    id                  BIGSERIAL PRIMARY KEY,
    organization_id     VARCHAR(100) NOT NULL,
    campaign_id         BIGINT REFERENCES submission_campaign (id) ON DELETE SET NULL,
    status              VARCHAR(40) NOT NULL DEFAULT 'QUEUED',
    provider            VARCHAR(40) NOT NULL DEFAULT 'heuristic',
    warn_count          INT NOT NULL DEFAULT 0,
    records_scanned     INT NOT NULL DEFAULT 0,
    result_message      TEXT,
    fallback_used       BOOLEAN NOT NULL DEFAULT FALSE,
    created_by          VARCHAR(120),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ
);

CREATE INDEX idx_ai_scan_job_org ON ai_scan_job (organization_id, created_at DESC);
CREATE INDEX idx_ai_scan_job_campaign ON ai_scan_job (campaign_id);
