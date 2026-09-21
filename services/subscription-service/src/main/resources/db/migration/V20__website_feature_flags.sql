-- Phase 0: School Website Platform catalog (module, features, limits).
-- Configure via Super Admin → Platform Subscription; enforce later via entitlements.

INSERT INTO module_definition (code, business_type_code, name, description, sort_order) VALUES
    ('SCHOOL_WEBSITE', 'SCHOOL', 'School Website', 'Public multi-tenant school website + CMS', 150)
ON CONFLICT (code) DO NOTHING;

INSERT INTO feature_definition (code, module_code, name, description, sort_order) VALUES
    ('FEATURE_WEBSITE', 'SCHOOL_WEBSITE', 'Website', 'Public school website enabled', 10),
    ('FEATURE_WEBSITE_CMS', 'SCHOOL_WEBSITE', 'Website CMS', 'School CMS admin for pages and media', 20),
    ('FEATURE_WEBSITE_ADMISSION', 'SCHOOL_WEBSITE', 'Online Admission', 'Public online admission on school domain', 30),
    ('FEATURE_WEBSITE_SEO', 'SCHOOL_WEBSITE', 'Website SEO', 'SEO metadata, sitemap, prerender', 40),
    ('FEATURE_WEBSITE_BLOG', 'SCHOOL_WEBSITE', 'Website Blog', 'Public blog module', 50)
ON CONFLICT (code) DO NOTHING;

INSERT INTO limit_definition (code, business_type_code, name, unit, aggregation, description, sort_order) VALUES
    ('website_pages', 'SCHOOL', 'Website pages', 'COUNT', 'NUMERIC', 'Max published CMS pages', 210),
    ('website_storage_gb', 'SCHOOL', 'Website storage', 'GB', 'NUMERIC', 'Media library storage quota', 220),
    ('website_custom_domains', 'SCHOOL', 'Custom domains', 'COUNT', 'NUMERIC', 'Max custom domains per school', 230)
ON CONFLICT (code) DO NOTHING;

-- Default off for school starter/basic; on for professional + enterprise style plans.
UPDATE subscription_plan
SET feature_flags_json = COALESCE(feature_flags_json, '{}'::jsonb) || jsonb_build_object(
        'FEATURE_WEBSITE', false,
        'FEATURE_WEBSITE_CMS', false,
        'FEATURE_WEBSITE_ADMISSION', false,
        'FEATURE_WEBSITE_SEO', false,
        'FEATURE_WEBSITE_BLOG', false
    ),
    updated_at = NOW()
WHERE id IN ('starter', 'basic', 'government', 'trust', 'custom')
   OR (id LIKE 'school-%' AND id NOT LIKE '%enterprise%' AND id NOT LIKE '%professional%' AND id NOT LIKE '%premium%');

UPDATE subscription_plan
SET feature_flags_json = COALESCE(feature_flags_json, '{}'::jsonb) || jsonb_build_object(
        'FEATURE_WEBSITE', true,
        'FEATURE_WEBSITE_CMS', true,
        'FEATURE_WEBSITE_ADMISSION', true,
        'FEATURE_WEBSITE_SEO', true,
        'FEATURE_WEBSITE_BLOG', false
    ),
    updated_at = NOW()
WHERE id IN ('professional', 'premium', 'enterprise')
   OR id LIKE '%professional%'
   OR id LIKE '%premium%'
   OR id LIKE '%enterprise%';

-- Avoid enabling website flags on CRM / shop vertical SKUs accidentally.
UPDATE subscription_plan
SET feature_flags_json = COALESCE(feature_flags_json, '{}'::jsonb) || jsonb_build_object(
        'FEATURE_WEBSITE', false,
        'FEATURE_WEBSITE_CMS', false,
        'FEATURE_WEBSITE_ADMISSION', false,
        'FEATURE_WEBSITE_SEO', false,
        'FEATURE_WEBSITE_BLOG', false
    ),
    updated_at = NOW()
WHERE id LIKE 'crm-%'
   OR id LIKE 'hospital-%'
   OR id LIKE 'poly-%'
   OR id LIKE 'pharmacy-%'
   OR id LIKE 'pathlab-%'
   OR id LIKE 'retail-%';

INSERT INTO plan_feature (plan_id, feature_code, enabled, updated_at)
SELECT p.id, f.key, (f.value = 'true'), NOW()
FROM subscription_plan p
CROSS JOIN LATERAL jsonb_each_text(p.feature_flags_json) AS f(key, value)
WHERE f.key IN (
    'FEATURE_WEBSITE',
    'FEATURE_WEBSITE_CMS',
    'FEATURE_WEBSITE_ADMISSION',
    'FEATURE_WEBSITE_SEO',
    'FEATURE_WEBSITE_BLOG'
)
ON CONFLICT (plan_id, feature_code) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = NOW();

-- Soft limits for school plans that have website enabled.
UPDATE subscription_plan
SET limits_json = COALESCE(limits_json, '{}'::jsonb) || jsonb_build_object(
        'website_pages', 50,
        'website_storage_gb', 5,
        'website_custom_domains', 2
    ),
    updated_at = NOW()
WHERE COALESCE((feature_flags_json->>'FEATURE_WEBSITE')::boolean, false) = true;

UPDATE subscription_plan
SET limits_json = COALESCE(limits_json, '{}'::jsonb) || jsonb_build_object(
        'website_pages', 200,
        'website_storage_gb', 25,
        'website_custom_domains', 5
    ),
    updated_at = NOW()
WHERE id = 'enterprise'
   OR id LIKE '%enterprise%';
