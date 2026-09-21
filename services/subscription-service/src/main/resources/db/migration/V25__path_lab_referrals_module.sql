-- Chargeable Path Lab incoming doctor referrals + collect payment.
-- Super Admin enables via Platform Subscription (pathlab-starter / plan toggle).
-- Shop runtime enforces ModuleCode.PATH_LAB_REFERRALS via effective-config.

INSERT INTO module_definition (code, business_type_code, name, description, sort_order) VALUES
    ('PATH_LAB_REFERRALS', 'PATHLAB', 'Incoming doctor referrals',
     'Accept connected-clinic referrals and collect Path Lab payment (chargeable)', 50)
ON CONFLICT (code) DO NOTHING;

INSERT INTO feature_definition (code, module_code, name, description, sort_order) VALUES
    ('PATH_LAB_REFERRALS', 'PATH_LAB_REFERRALS', 'Incoming doctor referrals',
     'Incoming board, Accept, and collect payment stay on the Path Lab shop', 10)
ON CONFLICT (code) DO NOTHING;

UPDATE subscription_plan
SET feature_flags_json = COALESCE(feature_flags_json, '{}'::jsonb)
        || '{"PATH_LAB_REFERRALS": true}'::jsonb,
    updated_at = NOW()
WHERE id = 'pathlab-starter';

INSERT INTO plan_feature (plan_id, feature_code, enabled, updated_at)
VALUES ('pathlab-starter', 'PATH_LAB_REFERRALS', TRUE, NOW())
ON CONFLICT (plan_id, feature_code) DO UPDATE
    SET enabled = EXCLUDED.enabled, updated_at = NOW();

INSERT INTO plan_module (plan_id, module_code, enabled, updated_at)
VALUES ('pathlab-starter', 'PATH_LAB_REFERRALS', TRUE, NOW())
ON CONFLICT (plan_id, module_code) DO UPDATE
    SET enabled = EXCLUDED.enabled, updated_at = NOW();
