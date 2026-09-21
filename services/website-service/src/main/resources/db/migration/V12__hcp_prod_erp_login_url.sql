-- Production ERP deep-link for HCP website Sign in (overrides local V3 localhost URL).
-- Local overrides: UPDATE website_site SET erp_login_url='http://localhost:4200/login' WHERE organization_id='HCP-01';
UPDATE website_site
SET erp_login_url = 'https://school.sugamflow.com/login',
    updated_at = NOW()
WHERE organization_id = 'HCP-01'
  AND branch_id = 'main';
