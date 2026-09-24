-- Phase 4: usage metering (additive). School callers are not required to use these APIs.

CREATE TABLE usage_counter (
    organization_id   VARCHAR(64)  NOT NULL,
    limit_code        VARCHAR(64)  NOT NULL,
    period_key        VARCHAR(32)  NOT NULL DEFAULT 'ALL',
    used_value        BIGINT       NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (organization_id, limit_code, period_key)
);

CREATE INDEX idx_usage_counter_org ON usage_counter (organization_id);

CREATE TABLE usage_event (
    id                BIGSERIAL    PRIMARY KEY,
    organization_id   VARCHAR(64)  NOT NULL,
    limit_code        VARCHAR(64)  NOT NULL,
    period_key        VARCHAR(32)  NOT NULL DEFAULT 'ALL',
    delta             BIGINT       NOT NULL,
    used_after        BIGINT       NOT NULL,
    reason            VARCHAR(256),
    actor             VARCHAR(128),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_usage_event_org_created ON usage_event (organization_id, created_at DESC);
CREATE INDEX idx_usage_event_org_limit ON usage_event (organization_id, limit_code);
