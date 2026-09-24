-- Phase 8b: GST tax rules + invoice tax breakdown (additive).
-- Extends V7 invoices; entitlements unchanged.

ALTER TABLE subscription_invoice DROP CONSTRAINT IF EXISTS ck_invoice_status;
ALTER TABLE subscription_invoice
    ADD CONSTRAINT ck_invoice_status CHECK (status IN ('DRAFT', 'ISSUED', 'PAID', 'VOID'));

ALTER TABLE subscription_invoice
    ADD COLUMN tax_rule_id VARCHAR(64) NULL,
    ADD COLUMN cgst_minor BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN sgst_minor BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN igst_minor BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN place_of_supply VARCHAR(8) NULL,
    ADD COLUMN seller_state_code VARCHAR(8) NULL,
    ADD COLUMN discount_minor BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN coupon_code VARCHAR(64) NULL,
    ADD COLUMN proration_factor NUMERIC(12, 8) NULL;

CREATE TABLE tax_rule (
    id                 VARCHAR(64)  PRIMARY KEY,
    code               VARCHAR(64)  NOT NULL UNIQUE,
    name               VARCHAR(128) NOT NULL,
    country_code       VARCHAR(8)   NOT NULL DEFAULT 'IN',
    hsn_sac            VARCHAR(32)  NULL,
    cgst_bps           INT          NOT NULL DEFAULT 0,
    sgst_bps           INT          NOT NULL DEFAULT 0,
    igst_bps           INT          NOT NULL DEFAULT 0,
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    is_default         BOOLEAN      NOT NULL DEFAULT FALSE,
    effective_from     TIMESTAMPTZ  NULL,
    effective_to       TIMESTAMPTZ  NULL,
    notes              VARCHAR(512) NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_tax_bps_nonneg CHECK (cgst_bps >= 0 AND sgst_bps >= 0 AND igst_bps >= 0)
);

CREATE INDEX idx_tax_rule_active ON tax_rule (active);

INSERT INTO tax_rule (
    id, code, name, country_code, hsn_sac, cgst_bps, sgst_bps, igst_bps, active, is_default, notes
) VALUES (
    'tax-in-gst18',
    'IN_GST_18',
    'India GST 18% (SaaS)',
    'IN',
    '998314',
    900,
    900,
    1800,
    TRUE,
    TRUE,
    'Phase 8 seed: 9% CGST + 9% SGST (intra) or 18% IGST (inter)'
)
ON CONFLICT (id) DO NOTHING;
