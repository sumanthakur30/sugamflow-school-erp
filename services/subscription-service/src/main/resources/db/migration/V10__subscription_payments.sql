-- Phase 8d: payment ledger for webhook → PAID (additive).

CREATE TABLE subscription_payment (
    id                   BIGSERIAL    PRIMARY KEY,
    organization_id      VARCHAR(64)  NOT NULL,
    invoice_id           BIGINT       NOT NULL REFERENCES subscription_invoice(id),
    provider             VARCHAR(64)  NOT NULL DEFAULT 'MANUAL',
    provider_payment_id  VARCHAR(128) NULL,
    amount_minor         BIGINT       NOT NULL,
    currency             VARCHAR(8)   NOT NULL DEFAULT 'INR',
    status               VARCHAR(32)  NOT NULL,
    renew_days           INT          NULL,
    raw_payload          TEXT         NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_payment_status CHECK (status IN ('SUCCESS', 'FAILED', 'PENDING')),
    CONSTRAINT ck_payment_amount_nonneg CHECK (amount_minor >= 0)
);

CREATE INDEX idx_sub_payment_org ON subscription_payment (organization_id, created_at DESC);
CREATE INDEX idx_sub_payment_invoice ON subscription_payment (invoice_id);
CREATE UNIQUE INDEX uq_sub_payment_provider_ref
    ON subscription_payment (provider, provider_payment_id)
    WHERE provider_payment_id IS NOT NULL;
