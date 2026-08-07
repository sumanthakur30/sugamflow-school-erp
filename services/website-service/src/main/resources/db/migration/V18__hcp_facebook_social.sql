-- HCP public Facebook page (footer + Theme socialFacebook).
UPDATE website_site
SET theme_json = theme_json
  || jsonb_build_object(
    'socialFacebook', 'https://www.facebook.com/prafullachandrajha'
  ),
    updated_at = NOW()
WHERE organization_id = 'HCP-01'
  AND branch_id = 'main';
