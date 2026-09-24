UPDATE website_site
SET theme_json = jsonb_set(
        jsonb_set(theme_json, '{logoUrl}', '"/pps/logo.jpeg"'),
        '{faviconUrl}',
        '"/pps/logo.jpeg"'
    ),
    updated_at = NOW()
WHERE organization_id = 'PPS-01'
  AND branch_id = 'rampur';
