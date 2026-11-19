-- Sellable shop AI add-ons. Super Admin configures here only.
-- Shop runtime maps these to ModuleCode AI_* via PlatformModuleCodeMapper + effective-config.
-- Per-shop shops.ai_features remains the operational toggle; plan modules OR into that gate.

INSERT INTO module_definition (code, business_type_code, name, description, sort_order) VALUES
    ('POLY_AI', 'POLYCLINIC', 'Polyclinic AI assist',
     'Draft-only voice Rx, patient summary, lab summary, and in-app assistant', 70),
    ('HOSPITAL_AI', 'HOSPITAL', 'Hospital AI assist',
     'Draft-only voice Rx, patient summary, lab summary, and in-app assistant', 70),
    ('PATHLAB_AI', 'PATHLAB', 'Path Lab AI assist',
     'Draft-only lab summary, patient summary, and in-app assistant', 70),
    ('PHARMACY_AI', 'PHARMACY', 'Pharmacy AI assist',
     'Draft-only voice Rx assist and in-app assistant', 70)
ON CONFLICT (code) DO NOTHING;

INSERT INTO feature_definition (code, module_code, name, description, sort_order) VALUES
    ('POLY_AI', 'POLY_AI', 'Polyclinic AI pack', 'Enables all polyclinic AI draft tools', 10),
    ('POLY_AI_VOICE', 'POLY_AI', 'Voice prescription draft', 'Speech / typed Rx draft — never auto-saves', 20),
    ('POLY_AI_SUMMARY', 'POLY_AI', 'Patient history AI summary', 'Draft visit/history summary from authorized records', 30),
    ('POLY_AI_LAB', 'POLY_AI', 'Lab report AI summary', 'Doctor-friendly lab summary — does not diagnose', 40),
    ('POLY_AI_CHAT', 'POLY_AI', 'AI assistant', 'In-app workflow help. Informational only', 50),
    ('HOSPITAL_AI', 'HOSPITAL_AI', 'Hospital AI pack', 'Enables all hospital AI draft tools', 10),
    ('HOSPITAL_AI_VOICE', 'HOSPITAL_AI', 'Voice prescription draft', 'Speech / typed Rx draft — never auto-saves', 20),
    ('HOSPITAL_AI_SUMMARY', 'HOSPITAL_AI', 'Patient history AI summary', 'Draft visit/history summary from authorized records', 30),
    ('HOSPITAL_AI_LAB', 'HOSPITAL_AI', 'Lab report AI summary', 'Doctor-friendly lab summary — does not diagnose', 40),
    ('HOSPITAL_AI_CHAT', 'HOSPITAL_AI', 'AI assistant', 'In-app workflow help. Informational only', 50),
    ('PATHLAB_AI', 'PATHLAB_AI', 'Path Lab AI pack', 'Enables Path Lab AI draft tools', 10),
    ('PATHLAB_AI_SUMMARY', 'PATHLAB_AI', 'Patient history AI summary', 'Draft history summary from authorized records', 20),
    ('PATHLAB_AI_LAB', 'PATHLAB_AI', 'Lab report AI summary', 'Doctor-friendly lab summary — does not diagnose', 30),
    ('PATHLAB_AI_CHAT', 'PATHLAB_AI', 'AI assistant', 'In-app workflow help. Informational only', 40),
    ('PHARMACY_AI', 'PHARMACY_AI', 'Pharmacy AI pack', 'Enables pharmacy AI draft tools', 10),
    ('PHARMACY_AI_VOICE', 'PHARMACY_AI', 'Voice prescription draft', 'Speech / typed Rx draft — never auto-saves', 20),
    ('PHARMACY_AI_CHAT', 'PHARMACY_AI', 'AI assistant', 'In-app workflow help. Informational only', 30)
ON CONFLICT (code) DO NOTHING;

-- Demo polyclinic plan: pack on so POLY-DEMO can try immediately. Super Admin can turn off.
UPDATE subscription_plan
SET feature_flags_json = COALESCE(feature_flags_json, '{}'::jsonb)
        || '{"POLY_AI": true, "POLY_AI_VOICE": true, "POLY_AI_SUMMARY": true, "POLY_AI_LAB": true, "POLY_AI_CHAT": true}'::jsonb,
    updated_at = NOW()
WHERE id = 'poly-starter';

INSERT INTO plan_feature (plan_id, feature_code, enabled, updated_at)
VALUES
    ('poly-starter', 'POLY_AI', TRUE, NOW()),
    ('poly-starter', 'POLY_AI_VOICE', TRUE, NOW()),
    ('poly-starter', 'POLY_AI_SUMMARY', TRUE, NOW()),
    ('poly-starter', 'POLY_AI_LAB', TRUE, NOW()),
    ('poly-starter', 'POLY_AI_CHAT', TRUE, NOW())
ON CONFLICT (plan_id, feature_code) DO UPDATE
    SET enabled = EXCLUDED.enabled, updated_at = NOW();

INSERT INTO plan_module (plan_id, module_code, enabled, updated_at)
VALUES ('poly-starter', 'POLY_AI', TRUE, NOW())
ON CONFLICT (plan_id, module_code) DO UPDATE
    SET enabled = EXCLUDED.enabled, updated_at = NOW();

INSERT INTO plan_feature (plan_id, feature_code, enabled, updated_at)
VALUES
    ('hospital-starter', 'HOSPITAL_AI', FALSE, NOW()),
    ('hospital-pro', 'HOSPITAL_AI', FALSE, NOW()),
    ('pathlab-starter', 'PATHLAB_AI', FALSE, NOW())
ON CONFLICT (plan_id, feature_code) DO NOTHING;

INSERT INTO plan_module (plan_id, module_code, enabled, updated_at)
VALUES
    ('hospital-starter', 'HOSPITAL_AI', FALSE, NOW()),
    ('hospital-pro', 'HOSPITAL_AI', FALSE, NOW()),
    ('pathlab-starter', 'PATHLAB_AI', FALSE, NOW())
ON CONFLICT (plan_id, module_code) DO NOTHING;
