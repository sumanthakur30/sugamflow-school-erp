-- =============================================================================
-- Enable Website CMS menu for HCP-01
-- DB: school_subscription_db  (AWS RDS)
--
-- Symptom: School Design shows only "Design Studio", not "Website CMS"
-- Cause: FEATURE_WEBSITE_CMS is false / missing on the assigned plan
--
-- Prefer Super Admin → Platform Subscription → School + Website when possible.
-- This script is the ops fallback.
-- =============================================================================

-- 1) What plan does HCP-01 have?
SELECT ts.organization_id, ts.plan_id, sp.name,
       sp.feature_flags_json ->> 'FEATURE_WEBSITE' AS website,
       sp.feature_flags_json ->> 'FEATURE_WEBSITE_CMS' AS website_cms
FROM tenant_subscription ts
JOIN subscription_plan sp ON sp.id = ts.plan_id
WHERE ts.organization_id = 'HCP-01';

-- 2) Turn on website flags on that plan (merge into existing JSON)
UPDATE subscription_plan sp
SET feature_flags_json = COALESCE(sp.feature_flags_json, '{}'::jsonb)
  || jsonb_build_object(
       'FEATURE_WEBSITE', true,
       'FEATURE_WEBSITE_CMS', true,
       'FEATURE_WEBSITE_ADMISSION', true,
       'FEATURE_WEBSITE_SEO', true,
       'FEATURE_WEBSITE_BLOG', true,
       'FEATURE_WEBSITE_ALUMNI', true,
       'FEATURE_CUSTOM_DOMAIN', true
     ),
    updated_at = NOW()
WHERE sp.id IN (
  SELECT plan_id FROM tenant_subscription WHERE organization_id = 'HCP-01'
);

-- 3) Verify
SELECT id, name,
       feature_flags_json ->> 'FEATURE_WEBSITE' AS website,
       feature_flags_json ->> 'FEATURE_WEBSITE_CMS' AS website_cms
FROM subscription_plan
WHERE id IN (SELECT plan_id FROM tenant_subscription WHERE organization_id = 'HCP-01');

-- After run:
--   Sign out / sign in on school.sugamflow.com as HCP owner
--   School Design → Website CMS should appear
--   Or open: https://school.sugamflow.com/admin/website-cms
