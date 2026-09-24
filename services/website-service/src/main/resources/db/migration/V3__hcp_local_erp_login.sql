-- Local ERP login deep-link for website SSO bridge (Phase 2).
UPDATE website_site
SET erp_login_url = 'http://localhost:4200/login',
    updated_at = NOW()
WHERE organization_id = 'HCP-01'
  AND (erp_login_url IS NULL OR erp_login_url LIKE '%school.sugamflow.com%');
