-- Manual/non-fee school income. Student fee receipts remain in fee_collection.
CREATE TABLE IF NOT EXISTS income_record (
    id                   UUID PRIMARY KEY,
    organization_id      VARCHAR(64)  NOT NULL,
    branch_id            VARCHAR(64),
    academic_session_id  VARCHAR(64),
    voucher_no           VARCHAR(64),
    income_date          DATE         NOT NULL,
    source               VARCHAR(128) NOT NULL,
    description          VARCHAR(512) NOT NULL DEFAULT '',
    amount               NUMERIC(14, 2) NOT NULL,
    payment_mode         VARCHAR(64)  NOT NULL DEFAULT 'CASH',
    status               VARCHAR(32)  NOT NULL DEFAULT 'POSTED',
    created_by           VARCHAR(128),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    payload              JSONB        NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_income_org_date
    ON income_record (organization_id, income_date DESC);

CREATE INDEX idx_income_org_branch_date
    ON income_record (organization_id, branch_id, income_date DESC);

CREATE INDEX idx_income_org_source
    ON income_record (organization_id, source);
