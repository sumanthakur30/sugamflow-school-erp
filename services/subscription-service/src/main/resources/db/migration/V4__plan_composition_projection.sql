-- Phase 2: normalized plan composition (dual-write projection of plan JSON).
-- Source of truth remains subscription_plan.limits_json / feature_flags_json.
-- No FK to catalog definitions so unknown keys still project safely.

CREATE TABLE plan_feature (
    plan_id         VARCHAR(64)  NOT NULL REFERENCES subscription_plan(id) ON DELETE CASCADE,
    feature_code    VARCHAR(96)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (plan_id, feature_code)
);

CREATE INDEX idx_plan_feature_code ON plan_feature (feature_code);

CREATE TABLE plan_limit (
    plan_id         VARCHAR(64)  NOT NULL REFERENCES subscription_plan(id) ON DELETE CASCADE,
    limit_code      VARCHAR(64)  NOT NULL,
    limit_value     BIGINT       NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (plan_id, limit_code)
);

CREATE INDEX idx_plan_limit_code ON plan_limit (limit_code);

CREATE TABLE plan_module (
    plan_id         VARCHAR(64)  NOT NULL REFERENCES subscription_plan(id) ON DELETE CASCADE,
    module_code     VARCHAR(64)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (plan_id, module_code)
);

CREATE INDEX idx_plan_module_code ON plan_module (module_code);
