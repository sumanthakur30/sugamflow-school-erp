-- Phase 8c: coupons (additive). Proration is computed at draft time (no separate table).

CREATE TABLE billing_coupon (
    id                   VARCHAR(64)  PRIMARY KEY,
    code                 VARCHAR(64)  NOT NULL UNIQUE,
    name                 VARCHAR(128) NOT NULL,
    discount_type        VARCHAR(16)  NOT NULL,
    discount_value       BIGINT       NOT NULL,
    currency             VARCHAR(8)   NOT NULL DEFAULT 'INR',
    max_redemptions      INT          NULL,
    redemption_count     INT          NOT NULL DEFAULT 0,
    min_subtotal_minor   BIGINT       NOT NULL DEFAULT 0,
    applicable_plan_id   VARCHAR(64)  NULL,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    valid_from           TIMESTAMPTZ  NULL,
    valid_to             TIMESTAMPTZ  NULL,
    notes                VARCHAR(512) NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_coupon_type CHECK (discount_type IN ('PERCENT', 'FIXED_MINOR')),
    CONSTRAINT ck_coupon_value_nonneg CHECK (discount_value >= 0),
    CONSTRAINT ck_coupon_redemptions CHECK (redemption_count >= 0)
);

CREATE INDEX idx_billing_coupon_active ON billing_coupon (active);

INSERT INTO billing_coupon (
    id, code, name, discount_type, discount_value, currency, max_redemptions,
    min_subtotal_minor, active, notes
) VALUES (
    'cpn-welcome10',
    'WELCOME10',
    'Welcome 10% off',
    'PERCENT',
    10,
    'INR',
    NULL,
    0,
    TRUE,
    'Phase 8 seed coupon'
)
ON CONFLICT (id) DO NOTHING;

-- Allow credit/discount lines (negative amount_minor).
ALTER TABLE subscription_invoice_line DROP CONSTRAINT IF EXISTS ck_invoice_line_amounts_nonneg;
