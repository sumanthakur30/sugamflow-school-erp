-- Referring-doctor Path Lab PDF viewing. Configure here only; shop enforces via effective-config.
-- POLY-DEMO (poly-starter) includes it so Rajeev can try immediately. Super Admin can turn it off.

INSERT INTO module_definition (code, business_type_code, name, description, sort_order) VALUES
    ('DOCTOR_LAB_REPORTS', 'POLYCLINIC', 'Doctor lab reports',
     'Referring doctor can view connected and external Path Lab report PDFs', 40),
    ('HOSPITAL_DOCTOR_LAB_REPORTS', 'HOSPITAL', 'Doctor lab reports',
     'Referring doctor can view connected and external Path Lab report PDFs', 60)
ON CONFLICT (code) DO NOTHING;

INSERT INTO feature_definition (code, module_code, name, description, sort_order) VALUES
    ('DOCTOR_LAB_REPORTS', 'DOCTOR_LAB_REPORTS', 'Doctor lab reports',
     'View referred Path Lab PDFs from the clinic workspace', 10),
    ('HOSPITAL_DOCTOR_LAB_REPORTS', 'HOSPITAL_DOCTOR_LAB_REPORTS', 'Doctor lab reports',
     'View referred Path Lab PDFs from the hospital workspace', 10)
ON CONFLICT (code) DO NOTHING;

UPDATE subscription_plan
SET feature_flags_json = jsonb_set(
        COALESCE(feature_flags_json::jsonb, '{}'::jsonb),
        '{DOCTOR_LAB_REPORTS}',
        'true'::jsonb,
        true
    )::text,
    updated_at = NOW()
WHERE id = 'poly-starter'
  AND COALESCE(feature_flags_json, '') NOT LIKE '%DOCTOR_LAB_REPORTS%';

UPDATE subscription_plan
SET feature_flags_json = jsonb_set(
        COALESCE(feature_flags_json::jsonb, '{}'::jsonb),
        '{HOSPITAL_DOCTOR_LAB_REPORTS}',
        'false'::jsonb,
        true
    )::text,
    updated_at = NOW()
WHERE id IN ('hospital-starter', 'hospital-pro')
  AND COALESCE(feature_flags_json, '') NOT LIKE '%HOSPITAL_DOCTOR_LAB_REPORTS%';

INSERT INTO plan_feature (plan_id, feature_code, enabled, updated_at)
VALUES ('poly-starter', 'DOCTOR_LAB_REPORTS', TRUE, NOW())
ON CONFLICT (plan_id, feature_code) DO UPDATE
    SET enabled = EXCLUDED.enabled, updated_at = NOW();

INSERT INTO plan_module (plan_id, module_code, enabled, updated_at)
VALUES ('poly-starter', 'DOCTOR_LAB_REPORTS', TRUE, NOW())
ON CONFLICT (plan_id, module_code) DO UPDATE
    SET enabled = EXCLUDED.enabled, updated_at = NOW();

INSERT INTO plan_feature (plan_id, feature_code, enabled, updated_at)
VALUES
    ('hospital-starter', 'HOSPITAL_DOCTOR_LAB_REPORTS', FALSE, NOW()),
    ('hospital-pro', 'HOSPITAL_DOCTOR_LAB_REPORTS', FALSE, NOW())
ON CONFLICT (plan_id, feature_code) DO NOTHING;

INSERT INTO plan_module (plan_id, module_code, enabled, updated_at)
VALUES
    ('hospital-starter', 'HOSPITAL_DOCTOR_LAB_REPORTS', FALSE, NOW()),
    ('hospital-pro', 'HOSPITAL_DOCTOR_LAB_REPORTS', FALSE, NOW())
ON CONFLICT (plan_id, module_code) DO NOTHING;
