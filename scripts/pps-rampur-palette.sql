UPDATE website_site
SET theme_json = theme_json || '{"primaryColor":"#1B4D3E","secondaryColor":"#C4A35A"}'::jsonb,
    updated_at = NOW()
WHERE organization_id = 'PPS-01' AND branch_id = 'rampur';
