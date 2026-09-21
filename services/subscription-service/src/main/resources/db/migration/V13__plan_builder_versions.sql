-- Phase 11: plan versioning + scheduled list prices (additive).
-- Live entitlements still come from subscription_plan JSON until a version is published.

CREATE TABLE plan_version (
    id                  BIGSERIAL    PRIMARY KEY,
    plan_id             VARCHAR(64)  NOT NULL REFERENCES subscription_plan(id) ON DELETE CASCADE,
    version_number      INT          NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    label               VARCHAR(128) NULL,
    notes               VARCHAR(512) NULL,
    feature_flags_json  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    limits_json         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    module_order_json   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at        TIMESTAMPTZ  NULL,
    CONSTRAINT uq_plan_version UNIQUE (plan_id, version_number),
    CONSTRAINT ck_plan_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_plan_version_number CHECK (version_number > 0)
);

CREATE INDEX idx_plan_version_plan_status ON plan_version (plan_id, status);

CREATE TABLE plan_price_schedule (
    id                  BIGSERIAL    PRIMARY KEY,
    plan_id             VARCHAR(64)  NOT NULL REFERENCES subscription_plan(id) ON DELETE CASCADE,
    price_book_id       VARCHAR(64)  NOT NULL REFERENCES price_book(id) ON DELETE CASCADE,
    billing_cycle_code  VARCHAR(32)  NOT NULL,
    amount_minor        BIGINT       NOT NULL,
    currency            VARCHAR(8)   NOT NULL DEFAULT 'INR',
    effective_at        TIMESTAMPTZ  NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'SCHEDULED',
    applied_at          TIMESTAMPTZ  NULL,
    notes               VARCHAR(512) NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_price_schedule_status CHECK (status IN ('SCHEDULED', 'APPLIED', 'CANCELLED')),
    CONSTRAINT ck_price_schedule_amount CHECK (amount_minor >= 0)
);

CREATE INDEX idx_plan_price_schedule_due
    ON plan_price_schedule (status, effective_at)
    WHERE status = 'SCHEDULED';
