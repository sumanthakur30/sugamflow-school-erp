-- Phase 19: config-driven finance masters + immutable transaction events.
CREATE TABLE IF NOT EXISTS finance_definition (
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

CREATE UNIQUE INDEX uq_finance_definition_scope_key_ver
    ON finance_definition (organization_id, definition_type, definition_key, version);

CREATE INDEX idx_finance_definition_org_type
    ON finance_definition (organization_id, definition_type, status);

CREATE TABLE IF NOT EXISTS finance_transaction (
    id                   UUID PRIMARY KEY,
    organization_id      VARCHAR(64)  NOT NULL,
    branch_id            VARCHAR(64),
    academic_session_id  VARCHAR(64),
    transaction_type     VARCHAR(64)  NOT NULL,
    status               VARCHAR(32)  NOT NULL,
    student_ref          VARCHAR(128),
    currency             VARCHAR(8)   NOT NULL DEFAULT 'INR',
    gross_amount         NUMERIC(14, 2),
    net_amount           NUMERIC(14, 2),
    reference_no         VARCHAR(128),
    idempotency_key      VARCHAR(128),
    source_collection_id UUID,
    payload              JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_finance_txn_idempotency
    ON finance_transaction (organization_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_finance_txn_org_created
    ON finance_transaction (organization_id, created_at DESC);

CREATE INDEX idx_finance_txn_org_type
    ON finance_transaction (organization_id, transaction_type, status);
