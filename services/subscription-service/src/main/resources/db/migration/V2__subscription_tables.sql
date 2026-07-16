-- Phase 2: subscription plans and tenant assignments.

CREATE TABLE subscription_plan (
    id                  VARCHAR(64)  PRIMARY KEY,
    code                VARCHAR(64)  NOT NULL UNIQUE,
    name                VARCHAR(128) NOT NULL,
    plan_type           VARCHAR(64)  NOT NULL,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    limits_json         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    feature_flags_json  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE tenant_subscription (
    organization_id     VARCHAR(64)  PRIMARY KEY,
    plan_id             VARCHAR(64)  NOT NULL REFERENCES subscription_plan(id),
    assigned_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tenant_subscription_plan ON tenant_subscription (plan_id);
