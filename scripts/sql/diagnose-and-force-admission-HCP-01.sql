-- =============================================================================
-- DIAGNOSE + FORCE full access for HCP-01
-- DB: school_subscription_db  (must match SCHOOL_SUBSCRIPTION_DB_URL on EC2)
-- =============================================================================

-- 1) Confirm you are on the right database
SELECT current_database() AS db, current_user AS db_user;

-- 2) Current assignment + flag (THIS must show true)
SELECT ts.organization_id,
       ts.plan_id,
       sp.feature_flags_json ->> 'FEATURE_ADMISSION' AS admission_text,
       sp.feature_flags_json -> 'FEATURE_ADMISSION' AS admission_json,
       jsonb_typeof(sp.feature_flags_json -> 'FEATURE_ADMISSION') AS admission_type,
       (SELECT COUNT(*) FROM jsonb_each(sp.feature_flags_json) e WHERE e.value = 'true'::jsonb) AS true_flags
FROM tenant_subscription ts
JOIN subscription_plan sp ON sp.id = ts.plan_id
WHERE ts.organization_id IN ('HCP-01', 'HCP.01');

-- 3) If no row above, list ALL tenant assignments
SELECT organization_id, plan_id, assigned_at FROM tenant_subscription ORDER BY assigned_at DESC;

-- 4) FORCE enterprise + FEATURE_ADMISSION boolean true (not string)
UPDATE subscription_plan
SET feature_flags_json = feature_flags_json || jsonb_build_object('FEATURE_ADMISSION', true),
    updated_at = NOW()
WHERE id IN ('enterprise', 'starter');

-- Ensure boolean type (fix if someone stored "true" as string)
UPDATE subscription_plan
SET feature_flags_json = jsonb_set(
      feature_flags_json,
      '{FEATURE_ADMISSION}',
      'true'::jsonb,
      true
    ),
    updated_at = NOW()
WHERE id IN ('enterprise', 'starter');

INSERT INTO tenant_subscription (organization_id, plan_id, assigned_at)
VALUES ('HCP-01', 'enterprise', NOW())
ON CONFLICT (organization_id) DO UPDATE
SET plan_id = 'enterprise', assigned_at = NOW();

-- 5) Re-verify
SELECT organization_id, plan_id,
       feature_flags_json -> 'FEATURE_ADMISSION' AS admission,
       jsonb_typeof(feature_flags_json -> 'FEATURE_ADMISSION') AS typ
FROM tenant_subscription ts
JOIN subscription_plan sp ON sp.id = ts.plan_id
WHERE organization_id = 'HCP-01';
-- Expect: enterprise | true | boolean
