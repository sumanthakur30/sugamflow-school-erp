-- Phase 21: config-driven ops masters (library catalog, policies).
CREATE TABLE IF NOT EXISTS ops_definition (
    id                   VARCHAR(64) PRIMARY KEY,
    organization_id      VARCHAR(64)  NOT NULL,
    branch_id            VARCHAR(64),
    academic_session_id  VARCHAR(64),
    definition_type      VARCHAR(64)  NOT NULL,
    definition_key       VARCHAR(128) NOT NULL,
    status               VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    version              INT          NOT NULL DEFAULT 1,
    payload              JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_ops_definition_scope_key_ver
    ON ops_definition (organization_id, definition_type, definition_key, version);

CREATE INDEX idx_ops_definition_org_type
    ON ops_definition (organization_id, definition_type, status);
