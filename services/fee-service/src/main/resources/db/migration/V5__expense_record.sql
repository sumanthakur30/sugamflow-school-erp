-- School operating expenses for Income & Expense reporting.
CREATE TABLE IF NOT EXISTS expense_record (
    id                   UUID PRIMARY KEY,
    organization_id      VARCHAR(64)  NOT NULL,
    branch_id            VARCHAR(64),
    academic_session_id  VARCHAR(64),
    voucher_no           VARCHAR(64),
    expense_date         DATE         NOT NULL,
    category             VARCHAR(128) NOT NULL,
    description          VARCHAR(512) NOT NULL DEFAULT '',
    amount               NUMERIC(14, 2) NOT NULL,
    payment_mode         VARCHAR(64)  NOT NULL DEFAULT 'CASH',
    status               VARCHAR(32)  NOT NULL DEFAULT 'POSTED',
    created_by           VARCHAR(128),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    payload              JSONB        NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_expense_org_date
    ON expense_record (organization_id, expense_date DESC);

CREATE INDEX idx_expense_org_branch_date
    ON expense_record (organization_id, branch_id, expense_date DESC);

CREATE INDEX idx_expense_org_category
    ON expense_record (organization_id, category);

CREATE INDEX idx_fee_collection_org_status_created
    ON fee_collection (organization_id, status, created_at DESC);

CREATE INDEX idx_fee_collection_org_branch_status_created
    ON fee_collection (organization_id, branch_id, status, created_at DESC);
