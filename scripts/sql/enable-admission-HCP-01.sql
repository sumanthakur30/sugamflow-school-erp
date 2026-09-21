-- =============================================================================
-- Enable Admission for HCP-01 — SAFE when lifecycle table is missing
-- DB: school_subscription_db
--
-- Error you hit: relation "tenant_subscription_lifecycle" does not exist
-- Cause: Flyway never applied V5+ on this DB (or old subscription image).
--
-- Entitlements (FEATURE_ADMISSION) only need:
--   subscription_plan.feature_flags_json
--   tenant_subscription (org → plan)
-- Lifecycle is optional for Admission menu.
-- =============================================================================

-- ---------- A) What tables / Flyway version exist? ----------
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN (
    'subscription_plan',
    'tenant_subscription',
    'tenant_subscription_lifecycle',
    'flyway_schema_history_school_subscription'
  )
ORDER BY table_name;

SELECT version, description, success, installed_on
FROM flyway_schema_history_school_subscription
ORDER BY installed_rank;

-- ---------- B) Plans (must see starter / FEATURE_ADMISSION) ----------
SELECT id, code, name, active,
       feature_flags_json ->> 'FEATURE_ADMISSION' AS feature_admission
FROM subscription_plan
ORDER BY id;

-- ---------- C) Ensure starter has FEATURE_ADMISSION = true ----------
INSERT INTO subscription_plan (
    id, code, name, plan_type, active, limits_json, feature_flags_json, updated_at
) VALUES (
    'starter',
    'starter',
    'Starter',
    'STARTER',
    TRUE,
    '{"maxStudents":200,"maxTeachers":20,"maxBranches":3,"maxUsers":30}'::jsonb,
    '{"FEATURE_ADMISSION":true,"FEATURE_FEE":true,"FEATURE_STUDENT_MASTER":true,"FEATURE_STAFF_MASTER":true,"FEATURE_ACADEMIC_LIFECYCLE":true,"FEATURE_ATTENDANCE":true,"FEATURE_EXAM":true,"FEATURE_MULTI_BRANCH":true,"FEATURE_ADMIN_CONFIG":true}'::jsonb,
    NOW()
)
ON CONFLICT (id) DO NOTHING;

UPDATE subscription_plan
SET feature_flags_json =
      COALESCE(feature_flags_json, '{}'::jsonb) || '{"FEATURE_ADMISSION": true}'::jsonb,
    updated_at = NOW()
WHERE id = 'starter';

UPDATE subscription_plan
SET feature_flags_json =
      COALESCE(feature_flags_json, '{}'::jsonb) || '{"FEATURE_ADMISSION": true}'::jsonb,
    updated_at = NOW()
WHERE id = 'enterprise';

-- ---------- D) Assign HCP-01 → starter (THIS is what unlocks Admission) ----------
INSERT INTO tenant_subscription (organization_id, plan_id, assigned_at)
VALUES ('HCP-01', 'starter', NOW())
ON CONFLICT (organization_id) DO UPDATE
SET plan_id = EXCLUDED.plan_id,
    assigned_at = EXCLUDED.assigned_at;

-- ---------- E) Verify (no lifecycle needed) ----------
SELECT ts.organization_id,
       ts.plan_id,
       sp.feature_flags_json ->> 'FEATURE_ADMISSION' AS feature_admission
FROM tenant_subscription ts
JOIN subscription_plan sp ON sp.id = ts.plan_id
WHERE ts.organization_id = 'HCP-01';

-- Expect: HCP-01 | starter | true


-- =============================================================================
-- OPTIONAL F) Create lifecycle table (only if you want V5 schema present)
-- Skip if step D already succeeded. Safe to run once.
-- =============================================================================

CREATE TABLE IF NOT EXISTS tenant_subscription_lifecycle (
    organization_id      VARCHAR(64)  PRIMARY KEY
        REFERENCES tenant_subscription(organization_id) ON DELETE CASCADE,
    status               VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    trial_ends_at        TIMESTAMPTZ  NULL,
    expires_at           TIMESTAMPTZ  NULL,
    grace_ends_at        TIMESTAMPTZ  NULL,
    grace_days           INT          NOT NULL DEFAULT 7,
    enforcement_enabled  BOOLEAN      NOT NULL DEFAULT FALSE,
    notes                VARCHAR(512) NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tenant_lifecycle_status
    ON tenant_subscription_lifecycle (status);
CREATE INDEX IF NOT EXISTS idx_tenant_lifecycle_expires
    ON tenant_subscription_lifecycle (expires_at);

INSERT INTO tenant_subscription_lifecycle (
    organization_id, status, trial_ends_at, expires_at, grace_ends_at,
    grace_days, enforcement_enabled, notes, created_at, updated_at
) VALUES (
    'HCP-01', 'ACTIVE', NULL, NULL, NULL, 7, FALSE,
    'Manual SQL: enable Admission for HCP-01', NOW(), NOW()
)
ON CONFLICT (organization_id) DO UPDATE
SET status = 'ACTIVE',
    expires_at = NULL,
    grace_ends_at = NULL,
    enforcement_enabled = FALSE,
    notes = 'Manual SQL: enable Admission for HCP-01',
    updated_at = NOW();


-- =============================================================================
-- After SQL on EC2 — clear cache:
--   docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production \
--     up -d --force-recreate subscription-service
--
-- If flyway history max version < 5, also redeploy a newer subscription-service image
-- so future migrations apply (do NOT fake Flyway rows unless you know the risk).
-- =============================================================================
