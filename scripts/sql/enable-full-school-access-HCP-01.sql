-- =============================================================================
-- FULL school access for org HCP-01
-- DB: school_subscription_db
--
-- Assigns plan "enterprise" with ALL school FEATURE_* flags = true
-- and limits = -1 (unlimited).
--
-- Does NOT require tenant_subscription_lifecycle (safe if V5 missing).
--
-- After run:
--   docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production \
--     up -d --force-recreate subscription-service
--   Sign out/in on school.sugamflow.com as HCP-01 owner
--
-- Note: menus unlock via flags. Phase B modules (library, hostel, transport,
-- attendance, exam, payroll, comms) still need those Docker services running.
-- =============================================================================

-- ---------- 0) Inspect ----------
SELECT id, code, name, active,
       feature_flags_json ->> 'FEATURE_ADMISSION' AS admission,
       jsonb_object_keys(COALESCE(feature_flags_json, '{}'::jsonb)) AS flag_sample
FROM subscription_plan
WHERE id IN ('starter', 'enterprise')
LIMIT 20;

SELECT organization_id, plan_id, assigned_at
FROM tenant_subscription
WHERE organization_id = 'HCP-01';

-- ---------- 1) Upsert ENTERPRISE plan (full flags + unlimited limits) ----------
INSERT INTO subscription_plan (
    id, code, name, plan_type, active, limits_json, feature_flags_json, updated_at
) VALUES (
    'enterprise',
    'ENTERPRISE',
    'Enterprise',
    'ENTERPRISE',
    TRUE,
    '{
      "maxStudents": -1,
      "maxTeachers": -1,
      "maxBranches": -1,
      "maxUsers": -1,
      "maxStorageGb": -1,
      "maxApiCalls": -1,
      "maxSms": -1,
      "maxWhatsApp": -1,
      "maxEmails": -1,
      "maxReports": -1,
      "maxCustomFields": -1,
      "maxSubjects": -1,
      "maxSections": -1,
      "maxClasses": -1,
      "maxSessions": -1,
      "aiUsageUnits": -1
    }'::jsonb,
    '{
      "FEATURE_DOCUMENT_STORAGE": true,
      "FEATURE_CLOUD_BACKUP": true,
      "FEATURE_BIOMETRIC": true,
      "FEATURE_GPS": true,
      "FEATURE_FACE_RECOGNITION": true,
      "FEATURE_LIBRARY": true,
      "FEATURE_HOSTEL": true,
      "FEATURE_TRANSPORT": true,
      "FEATURE_PAYROLL": true,
      "FEATURE_ACCOUNTING": true,
      "FEATURE_VISITOR": true,
      "FEATURE_HR": true,
      "FEATURE_INVENTORY": true,
      "FEATURE_API_ACCESS": true,
      "FEATURE_WHITE_LABEL": true,
      "FEATURE_CUSTOM_DOMAIN": true,
      "FEATURE_CUSTOM_BRANDING": true,
      "FEATURE_AUDIT_LOGS": true,
      "FEATURE_ROLE_LIMITS": true,
      "FEATURE_APPROVAL_WORKFLOW": true,
      "FEATURE_DIGITAL_SIGNATURE": true,
      "FEATURE_MULTI_PAYMENT_GATEWAY": true,
      "FEATURE_PARENT_APP": true,
      "FEATURE_TEACHER_APP": true,
      "FEATURE_STUDENT_APP": true,
      "FEATURE_MULTI_BRANCH": true,
      "FEATURE_OFFLINE_MODE": true,
      "FEATURE_ADMIN_CONFIG": true,
      "FEATURE_FORM_BUILDER": true,
      "FEATURE_WORKFLOW_BUILDER": true,
      "FEATURE_RULE_ENGINE": true,
      "FEATURE_REPORT_BUILDER": true,
      "FEATURE_ADMISSION": true,
      "FEATURE_FEE": true,
      "FEATURE_STUDENT_MASTER": true,
      "FEATURE_STAFF_MASTER": true,
      "FEATURE_ACADEMIC_LIFECYCLE": true,
      "FEATURE_OPS_DEPTH": true,
      "FEATURE_ATTENDANCE": true,
      "FEATURE_EXAM": true,
      "FEATURE_AI": true
    }'::jsonb,
    NOW()
)
ON CONFLICT (id) DO UPDATE
SET code = EXCLUDED.code,
    name = EXCLUDED.name,
    plan_type = EXCLUDED.plan_type,
    active = TRUE,
    limits_json = EXCLUDED.limits_json,
    feature_flags_json = EXCLUDED.feature_flags_json,
    updated_at = NOW();

-- ---------- 2) Assign HCP-01 → enterprise ----------
INSERT INTO tenant_subscription (organization_id, plan_id, assigned_at)
VALUES ('HCP-01', 'enterprise', NOW())
ON CONFLICT (organization_id) DO UPDATE
SET plan_id = 'enterprise',
    assigned_at = NOW();

-- ---------- 3) Optional lifecycle (skip if table missing — comment out on error) ----------
-- CREATE TABLE IF NOT EXISTS tenant_subscription_lifecycle (
--     organization_id      VARCHAR(64) PRIMARY KEY
--         REFERENCES tenant_subscription(organization_id) ON DELETE CASCADE,
--     status               VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
--     trial_ends_at        TIMESTAMPTZ NULL,
--     expires_at           TIMESTAMPTZ NULL,
--     grace_ends_at        TIMESTAMPTZ NULL,
--     grace_days           INT NOT NULL DEFAULT 7,
--     enforcement_enabled  BOOLEAN NOT NULL DEFAULT FALSE,
--     notes                VARCHAR(512) NULL,
--     created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
--     updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
-- );
--
-- INSERT INTO tenant_subscription_lifecycle (
--     organization_id, status, grace_days, enforcement_enabled, notes, created_at, updated_at
-- ) VALUES (
--     'HCP-01', 'ACTIVE', 7, FALSE, 'Full enterprise access', NOW(), NOW()
-- )
-- ON CONFLICT (organization_id) DO UPDATE
-- SET status = 'ACTIVE', expires_at = NULL, enforcement_enabled = FALSE, updated_at = NOW();

-- ---------- 4) Verify full access ----------
SELECT ts.organization_id,
       ts.plan_id,
       sp.active,
       (SELECT COUNT(*) FROM jsonb_each_text(sp.feature_flags_json) x WHERE x.value = 'true') AS flags_true,
       sp.feature_flags_json ->> 'FEATURE_ADMISSION' AS admission,
       sp.feature_flags_json ->> 'FEATURE_FEE' AS fee,
       sp.feature_flags_json ->> 'FEATURE_LIBRARY' AS library,
       sp.feature_flags_json ->> 'FEATURE_PAYROLL' AS payroll,
       sp.feature_flags_json ->> 'FEATURE_CUSTOM_BRANDING' AS branding,
       sp.limits_json ->> 'maxStudents' AS max_students
FROM tenant_subscription ts
JOIN subscription_plan sp ON sp.id = ts.plan_id
WHERE ts.organization_id = 'HCP-01';

-- Expect: plan_id=enterprise, flags_true >= 40, admission/fee/... = true, max_students = -1

-- List every flag for HCP-01 plan:
SELECT key AS feature_flag, value AS enabled
FROM tenant_subscription ts
JOIN subscription_plan sp ON sp.id = ts.plan_id
CROSS JOIN LATERAL jsonb_each_text(sp.feature_flags_json) AS f(key, value)
WHERE ts.organization_id = 'HCP-01'
ORDER BY key;
