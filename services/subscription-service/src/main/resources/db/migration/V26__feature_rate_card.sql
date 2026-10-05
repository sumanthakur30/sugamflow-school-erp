-- Feature rate card. Additive only.
-- Does not change subscription_plan, tenant_subscription, entitlements, plan_price, or invoices.
-- A price change closes the open row and inserts a new one.

CREATE TABLE feature_rate_version (
    id                      BIGSERIAL    PRIMARY KEY,
    feature_code            VARCHAR(96)  NOT NULL REFERENCES feature_definition(code),
    business_type_code      VARCHAR(64)  NULL REFERENCES business_type(code),
    unit_model              VARCHAR(40)  NOT NULL DEFAULT 'PER_ORGANIZATION',
    monthly_amount_minor    BIGINT       NOT NULL DEFAULT 0,
    yearly_amount_minor     BIGINT       NOT NULL DEFAULT 0,
    gst_inclusive           BOOLEAN      NOT NULL DEFAULT FALSE,
    sellable_addon          BOOLEAN      NOT NULL DEFAULT FALSE,
    effective_from          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    effective_until         TIMESTAMPTZ  NULL,
    changed_by              VARCHAR(128) NULL,
    change_reason           VARCHAR(512) NULL,
    benchmark_low_minor     BIGINT       NULL,
    benchmark_average_minor BIGINT       NULL,
    benchmark_high_minor    BIGINT       NULL,
    benchmark_notes         VARCHAR(512) NULL,
    benchmark_reviewed_on   DATE         NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_feature_rate_amounts CHECK (monthly_amount_minor >= 0 AND yearly_amount_minor >= 0),
    CONSTRAINT ck_feature_rate_window CHECK (effective_until IS NULL OR effective_until >= effective_from),
    CONSTRAINT ck_feature_rate_unit CHECK (unit_model IN (
        'PER_ORGANIZATION', 'PER_OUTLET', 'PER_USER', 'PER_EMPLOYEE', 'PER_WAREHOUSE',
        'PER_LOCATION', 'PER_CHANNEL', 'PER_TRANSACTION', 'USAGE', 'ONE_TIME', 'ANNUAL'
    ))
);

CREATE UNIQUE INDEX uq_feature_rate_open
    ON feature_rate_version (feature_code, COALESCE(business_type_code, ''))
    WHERE effective_until IS NULL;

CREATE INDEX idx_feature_rate_feature ON feature_rate_version (feature_code, effective_from DESC);

CREATE TABLE plan_rate_discount (
    id               BIGSERIAL    PRIMARY KEY,
    plan_id          VARCHAR(64)  NOT NULL REFERENCES subscription_plan(id) ON DELETE CASCADE,
    discount_bps     INT          NOT NULL DEFAULT 0,
    reason           VARCHAR(512) NULL,
    effective_from   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    effective_until  TIMESTAMPTZ  NULL,
    changed_by       VARCHAR(128) NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_plan_rate_discount_bps CHECK (discount_bps >= 0 AND discount_bps <= 10000)
);

CREATE UNIQUE INDEX uq_plan_rate_discount_open
    ON plan_rate_discount (plan_id)
    WHERE effective_until IS NULL;

CREATE TABLE pricing_quote (
    id                     BIGSERIAL    PRIMARY KEY,
    customer_name          VARCHAR(160) NOT NULL,
    organization_id        VARCHAR(64)  NULL,
    business_type_code     VARCHAR(64)  NULL,
    plan_id                VARCHAR(64)  NULL,
    feature_codes          VARCHAR(2000) NOT NULL DEFAULT '',
    standard_amount_minor  BIGINT       NOT NULL,
    discount_bps           INT          NOT NULL DEFAULT 0,
    final_amount_minor     BIGINT       NOT NULL,
    gst_inclusive          BOOLEAN      NOT NULL DEFAULT FALSE,
    billing_cycle          VARCHAR(16)  NOT NULL DEFAULT 'MONTHLY',
    valid_from             DATE         NULL,
    valid_until            DATE         NULL,
    status                 VARCHAR(24)  NOT NULL DEFAULT 'DRAFT',
    reason                 VARCHAR(512) NULL,
    created_by             VARCHAR(128) NULL,
    approved_by            VARCHAR(128) NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_pricing_quote_amounts CHECK (standard_amount_minor >= 0 AND final_amount_minor >= 0),
    CONSTRAINT ck_pricing_quote_bps CHECK (discount_bps >= 0 AND discount_bps <= 10000),
    CONSTRAINT ck_pricing_quote_status CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_pricing_quote_status ON pricing_quote (status, created_at DESC);
