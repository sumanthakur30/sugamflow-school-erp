-- Multi-tenant brand/contact fields on theme_json (additive; any school can set via CMS).
-- Seeds HCP-01 demo values only where keys are still missing.

UPDATE website_site
SET theme_json = theme_json
  || jsonb_build_object(
    'tagline', COALESCE(theme_json->>'tagline', 'CBSE Nursery-VIII | Katihar'),
    'contactEmail', COALESCE(theme_json->>'contactEmail', 'info@hcpschool.com'),
    'contactPhone', COALESCE(theme_json->>'contactPhone', '+91-8789896189'),
    'workingHours', COALESCE(theme_json->>'workingHours', 'Mon-Sat | 9:00 AM - 4:00 PM'),
    'address', COALESCE(theme_json->>'address', 'Mirchaibari, Katihar'),
    'footerBlurb', COALESCE(
      theme_json->>'footerBlurb',
      'Nurturing curious minds with strong academics, character, and a caring campus community.'
    )
  ),
  updated_at = NOW()
WHERE organization_id = 'HCP-01';
