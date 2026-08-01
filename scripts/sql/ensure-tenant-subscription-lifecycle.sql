-- =============================================================================
-- Ensure tenant_subscription_lifecycle exists (Flyway already at V18 — will NOT re-run V5)
-- DB: school_subscription_db
-- =============================================================================

-- 1) Does the table exist?
SELECT to_regclass('public.tenant_subscription_lifecycle') AS lifecycle_table;

-- 2) Create if missing
CREATE TABLE IF NOT EXISTS tenant_subscription_lifecycle (
    organization_id      VARCHAR(64) PRIMARY KEY
        REFERENCES tenant_subscription(organization_id) ON DELETE CASCADE,
    status               VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    trial_ends_at        TIMESTAMPTZ NULL,
    expires_at           TIMESTAMPTZ NULL,
    grace_ends_at        TIMESTAMPTZ NULL,
    grace_days           INT NOT NULL DEFAULT 7,
    enforcement_enabled  BOOLEAN NOT NULL DEFAULT FALSE,
    notes                VARCHAR(512) NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tenant_lifecycle_status
  ON tenant_subscription_lifecycle (status);
CREATE INDEX IF NOT EXISTS idx_tenant_lifecycle_expires
  ON tenant_subscription_lifecycle (expires_at);

INSERT INTO tenant_subscription_lifecycle (
    organization_id, status, grace_days, enforcement_enabled, notes
)
SELECT organization_id, 'ACTIVE', 7, FALSE, 'manual backfill'
FROM tenant_subscription
ON CONFLICT (organization_id) DO NOTHING;

-- 3) Verify
SELECT to_regclass('public.tenant_subscription_lifecycle') AS lifecycle_table;
SELECT COUNT(*) AS lifecycle_rows FROM tenant_subscription_lifecycle;
SELECT organization_id, plan_id FROM tenant_subscription WHERE organization_id = 'HCP-01';
