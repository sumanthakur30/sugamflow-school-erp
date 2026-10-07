-- Quote payments are collected against the quotation. They do not change the standard price book or existing subscriptions.
ALTER TABLE pricing_quote DROP CONSTRAINT ck_pricing_quote_status;
ALTER TABLE pricing_quote
    ADD CONSTRAINT ck_pricing_quote_status
    CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'PAID', 'PARTIAL'));

ALTER TABLE pricing_quote
    ADD COLUMN organization_name VARCHAR(160) NULL,
    ADD COLUMN owner_phone VARCHAR(32) NULL,
    ADD COLUMN owner_email VARCHAR(160) NULL,
    ADD COLUMN payment_token VARCHAR(64) NULL;

CREATE TABLE pricing_quote_payment (
    id            BIGSERIAL PRIMARY KEY,
    quote_id      BIGINT       NOT NULL REFERENCES pricing_quote (id),
    amount_minor  BIGINT       NOT NULL,
    method        VARCHAR(24)  NOT NULL,
    reference_no  VARCHAR(128) NULL,
    note          VARCHAR(512) NULL,
    paid_on       DATE         NOT NULL DEFAULT CURRENT_DATE,
    recorded_by   VARCHAR(128) NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_pricing_quote_payment_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_pricing_quote_payment_method CHECK (method IN ('CASH', 'BANK', 'CHEQUE', 'UPI', 'RAZORPAY'))
);

CREATE INDEX idx_pricing_quote_payment_quote ON pricing_quote_payment (quote_id, created_at);
