-- CBSE Compliance module catalog (P0). Default off; enable on professional+ school plans.

INSERT INTO module_definition (code, business_type_code, name, description, sort_order) VALUES
    ('SCHOOL_COMPLIANCE', 'SCHOOL', 'Board Compliance', 'CBSE / board mandatory data submission & disclosure', 160)
ON CONFLICT (code) DO NOTHING;

INSERT INTO feature_definition (code, module_code, name, description, sort_order) VALUES
    ('FEATURE_CBSE_COMPLIANCE', 'SCHOOL_COMPLIANCE', 'CBSE Compliance', 'Compliance dashboard, profile, campaigns, exports', 10),
    ('FEATURE_BOARD_DISCLOSURE', 'SCHOOL_COMPLIANCE', 'Board Disclosure', 'Mandatory public disclosure auto-publish to website', 20)
ON CONFLICT (code) DO NOTHING;

UPDATE subscription_plan
SET feature_flags_json = COALESCE(feature_flags_json, '{}'::jsonb) || jsonb_build_object(
        'FEATURE_CBSE_COMPLIANCE', false,
        'FEATURE_BOARD_DISCLOSURE', false
    ),
    updated_at = NOW()
WHERE id IN ('starter', 'basic', 'government', 'trust', 'custom')
   OR (id LIKE 'school-%' AND id NOT LIKE '%enterprise%' AND id NOT LIKE '%professional%' AND id NOT LIKE '%premium%');

UPDATE subscription_plan
SET feature_flags_json = COALESCE(feature_flags_json, '{}'::jsonb) || jsonb_build_object(
        'FEATURE_CBSE_COMPLIANCE', true,
        'FEATURE_BOARD_DISCLOSURE', true
    ),
    updated_at = NOW()
WHERE id IN ('professional', 'premium', 'enterprise')
   OR id LIKE '%professional%'
   OR id LIKE '%premium%'
   OR id LIKE '%enterprise%';

UPDATE subscription_plan
SET feature_flags_json = COALESCE(feature_flags_json, '{}'::jsonb) || jsonb_build_object(
        'FEATURE_CBSE_COMPLIANCE', false,
        'FEATURE_BOARD_DISCLOSURE', false
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
WHERE f.key IN ('FEATURE_CBSE_COMPLIANCE', 'FEATURE_BOARD_DISCLOSURE')
ON CONFLICT (plan_id, feature_code) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = NOW();
