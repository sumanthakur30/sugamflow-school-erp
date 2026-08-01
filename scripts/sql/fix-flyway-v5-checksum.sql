-- =============================================================================
-- Fix Flyway V5 checksum mismatch (null vs -964129854)
-- Error: Migration checksum mismatch for migration version 5
--        Applied: null  |  Resolved locally: -964129854
-- DB: school_subscription_db
-- =============================================================================

-- 1) Inspect current V5 history row
SELECT installed_rank, version, description, type, checksum, success, installed_on
FROM flyway_schema_history_school_subscription
WHERE version = '5'
ORDER BY installed_rank;

-- 2) Ensure table exists (Flyway will NOT re-run V5)
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

-- 3) Repair checksum to match JAR (Flyway 10 / Spring Boot 3.3.5)
UPDATE flyway_schema_history_school_subscription
SET checksum = -964129854,
    success  = TRUE
WHERE version = '5';

-- If no V5 row exists at all, insert one (adjust installed_rank if needed)
INSERT INTO flyway_schema_history_school_subscription (
    installed_rank, version, description, type, script, checksum,
    installed_by, installed_on, execution_time, success
)
SELECT
    COALESCE((SELECT MIN(installed_rank) - 1 FROM flyway_schema_history_school_subscription), 1),
    '5',
    'tenant subscription lifecycle',
    'SQL',
    'V5__tenant_subscription_lifecycle.sql',
    -964129854,
    'manual',
    NOW(),
    0,
    TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM flyway_schema_history_school_subscription WHERE version = '5'
);

-- 4) Verify
SELECT installed_rank, version, description, checksum, success
FROM flyway_schema_history_school_subscription
WHERE version = '5';

SELECT to_regclass('public.tenant_subscription_lifecycle') AS lifecycle_table;
SELECT COUNT(*) FROM tenant_subscription_lifecycle;
