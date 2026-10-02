CREATE TABLE IF NOT EXISTS sensitive_export_audit (
    id               UUID PRIMARY KEY,
    organization_id  VARCHAR(64)  NOT NULL,
    branch_id        VARCHAR(64),
    export_kind      VARCHAR(64)  NOT NULL,
    includes_aadhaar BOOLEAN      NOT NULL DEFAULT FALSE,
    row_count        INTEGER      NOT NULL DEFAULT 0,
    actor_id         VARCHAR(128),
    actor_role       VARCHAR(64),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sensitive_export_audit_org_created
    ON sensitive_export_audit (organization_id, created_at DESC);
