-- Phase 13: AI / usage credit monetization — policies + period carry-forward runs (additive).

CREATE TABLE credit_policy (
    meter_code              VARCHAR(64)  PRIMARY KEY,
    name                    VARCHAR(128) NOT NULL,
    period_type             VARCHAR(16)  NOT NULL DEFAULT 'MONTHLY',
    carry_forward_bps       INT          NOT NULL DEFAULT 0,
    carry_forward_cap       BIGINT       NULL,
    expire_unused           BOOLEAN      NOT NULL DEFAULT FALSE,
    plan_grant_amount       BIGINT       NOT NULL DEFAULT 0,
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    notes                   VARCHAR(512) NULL,
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_credit_period_type CHECK (
        period_type IN ('NONE', 'MONTHLY', 'QUARTERLY', 'YEARLY')
    ),
    CONSTRAINT ck_credit_carry_bps CHECK (carry_forward_bps >= 0 AND carry_forward_bps <= 10000),
    CONSTRAINT ck_credit_plan_grant CHECK (plan_grant_amount >= 0)
);

CREATE TABLE credit_period_run (
    id                      BIGSERIAL    PRIMARY KEY,
    organization_id         VARCHAR(64)  NOT NULL,
    meter_code              VARCHAR(64)  NOT NULL REFERENCES credit_policy(meter_code),
    period_key              VARCHAR(32)  NOT NULL,
    opening_balance         BIGINT       NOT NULL,
    carried_forward         BIGINT       NOT NULL DEFAULT 0,
    expired                 BIGINT       NOT NULL DEFAULT 0,
    granted                 BIGINT       NOT NULL DEFAULT 0,
    closing_balance         BIGINT       NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_credit_period_run UNIQUE (organization_id, meter_code, period_key),
    CONSTRAINT ck_credit_period_nonneg CHECK (
        opening_balance >= 0
        AND carried_forward >= 0
        AND expired >= 0
        AND granted >= 0
        AND closing_balance >= 0
    )
);

CREATE INDEX idx_credit_period_run_org ON credit_period_run (organization_id, created_at DESC);

INSERT INTO credit_policy (
    meter_code, name, period_type, carry_forward_bps, carry_forward_cap,
    expire_unused, plan_grant_amount, notes
) VALUES
    ('ai_credits', 'AI credits', 'MONTHLY', 5000, 2500, TRUE, 0,
     'Phase 13 seed: 50% carry-forward, cap 2500, expire remainder'),
    ('whatsapp', 'WhatsApp messages', 'MONTHLY', 0, 0, TRUE, 0,
     'No carry — unused expire at period close'),
    ('sms', 'SMS credits', 'MONTHLY', 0, 0, TRUE, 0,
     'No carry — unused expire at period close')
ON CONFLICT (meter_code) DO NOTHING;
