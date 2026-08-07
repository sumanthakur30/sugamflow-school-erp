-- P3: approval trail, export artifacts, campaign lock/submit timestamps.

ALTER TABLE submission_campaign
    ADD COLUMN IF NOT EXISTS require_management_approval BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS locked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS locked_by VARCHAR(120),
    ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS submitted_by VARCHAR(120),
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS archived_by VARCHAR(120),
    ADD COLUMN IF NOT EXISTS adapter_channel VARCHAR(40);

CREATE TABLE approval_step (
    id                  BIGSERIAL PRIMARY KEY,
    organization_id     VARCHAR(100) NOT NULL,
    campaign_id         BIGINT NOT NULL REFERENCES submission_campaign (id) ON DELETE CASCADE,
    step_code           VARCHAR(40) NOT NULL,
    decision            VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    actor_user_id       VARCHAR(120),
    comment_text        TEXT,
    decided_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_approval_step_campaign_code UNIQUE (campaign_id, step_code)
);

CREATE INDEX idx_approval_step_org ON approval_step (organization_id, campaign_id);

CREATE TABLE export_artifact (
    id                  BIGSERIAL PRIMARY KEY,
    organization_id     VARCHAR(100) NOT NULL,
    campaign_id         BIGINT NOT NULL REFERENCES submission_campaign (id) ON DELETE CASCADE,
    artifact_key        VARCHAR(80) NOT NULL,
    format_code         VARCHAR(20) NOT NULL,
    file_name           VARCHAR(255) NOT NULL,
    storage_path        VARCHAR(1000) NOT NULL,
    content_type        VARCHAR(120) NOT NULL,
    file_size           BIGINT NOT NULL DEFAULT 0,
    checksum_sha256     VARCHAR(64),
    created_by          VARCHAR(120),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_export_artifact_campaign ON export_artifact (campaign_id, created_at DESC);
CREATE INDEX idx_export_artifact_org ON export_artifact (organization_id, campaign_id);
