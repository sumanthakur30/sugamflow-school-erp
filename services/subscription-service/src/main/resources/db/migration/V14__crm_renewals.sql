-- Phase 12: CRM renewals — pipeline opportunities + reminder queue (additive).

CREATE TABLE renewal_opportunity (
    id                  BIGSERIAL    PRIMARY KEY,
    organization_id     VARCHAR(64)  NOT NULL,
    plan_id             VARCHAR(64)  NULL,
    stage               VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    health_score        INT          NOT NULL DEFAULT 50,
    owner               VARCHAR(128) NULL,
    next_action_at      TIMESTAMPTZ  NULL,
    notes               VARCHAR(1024) NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_renewal_opportunity_org UNIQUE (organization_id),
    CONSTRAINT ck_renewal_stage CHECK (
        stage IN ('LEAD', 'TRIAL', 'ACTIVE', 'RENEWAL_DUE', 'AT_RISK', 'WON', 'LOST', 'CHURNED')
    ),
    CONSTRAINT ck_renewal_health CHECK (health_score >= 0 AND health_score <= 100)
);

CREATE INDEX idx_renewal_opportunity_stage ON renewal_opportunity (stage, next_action_at);

CREATE TABLE renewal_reminder (
    id                  BIGSERIAL    PRIMARY KEY,
    organization_id     VARCHAR(64)  NOT NULL,
    opportunity_id      BIGINT       NULL REFERENCES renewal_opportunity(id) ON DELETE SET NULL,
    reminder_type       VARCHAR(32)  NOT NULL,
    due_at              TIMESTAMPTZ  NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    channel             VARCHAR(32)  NOT NULL DEFAULT 'IN_APP',
    payload_json        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    sent_at             TIMESTAMPTZ  NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_reminder_type CHECK (
        reminder_type IN ('T30', 'T14', 'T7', 'GRACE', 'FAIL_PAY', 'UPSELL')
    ),
    CONSTRAINT ck_reminder_status CHECK (status IN ('PENDING', 'SENT', 'SKIPPED', 'CANCELLED'))
);

CREATE UNIQUE INDEX uq_renewal_reminder_pending
    ON renewal_reminder (organization_id, reminder_type, due_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_renewal_reminder_due ON renewal_reminder (status, due_at);
