-- Per-student fee adjustments. The class fee structure is not changed.
-- Past finance_transaction rows keep the amounts they were issued with.
CREATE TABLE IF NOT EXISTS student_fee_adjustment (
    id                   VARCHAR(64) PRIMARY KEY,
    organization_id      VARCHAR(64)  NOT NULL,
    branch_id            VARCHAR(64),
    academic_session_id  VARCHAR(64),
    admission_no         VARCHAR(128) NOT NULL,
    status               VARCHAR(32)  NOT NULL,
    mode                 VARCHAR(32)  NOT NULL,
    adjustment_value     NUMERIC(14, 2) NOT NULL,
    applies_to_heads     JSONB        NOT NULL DEFAULT '[]'::jsonb,
    reason               VARCHAR(500) NOT NULL,
    effective_from       DATE,
    effective_to         DATE,
    created_by           VARCHAR(128),
    approved_by          VARCHAR(128),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_student_fee_adj_org_admission
    ON student_fee_adjustment (organization_id, admission_no, status);

CREATE TABLE IF NOT EXISTS student_fee_adjustment_audit (
    id               VARCHAR(64) PRIMARY KEY,
    adjustment_id    VARCHAR(64)  NOT NULL,
    organization_id  VARCHAR(64)  NOT NULL,
    action           VARCHAR(32)  NOT NULL,
    actor            VARCHAR(128),
    reason           VARCHAR(500),
    snapshot         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_student_fee_adj_audit
    ON student_fee_adjustment_audit (organization_id, adjustment_id, created_at);
