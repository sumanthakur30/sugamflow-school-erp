-- Phase 9: add-on marketplace + Razorpay order refs (additive).

CREATE TABLE addon_definition (
    sku                 VARCHAR(64)  PRIMARY KEY,
    name                VARCHAR(128) NOT NULL,
    description         VARCHAR(512) NULL,
    addon_type          VARCHAR(32)  NOT NULL,
    meter_code          VARCHAR(64)  NULL,
    credit_amount       BIGINT       NOT NULL DEFAULT 0,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order          INT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_addon_type CHECK (
        addon_type IN ('STORAGE', 'BRANCH', 'SEAT', 'AI_CREDITS', 'WHATSAPP', 'SMS', 'API', 'LICENSE', 'SERVICE')
    )
);

CREATE TABLE addon_price (
    id                  BIGSERIAL    PRIMARY KEY,
    sku                 VARCHAR(64)  NOT NULL REFERENCES addon_definition(sku) ON DELETE CASCADE,
    price_book_id       VARCHAR(64)  NOT NULL REFERENCES price_book(id) ON DELETE CASCADE,
    billing_cycle_code  VARCHAR(32)  NOT NULL DEFAULT 'ONE_TIME',
    amount_minor        BIGINT       NOT NULL,
    currency            VARCHAR(8)   NOT NULL DEFAULT 'INR',
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_addon_price UNIQUE (sku, price_book_id, billing_cycle_code),
    CONSTRAINT ck_addon_price_amount CHECK (amount_minor >= 0)
);

CREATE INDEX idx_addon_price_sku ON addon_price (sku);

CREATE TABLE tenant_addon (
    id                  BIGSERIAL    PRIMARY KEY,
    organization_id     VARCHAR(64)  NOT NULL,
    sku                 VARCHAR(64)  NOT NULL REFERENCES addon_definition(sku),
    quantity            INT          NOT NULL DEFAULT 1,
    status              VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    invoice_id          BIGINT       NULL,
    starts_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    ends_at             TIMESTAMPTZ  NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_tenant_addon_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_tenant_addon_qty CHECK (quantity > 0)
);

CREATE INDEX idx_tenant_addon_org ON tenant_addon (organization_id, status);

CREATE TABLE credit_wallet (
    organization_id     VARCHAR(64)  NOT NULL,
    meter_code          VARCHAR(64)  NOT NULL,
    balance             BIGINT       NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (organization_id, meter_code),
    CONSTRAINT ck_credit_balance_nonneg CHECK (balance >= 0)
);

CREATE TABLE credit_ledger (
    id                  BIGSERIAL    PRIMARY KEY,
    organization_id     VARCHAR(64)  NOT NULL,
    meter_code          VARCHAR(64)  NOT NULL,
    delta               BIGINT       NOT NULL,
    balance_after       BIGINT       NOT NULL,
    reason              VARCHAR(256) NULL,
    invoice_id          BIGINT       NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_credit_ledger_org ON credit_ledger (organization_id, created_at DESC);

ALTER TABLE subscription_invoice
    ADD COLUMN gateway_order_id VARCHAR(128) NULL;

ALTER TABLE subscription_payment
    ADD COLUMN gateway_order_id VARCHAR(128) NULL;

CREATE INDEX idx_sub_invoice_gateway_order ON subscription_invoice (gateway_order_id)
    WHERE gateway_order_id IS NOT NULL;

-- ONE_TIME cycle for marketplace packs (not a recurring subscription cycle).
INSERT INTO billing_cycle (code, name, months, sort_order, active) VALUES
    ('ONE_TIME', 'One-time', 0, 5, TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO addon_definition (sku, name, description, addon_type, meter_code, credit_amount, sort_order) VALUES
    ('ADDON_STORAGE_50GB', 'Extra Storage 50GB', 'Adds 50GB to storage entitlement', 'STORAGE', 'storage_gb', 50, 10),
    ('ADDON_BRANCH', 'Extra Branch', 'Adds one branch/campus seat', 'BRANCH', 'branches', 1, 20),
    ('ADDON_SEAT', 'Extra Employee Seat', 'Adds one user seat', 'SEAT', 'employees', 1, 30),
    ('ADDON_AI_5K', 'AI Credits 5,000', 'Top-up 5000 AI credits', 'AI_CREDITS', 'ai_credits', 5000, 40),
    ('ADDON_WA_1K', 'WhatsApp 1,000', 'Top-up 1000 WhatsApp messages', 'WHATSAPP', 'whatsapp', 1000, 50),
    ('ADDON_SMS_1K', 'SMS 1,000', 'Top-up 1000 SMS', 'SMS', 'sms', 1000, 60)
ON CONFLICT (sku) DO NOTHING;

INSERT INTO addon_price (sku, price_book_id, billing_cycle_code, amount_minor, currency, active)
SELECT v.sku, 'pb-in-smb', 'ONE_TIME', v.amount_minor, 'INR', TRUE
FROM (VALUES
    ('ADDON_STORAGE_50GB', 29900),
    ('ADDON_BRANCH', 49900),
    ('ADDON_SEAT', 19900),
    ('ADDON_AI_5K', 99900),
    ('ADDON_WA_1K', 49900),
    ('ADDON_SMS_1K', 19900)
) AS v(sku, amount_minor)
WHERE EXISTS (SELECT 1 FROM price_book WHERE id = 'pb-in-smb')
  AND EXISTS (SELECT 1 FROM addon_definition d WHERE d.sku = v.sku)
ON CONFLICT (sku, price_book_id, billing_cycle_code) DO NOTHING;
