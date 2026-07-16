-- Phase 20: config-driven academic lifecycle (promotion maps, TC policy, events).
CREATE TABLE IF NOT EXISTS lifecycle_definition (
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

CREATE UNIQUE INDEX uq_lifecycle_definition_scope_key_ver
    ON lifecycle_definition (organization_id, definition_type, definition_key, version);

CREATE INDEX idx_lifecycle_definition_org_type
    ON lifecycle_definition (organization_id, definition_type, status);

CREATE TABLE IF NOT EXISTS lifecycle_event (
    id                   UUID PRIMARY KEY,
    organization_id      VARCHAR(64)  NOT NULL,
    branch_id            VARCHAR(64),
    academic_session_id  VARCHAR(64),
    event_type           VARCHAR(64)  NOT NULL,
    status               VARCHAR(32)  NOT NULL,
    student_id           UUID         NOT NULL,
    reference_no         VARCHAR(128),
    idempotency_key      VARCHAR(128),
    payload              JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_lifecycle_event_idempotency
    ON lifecycle_event (organization_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_lifecycle_event_org_created
    ON lifecycle_event (organization_id, created_at DESC);

CREATE INDEX idx_lifecycle_event_student
    ON lifecycle_event (organization_id, student_id, created_at DESC);
