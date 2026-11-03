-- School platform subscription billed per active student.
-- Separate from school fee collections and from shop subscription_billing_records.

CREATE TABLE school_billing_contract (
    organization_id          VARCHAR(64)  PRIMARY KEY,
    active                   BOOLEAN      NOT NULL DEFAULT TRUE,
    unit_model               VARCHAR(40)  NOT NULL DEFAULT 'PER_STUDENT',
    per_student_rate_minor   BIGINT       NULL,
    base_fee_minor           BIGINT       NOT NULL DEFAULT 0,
    discount_minor           BIGINT       NOT NULL DEFAULT 0,
    buyer_state_code         VARCHAR(8)   NULL,
    seller_state_code        VARCHAR(8)   NULL,
    count_policy             VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE_ON_RUN',
    effective_from           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    effective_until          TIMESTAMPTZ  NULL,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_school_bill_unit CHECK (unit_model = 'PER_STUDENT'),
    CONSTRAINT ck_school_bill_policy CHECK (count_policy = 'ACTIVE_ON_RUN'),
    CONSTRAINT ck_school_bill_amounts CHECK (
        base_fee_minor >= 0
        AND discount_minor >= 0
        AND (per_student_rate_minor IS NULL OR per_student_rate_minor >= 0)
    )
);

CREATE TABLE school_billing_run (
    id                  BIGSERIAL    PRIMARY KEY,
    organization_id     VARCHAR(64)  NOT NULL,
    period_key          VARCHAR(16)  NOT NULL,
    obligation_type     VARCHAR(32)  NOT NULL DEFAULT 'SCHOOL_PER_STUDENT',
    count_policy        VARCHAR(32)  NOT NULL,
    billable_count      INT          NOT NULL,
    snapshot_at         TIMESTAMPTZ  NOT NULL,
    rate_version_id     BIGINT       NULL,
    unit_amount_minor   BIGINT       NOT NULL,
    base_fee_minor      BIGINT       NOT NULL,
    discount_minor      BIGINT       NOT NULL,
    taxable_minor       BIGINT       NOT NULL,
    cgst_minor          BIGINT       NOT NULL DEFAULT 0,
    sgst_minor          BIGINT       NOT NULL DEFAULT 0,
    igst_minor          BIGINT       NOT NULL DEFAULT 0,
    tax_minor           BIGINT       NOT NULL DEFAULT 0,
    total_minor         BIGINT       NOT NULL DEFAULT 0,
    invoice_id          BIGINT       NULL REFERENCES subscription_invoice(id),
    job_status          VARCHAR(16)  NOT NULL,
    error_message       VARCHAR(512) NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_school_billing_run UNIQUE (organization_id, period_key, obligation_type),
    CONSTRAINT ck_school_run_status CHECK (job_status IN ('PENDING', 'ISSUED', 'FAILED')),
    CONSTRAINT ck_school_run_amounts CHECK (
        billable_count >= 0
        AND unit_amount_minor >= 0
        AND base_fee_minor >= 0
        AND discount_minor >= 0
        AND taxable_minor >= 0
        AND cgst_minor >= 0
        AND sgst_minor >= 0
        AND igst_minor >= 0
        AND tax_minor >= 0
        AND total_minor >= 0
    )
);

CREATE INDEX idx_school_billing_run_period
    ON school_billing_run (period_key, job_status);
