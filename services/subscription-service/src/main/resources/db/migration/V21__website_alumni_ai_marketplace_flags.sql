-- Phase 6: alumni / AI / marketplace website feature flags.
INSERT INTO feature_definition (code, module_code, name, description, sort_order) VALUES
    ('FEATURE_WEBSITE_ALUMNI', 'SCHOOL_WEBSITE', 'Website Alumni', 'Public alumni directory on school website', 60),
    ('FEATURE_WEBSITE_AI', 'SCHOOL_WEBSITE', 'Website AI Assist', 'AI draft helpers for CMS/SEO content', 70),
    ('FEATURE_WEBSITE_MARKETPLACE', 'SCHOOL_WEBSITE', 'Website Marketplace', 'Template marketplace for school websites', 80)
ON CONFLICT (code) DO NOTHING;

UPDATE subscription_plan
SET feature_flags_json = COALESCE(feature_flags_json, '{}'::jsonb) || jsonb_build_object(
        'FEATURE_WEBSITE_ALUMNI', false,
        'FEATURE_WEBSITE_AI', false,
        'FEATURE_WEBSITE_MARKETPLACE', false
    ),
    updated_at = NOW()
WHERE COALESCE((feature_flags_json->>'FEATURE_WEBSITE')::boolean, false) = false
   OR id LIKE 'crm-%'
   OR id LIKE 'hospital-%'
   OR id LIKE 'pharmacy-%'
   OR id LIKE 'pathlab-%'
   OR id LIKE 'retail-%';

UPDATE subscription_plan
SET feature_flags_json = COALESCE(feature_flags_json, '{}'::jsonb) || jsonb_build_object(
        'FEATURE_WEBSITE_ALUMNI', true,
        'FEATURE_WEBSITE_AI', true,
        'FEATURE_WEBSITE_MARKETPLACE', true
    ),
    updated_at = NOW()
WHERE id IN ('enterprise')
   OR id LIKE '%enterprise%';

INSERT INTO plan_feature (plan_id, feature_code, enabled, updated_at)
SELECT p.id, f.key, (f.value = 'true'), NOW()
FROM subscription_plan p
CROSS JOIN LATERAL jsonb_each_text(p.feature_flags_json) AS f(key, value)
WHERE f.key IN (
    'FEATURE_WEBSITE_ALUMNI',
    'FEATURE_WEBSITE_AI',
    'FEATURE_WEBSITE_MARKETPLACE'
)
ON CONFLICT (plan_id, feature_code) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = NOW();

INSERT INTO limit_definition (code, business_type_code, name, unit, aggregation, description, sort_order) VALUES
    ('website_campuses', 'SCHOOL', 'Website campuses', 'COUNT', 'NUMERIC', 'Max campus websites per school', 240)
ON CONFLICT (code) DO NOTHING;

UPDATE subscription_plan
SET limits_json = COALESCE(limits_json, '{}'::jsonb) || jsonb_build_object('website_campuses', 3),
    updated_at = NOW()
WHERE COALESCE((feature_flags_json->>'FEATURE_WEBSITE')::boolean, false) = true;

UPDATE subscription_plan
SET limits_json = COALESCE(limits_json, '{}'::jsonb) || jsonb_build_object('website_campuses', 20),
    updated_at = NOW()
WHERE id = 'enterprise' OR id LIKE '%enterprise%';
