-- Phase 8 MVP: commercial pricing + draft invoices (additive).
-- No payment gateway. Existing entitlements / feature-flag APIs unchanged.
-- Amounts are minor units (paise for INR).

CREATE TABLE billing_cycle (
    code         VARCHAR(32)  PRIMARY KEY,
    name         VARCHAR(64)  NOT NULL,
    months       INT          NOT NULL,
    sort_order   INT          NOT NULL DEFAULT 0,
    active       BOOLEAN      NOT NULL DEFAULT TRUE
);

INSERT INTO billing_cycle (code, name, months, sort_order) VALUES
    ('MONTHLY', 'Monthly', 1, 10),
    ('QUARTERLY', 'Quarterly', 3, 20),
    ('HALF_YEARLY', 'Half-yearly', 6, 30),
    ('YEARLY', 'Yearly', 12, 40)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE price_book (
    id              VARCHAR(64)  PRIMARY KEY,
    code            VARCHAR(64)  NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    currency        VARCHAR(8)   NOT NULL DEFAULT 'INR',
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    effective_from  TIMESTAMPTZ  NULL,
    effective_to    TIMESTAMPTZ  NULL,
    notes           VARCHAR(512) NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_price_book_active ON price_book (active);

CREATE TABLE plan_price (
    id                  BIGSERIAL    PRIMARY KEY,
    price_book_id       VARCHAR(64)  NOT NULL REFERENCES price_book(id) ON DELETE CASCADE,
    plan_id             VARCHAR(64)  NOT NULL REFERENCES subscription_plan(id) ON DELETE CASCADE,
    billing_cycle_code  VARCHAR(32)  NOT NULL REFERENCES billing_cycle(code),
    amount_minor        BIGINT       NOT NULL,
    currency            VARCHAR(8)   NOT NULL DEFAULT 'INR',
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_plan_price_book_plan_cycle UNIQUE (price_book_id, plan_id, billing_cycle_code),
    CONSTRAINT ck_plan_price_amount_nonneg CHECK (amount_minor >= 0)
);

CREATE INDEX idx_plan_price_plan ON plan_price (plan_id);
CREATE INDEX idx_plan_price_book ON plan_price (price_book_id);

CREATE TABLE subscription_invoice (
    id                  BIGSERIAL    PRIMARY KEY,
    organization_id     VARCHAR(64)  NOT NULL,
    invoice_number      VARCHAR(64)  NULL,
    status              VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    currency            VARCHAR(8)   NOT NULL DEFAULT 'INR',
    plan_id             VARCHAR(64)  NULL,
    billing_cycle_code  VARCHAR(32)  NULL,
    price_book_id       VARCHAR(64)  NULL,
    subtotal_minor      BIGINT       NOT NULL DEFAULT 0,
    tax_minor           BIGINT       NOT NULL DEFAULT 0,
    total_minor         BIGINT       NOT NULL DEFAULT 0,
    period_start        TIMESTAMPTZ  NULL,
    period_end          TIMESTAMPTZ  NULL,
    issued_at           TIMESTAMPTZ  NULL,
    due_at              TIMESTAMPTZ  NULL,
    notes               VARCHAR(512) NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_invoice_status CHECK (status IN ('DRAFT', 'ISSUED', 'VOID')),
    CONSTRAINT ck_invoice_amounts_nonneg CHECK (
        subtotal_minor >= 0 AND tax_minor >= 0 AND total_minor >= 0
    )
);

CREATE INDEX idx_sub_invoice_org ON subscription_invoice (organization_id, created_at DESC);
CREATE INDEX idx_sub_invoice_status ON subscription_invoice (status);
CREATE UNIQUE INDEX uq_sub_invoice_number
    ON subscription_invoice (invoice_number)
    WHERE invoice_number IS NOT NULL;

CREATE TABLE subscription_invoice_line (
    id                  BIGSERIAL    PRIMARY KEY,
    invoice_id          BIGINT       NOT NULL REFERENCES subscription_invoice(id) ON DELETE CASCADE,
    line_type           VARCHAR(32)  NOT NULL DEFAULT 'PLAN',
    description         VARCHAR(256) NOT NULL,
    quantity            INT          NOT NULL DEFAULT 1,
    unit_amount_minor   BIGINT       NOT NULL,
    amount_minor        BIGINT       NOT NULL,
    plan_id             VARCHAR(64)  NULL,
    sort_order          INT          NOT NULL DEFAULT 0,
    CONSTRAINT ck_invoice_line_qty CHECK (quantity > 0),
    CONSTRAINT ck_invoice_line_amounts_nonneg CHECK (
        unit_amount_minor >= 0 AND amount_minor >= 0
    )
);

CREATE INDEX idx_sub_invoice_line_invoice ON subscription_invoice_line (invoice_id);

-- Default India SMB price book (inactive commercial enforcement — catalog only until used).
INSERT INTO price_book (id, code, name, currency, active, is_default, notes)
VALUES (
    'pb-in-smb',
    'IN_SMB',
    'India SMB list',
    'INR',
    TRUE,
    TRUE,
    'Phase 8 seed: list prices for starter (admin may edit)'
)
ON CONFLICT (id) DO NOTHING;

-- Sample starter prices only if starter plan row exists (paise).
INSERT INTO plan_price (price_book_id, plan_id, billing_cycle_code, amount_minor, currency, active)
SELECT 'pb-in-smb', 'starter', 'MONTHLY', 99900, 'INR', TRUE
WHERE EXISTS (SELECT 1 FROM subscription_plan WHERE id = 'starter')
ON CONFLICT (price_book_id, plan_id, billing_cycle_code) DO NOTHING;

INSERT INTO plan_price (price_book_id, plan_id, billing_cycle_code, amount_minor, currency, active)
SELECT 'pb-in-smb', 'starter', 'YEARLY', 999900, 'INR', TRUE
WHERE EXISTS (SELECT 1 FROM subscription_plan WHERE id = 'starter')
ON CONFLICT (price_book_id, plan_id, billing_cycle_code) DO NOTHING;
