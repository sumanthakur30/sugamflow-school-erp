-- Phase 0 CRM platform prep: standalone CRM catalog + sellable plans (additive only).
-- Does NOT modify existing School/ERP plan rows or entitlement JSON for non-CRM plans.
-- CRM UI is not shipped in this phase; Super Admin can see CRM plans in catalog.

-- ---------------------------------------------------------------------------
-- Business type: standalone CRM (sell without ERP)
-- ---------------------------------------------------------------------------
INSERT INTO business_type (code, name, description, sort_order) VALUES
    ('CRM', 'CRM', 'Standalone / integrated Lead Management CRM (business-agnostic)', 5)
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Modules (catalog only; enforcement arrives with crm-service)
-- ---------------------------------------------------------------------------
INSERT INTO module_definition (code, business_type_code, name, description, sort_order) VALUES
    ('CRM_CORE', 'CRM', 'CRM Core', 'Leads, contacts, accounts, pipelines, activities', 10),
    ('CRM_QUOTE', 'CRM', 'Quotations', 'Quotes, GST snapshot, share, acceptance', 20),
    ('CRM_AUTOMATION', 'CRM', 'Sales Automation', 'Assignment rules, workflows, SLA, sequences', 30),
    ('CRM_CAMPAIGN', 'CRM', 'Campaigns', 'Campaigns and attribution', 40),
    ('CRM_AI', 'CRM', 'CRM AI', 'Summaries, scoring assist, next-best-action, OCR', 50),
    ('CRM_COMMS', 'CRM', 'CRM Communications', 'WhatsApp / SMS / Email hub integration', 60)
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Features
-- ---------------------------------------------------------------------------
INSERT INTO feature_definition (code, module_code, name, description, sort_order) VALUES
    ('FEATURE_CRM', 'CRM_CORE', 'CRM module', 'Master gate for CRM access', 10),
    ('FEATURE_CRM_LEADS', 'CRM_CORE', 'Lead management', 'Lead capture and lifecycle', 20),
    ('FEATURE_CRM_PIPELINE', 'CRM_CORE', 'Pipelines', 'Configurable pipelines and stages', 30),
    ('FEATURE_CRM_ACTIVITIES', 'CRM_CORE', 'Activities', 'Tasks, calls, meetings, timeline', 40),
    ('FEATURE_CRM_IMPORT', 'CRM_CORE', 'Import', 'CSV / Excel lead import', 50),
    ('FEATURE_CRM_API', 'CRM_CORE', 'CRM API', 'Public ingest and CRM REST API', 60),
    ('FEATURE_CRM_QUOTE', 'CRM_QUOTE', 'Quotations', 'Quote builder and share', 10),
    ('FEATURE_CRM_APPROVAL', 'CRM_QUOTE', 'Quote approval', 'Discount / quote approvals', 20),
    ('FEATURE_CRM_AUTOMATION', 'CRM_AUTOMATION', 'Automation', 'Workflows and assignment engine', 10),
    ('FEATURE_CRM_SEQUENCES', 'CRM_AUTOMATION', 'Sequences', 'Multi-step follow-up sequences', 20),
    ('FEATURE_CRM_CAMPAIGN', 'CRM_CAMPAIGN', 'Campaigns', 'Campaign objects and ROI', 10),
    ('FEATURE_CRM_AI', 'CRM_AI', 'CRM AI', 'AI assist pack', 10),
    ('FEATURE_CRM_WHATSAPP', 'CRM_COMMS', 'WhatsApp CRM', 'WhatsApp Business sequences', 10),
    ('FEATURE_CRM_SMS', 'CRM_COMMS', 'SMS CRM', 'SMS sequences', 20),
    ('FEATURE_CRM_EMAIL', 'CRM_COMMS', 'Email CRM', 'Email sequences and tracking', 30)
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Limits (CRM-scoped codes; do not collide with School maxStudents semantics)
-- ---------------------------------------------------------------------------
INSERT INTO limit_definition (code, business_type_code, name, unit, aggregation, description, sort_order) VALUES
    ('crm.max_users', 'CRM', 'CRM users', 'COUNT', 'NUMERIC', 'Max CRM seats', 10),
    ('crm.max_pipelines', 'CRM', 'Pipelines', 'COUNT', 'NUMERIC', 'Max pipelines per workspace', 20),
    ('crm.max_leads', 'CRM', 'Leads', 'COUNT', 'NUMERIC', 'Max active leads (-1 unlimited)', 30),
    ('crm.max_storage_mb', 'CRM', 'CRM storage', 'MB', 'NUMERIC', 'Attachment storage', 40),
    ('crm.max_api_calls_month', 'CRM', 'CRM API calls / month', 'COUNT', 'NUMERIC', 'Public + partner API quota', 50),
    ('crm.ai_calls_month', 'CRM', 'AI calls / month', 'COUNT', 'NUMERIC', 'AI assist invocations', 60),
    ('crm.max_whatsapp_month', 'CRM', 'WhatsApp / month', 'COUNT', 'NUMERIC', 'Outbound WhatsApp messages', 70)
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Sellable CRM plans (standalone). Existing School plans untouched.
-- ---------------------------------------------------------------------------
INSERT INTO subscription_plan (id, code, name, plan_type, active, limits_json, feature_flags_json, updated_at)
VALUES (
    'crm-starter',
    'CRM_STARTER',
    'CRM Starter',
    'CRM_STARTER',
    TRUE,
    '{
      "crm.max_users": 5,
      "crm.max_pipelines": 1,
      "crm.max_leads": 2000,
      "crm.max_storage_mb": 1024,
      "crm.max_api_calls_month": 5000,
      "crm.ai_calls_month": 0,
      "crm.max_whatsapp_month": 200,
      "maxUsers": 5,
      "maxStorageGb": 1,
      "maxApiCalls": 5000,
      "maxWhatsApp": 200,
      "maxSms": 200,
      "maxEmails": 1000
    }'::jsonb,
    '{
      "FEATURE_CRM": true,
      "FEATURE_CRM_LEADS": true,
      "FEATURE_CRM_PIPELINE": true,
      "FEATURE_CRM_ACTIVITIES": true,
      "FEATURE_CRM_IMPORT": true,
      "FEATURE_CRM_API": false,
      "FEATURE_CRM_QUOTE": false,
      "FEATURE_CRM_APPROVAL": false,
      "FEATURE_CRM_AUTOMATION": false,
      "FEATURE_CRM_SEQUENCES": false,
      "FEATURE_CRM_CAMPAIGN": false,
      "FEATURE_CRM_AI": false,
      "FEATURE_CRM_WHATSAPP": false,
      "FEATURE_CRM_SMS": false,
      "FEATURE_CRM_EMAIL": true
    }'::jsonb,
    NOW()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO subscription_plan (id, code, name, plan_type, active, limits_json, feature_flags_json, updated_at)
VALUES (
    'crm-professional',
    'CRM_PROFESSIONAL',
    'CRM Professional',
    'CRM_PROFESSIONAL',
    TRUE,
    '{
      "crm.max_users": 25,
      "crm.max_pipelines": 10,
      "crm.max_leads": 25000,
      "crm.max_storage_mb": 10240,
      "crm.max_api_calls_month": 100000,
      "crm.ai_calls_month": 0,
      "crm.max_whatsapp_month": 5000,
      "maxUsers": 25,
      "maxStorageGb": 10,
      "maxApiCalls": 100000,
      "maxWhatsApp": 5000,
      "maxSms": 5000,
      "maxEmails": 20000
    }'::jsonb,
    '{
      "FEATURE_CRM": true,
      "FEATURE_CRM_LEADS": true,
      "FEATURE_CRM_PIPELINE": true,
      "FEATURE_CRM_ACTIVITIES": true,
      "FEATURE_CRM_IMPORT": true,
      "FEATURE_CRM_API": true,
      "FEATURE_CRM_QUOTE": true,
      "FEATURE_CRM_APPROVAL": true,
      "FEATURE_CRM_AUTOMATION": true,
      "FEATURE_CRM_SEQUENCES": true,
      "FEATURE_CRM_CAMPAIGN": false,
      "FEATURE_CRM_AI": false,
      "FEATURE_CRM_WHATSAPP": true,
      "FEATURE_CRM_SMS": true,
      "FEATURE_CRM_EMAIL": true
    }'::jsonb,
    NOW()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO subscription_plan (id, code, name, plan_type, active, limits_json, feature_flags_json, updated_at)
VALUES (
    'crm-enterprise',
    'CRM_ENTERPRISE',
    'CRM Enterprise',
    'CRM_ENTERPRISE',
    TRUE,
    '{
      "crm.max_users": -1,
      "crm.max_pipelines": -1,
      "crm.max_leads": -1,
      "crm.max_storage_mb": -1,
      "crm.max_api_calls_month": -1,
      "crm.ai_calls_month": 5000,
      "crm.max_whatsapp_month": -1,
      "maxUsers": -1,
      "maxStorageGb": -1,
      "maxApiCalls": -1,
      "maxWhatsApp": -1,
      "maxSms": -1,
      "maxEmails": -1
    }'::jsonb,
    '{
      "FEATURE_CRM": true,
      "FEATURE_CRM_LEADS": true,
      "FEATURE_CRM_PIPELINE": true,
      "FEATURE_CRM_ACTIVITIES": true,
      "FEATURE_CRM_IMPORT": true,
      "FEATURE_CRM_API": true,
      "FEATURE_CRM_QUOTE": true,
      "FEATURE_CRM_APPROVAL": true,
      "FEATURE_CRM_AUTOMATION": true,
      "FEATURE_CRM_SEQUENCES": true,
      "FEATURE_CRM_CAMPAIGN": true,
      "FEATURE_CRM_AI": true,
      "FEATURE_CRM_WHATSAPP": true,
      "FEATURE_CRM_SMS": true,
      "FEATURE_CRM_EMAIL": true
    }'::jsonb,
    NOW()
)
ON CONFLICT (id) DO NOTHING;

-- Plan composition projection (safe if tables empty for these plans)
INSERT INTO plan_module (plan_id, module_code, enabled, updated_at)
SELECT p.id, m.module_code, TRUE, NOW()
FROM (VALUES
    ('crm-starter', 'CRM_CORE'),
    ('crm-professional', 'CRM_CORE'),
    ('crm-professional', 'CRM_QUOTE'),
    ('crm-professional', 'CRM_AUTOMATION'),
    ('crm-professional', 'CRM_COMMS'),
    ('crm-enterprise', 'CRM_CORE'),
    ('crm-enterprise', 'CRM_QUOTE'),
    ('crm-enterprise', 'CRM_AUTOMATION'),
    ('crm-enterprise', 'CRM_CAMPAIGN'),
    ('crm-enterprise', 'CRM_AI'),
    ('crm-enterprise', 'CRM_COMMS')
) AS m(plan_id, module_code)
JOIN subscription_plan p ON p.id = m.plan_id
ON CONFLICT (plan_id, module_code) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = NOW();

INSERT INTO plan_feature (plan_id, feature_code, enabled, updated_at)
SELECT p.id, f.key, (f.value = 'true'), NOW()
FROM subscription_plan p
CROSS JOIN LATERAL jsonb_each_text(p.feature_flags_json) AS f(key, value)
WHERE p.id IN ('crm-starter', 'crm-professional', 'crm-enterprise')
ON CONFLICT (plan_id, feature_code) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = NOW();

INSERT INTO plan_limit (plan_id, limit_code, limit_value, updated_at)
SELECT p.id, l.key, (l.value)::bigint, NOW()
FROM subscription_plan p
CROSS JOIN LATERAL jsonb_each_text(p.limits_json) AS l(key, value)
WHERE p.id IN ('crm-starter', 'crm-professional', 'crm-enterprise')
  AND l.value ~ '^-?[0-9]+$'
ON CONFLICT (plan_id, limit_code) DO UPDATE SET limit_value = EXCLUDED.limit_value, updated_at = NOW();

-- ---------------------------------------------------------------------------
-- Marketplace add-ons (optional SKUs; prices left for commercial books)
-- ---------------------------------------------------------------------------
INSERT INTO addon_definition (sku, name, description, addon_type, meter_code, credit_amount, active, sort_order)
VALUES
    ('CRM_AI_ADDON', 'CRM AI Add-on', 'AI summaries, drafts, NBA, OCR', 'AI_CREDITS', 'crm.ai_calls_month', 1000, TRUE, 200),
    ('CRM_WA_ADDON', 'CRM WhatsApp Add-on', 'Extra WhatsApp quota for CRM sequences', 'WHATSAPP', 'crm.max_whatsapp_month', 2000, TRUE, 210),
    ('CRM_API_ADDON', 'CRM API Add-on', 'Higher CRM API ingest quota', 'API', 'crm.max_api_calls_month', 50000, TRUE, 220),
    ('CRM_SEAT_ADDON', 'CRM Extra Seats', 'Additional CRM user seats', 'SEAT', 'crm.max_users', 5, TRUE, 230),
    ('CRM_STORAGE_ADDON', 'CRM Extra Storage', 'Additional CRM attachment storage (MB credits)', 'STORAGE', 'crm.max_storage_mb', 5120, TRUE, 240),
    ('CRM_AUTOMATION_ADDON', 'CRM Sales Automation', 'Unlock automation if not on Pro+', 'LICENSE', NULL, 0, TRUE, 250)
ON CONFLICT (sku) DO NOTHING;
