-- FEATURE_CRM_CONVERT: lead becomes a shop customer. Off on every plan until Super Admin enables it.

INSERT INTO feature_definition (code, module_code, name, description, sort_order) VALUES
    ('FEATURE_CRM_CONVERT', 'CRM_CORE', 'Convert lead to customer', 'Send a CRM lead to the shop customer list', 80)
ON CONFLICT (code) DO NOTHING;

UPDATE subscription_plan
SET feature_flags_json = COALESCE(feature_flags_json, '{}'::jsonb) || '{"FEATURE_CRM_CONVERT": false}'::jsonb,
    updated_at = NOW()
WHERE id IN ('crm-starter', 'crm-professional', 'crm-enterprise');

INSERT INTO plan_feature (plan_id, feature_code, enabled, updated_at)
SELECT p.id, f.key, (f.value = 'true'), NOW()
FROM subscription_plan p
CROSS JOIN LATERAL jsonb_each_text(p.feature_flags_json) AS f(key, value)
WHERE p.id IN ('crm-starter', 'crm-professional', 'crm-enterprise')
  AND f.key = 'FEATURE_CRM_CONVERT'
ON CONFLICT (plan_id, feature_code) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = NOW();
