-- Assert CRM catalog SKUs / flags (run against subscription DB after V17).
SELECT code FROM business_type WHERE code = 'CRM';
SELECT code FROM subscription_plan WHERE code IN ('crm-starter', 'crm-professional', 'crm-enterprise');
SELECT code FROM feature_definition WHERE code IN ('FEATURE_CRM', 'FEATURE_CRM_QUOTE', 'FEATURE_CRM_LEADS');
