-- Additive Jyotish SaaS catalog (standalone). Does NOT modify School / CRM / shop plan JSON.
-- Assign plans via Super Admin → Platform Subscription.

-- ---------------------------------------------------------------------------
-- Business type
-- ---------------------------------------------------------------------------
INSERT INTO business_type (code, name, description, sort_order) VALUES
    ('JYOTISH', 'Jyotish', 'Sugam Jyotish / astrology workspace (SaaS)', 15)
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Modules
-- ---------------------------------------------------------------------------
INSERT INTO module_definition (code, business_type_code, name, description, sort_order) VALUES
    ('JYOTISH_CORE', 'JYOTISH', 'Jyotish Core', 'Kundali generation, dasha, yoga, gochar', 10),
    ('JYOTISH_MATCHING', 'JYOTISH', 'Matching', 'Ashta-koota / manglik matching', 20),
    ('JYOTISH_REPORTS', 'JYOTISH', 'Reports', 'PDF kundali / matching reports', 30),
    ('JYOTISH_AI', 'JYOTISH', 'Jyotish AI', 'AI ask / interpretation assist', 40)
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Features (enforced by jyotish-service when jyotish.entitlement.enabled=true)
-- ---------------------------------------------------------------------------
INSERT INTO feature_definition (code, module_code, name, description, sort_order) VALUES
    ('FEATURE_JYOTISH', 'JYOTISH_CORE', 'Jyotish module', 'Master gate for Jyotish access', 10),
    ('FEATURE_JYOTISH_MATCHING', 'JYOTISH_MATCHING', 'Matching', 'Compatibility matching', 10),
    ('FEATURE_JYOTISH_REPORTS', 'JYOTISH_REPORTS', 'Reports', 'PDF report generation', 10),
    ('FEATURE_JYOTISH_AI', 'JYOTISH_AI', 'Jyotish AI', 'AI ask assistant', 10)
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Limits
-- ---------------------------------------------------------------------------
INSERT INTO limit_definition (code, business_type_code, name, unit, aggregation, description, sort_order) VALUES
    ('jyotish.max_users', 'JYOTISH', 'Jyotish users', 'COUNT', 'NUMERIC', 'Max Jyotish seats', 10),
    ('jyotish.max_kundali_month', 'JYOTISH', 'Kundali / month', 'COUNT', 'NUMERIC', 'Generated kundali quota', 20),
    ('jyotish.ai_asks_month', 'JYOTISH', 'AI asks / month', 'COUNT', 'NUMERIC', 'AI ask invocations', 30)
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Sellable plans
-- ---------------------------------------------------------------------------
INSERT INTO subscription_plan (id, code, name, plan_type, active, limits_json, feature_flags_json, updated_at)
VALUES (
    'jyotish-starter',
    'JYOTISH_STARTER',
    'Jyotish Starter',
    'JYOTISH_STARTER',
    TRUE,
    '{
      "jyotish.max_users": 3,
      "jyotish.max_kundali_month": 100,
      "jyotish.ai_asks_month": 0,
      "maxUsers": 3
    }',
    '{
      "FEATURE_JYOTISH": true,
      "FEATURE_JYOTISH_MATCHING": false,
      "FEATURE_JYOTISH_REPORTS": false,
      "FEATURE_JYOTISH_AI": false
    }',
    NOW()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO subscription_plan (id, code, name, plan_type, active, limits_json, feature_flags_json, updated_at)
VALUES (
    'jyotish-professional',
    'JYOTISH_PROFESSIONAL',
    'Jyotish Professional',
    'JYOTISH_PROFESSIONAL',
    TRUE,
    '{
      "jyotish.max_users": 10,
      "jyotish.max_kundali_month": 1000,
      "jyotish.ai_asks_month": 0,
      "maxUsers": 10
    }',
    '{
      "FEATURE_JYOTISH": true,
      "FEATURE_JYOTISH_MATCHING": true,
      "FEATURE_JYOTISH_REPORTS": true,
      "FEATURE_JYOTISH_AI": false
    }',
    NOW()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO subscription_plan (id, code, name, plan_type, active, limits_json, feature_flags_json, updated_at)
VALUES (
    'jyotish-enterprise',
    'JYOTISH_ENTERPRISE',
    'Jyotish Enterprise',
    'JYOTISH_ENTERPRISE',
    TRUE,
    '{
      "jyotish.max_users": 50,
      "jyotish.max_kundali_month": -1,
      "jyotish.ai_asks_month": 2000,
      "maxUsers": 50
    }',
    '{
      "FEATURE_JYOTISH": true,
      "FEATURE_JYOTISH_MATCHING": true,
      "FEATURE_JYOTISH_REPORTS": true,
      "FEATURE_JYOTISH_AI": true
    }',
    NOW()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO plan_module (plan_id, module_code, enabled, updated_at)
SELECT p.id, m.module_code, TRUE, NOW()
FROM (VALUES
    ('jyotish-starter', 'JYOTISH_CORE'),
    ('jyotish-professional', 'JYOTISH_CORE'),
    ('jyotish-professional', 'JYOTISH_MATCHING'),
    ('jyotish-professional', 'JYOTISH_REPORTS'),
    ('jyotish-enterprise', 'JYOTISH_CORE'),
    ('jyotish-enterprise', 'JYOTISH_MATCHING'),
    ('jyotish-enterprise', 'JYOTISH_REPORTS'),
    ('jyotish-enterprise', 'JYOTISH_AI')
) AS m(plan_id, module_code)
JOIN subscription_plan p ON p.id = m.plan_id
ON CONFLICT (plan_id, module_code) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = NOW();

INSERT INTO plan_feature (plan_id, feature_code, enabled, updated_at)
SELECT p.id, f.key, (f.value = 'true'), NOW()
FROM subscription_plan p
CROSS JOIN LATERAL jsonb_each_text(p.feature_flags_json) AS f(key, value)
WHERE p.id IN ('jyotish-starter', 'jyotish-professional', 'jyotish-enterprise')
ON CONFLICT (plan_id, feature_code) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = NOW();

INSERT INTO plan_limit (plan_id, limit_code, limit_value, updated_at)
SELECT p.id, l.key, (l.value)::bigint, NOW()
FROM subscription_plan p
CROSS JOIN LATERAL jsonb_each_text(p.limits_json) AS l(key, value)
WHERE p.id IN ('jyotish-starter', 'jyotish-professional', 'jyotish-enterprise')
  AND l.value ~ '^-?[0-9]+$'
ON CONFLICT (plan_id, limit_code) DO UPDATE SET limit_value = EXCLUDED.limit_value, updated_at = NOW();
