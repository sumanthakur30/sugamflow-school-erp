-- One-shot HCP homepage polish for production RDS (website_db).
-- Run against school_website_db when you cannot wait for website-service Flyway V13.
-- Safe: HCP-01 / main only.

UPDATE website_site
SET display_name = 'Holly Cross Public School',
    seo_json = jsonb_set(
      jsonb_set(
        COALESCE(seo_json, '{}'::jsonb),
        '{defaultTitle}',
        '"Holly Cross Public School · CBSE Katihar"'
      ),
      '{defaultDescription}',
      '"CBSE school (Nursery–VIII) in Mirchaibari, Katihar. Strong academics, character, and a caring campus community. Admissions open."'
    ),
    homepage_json = '[
      {
        "type": "HERO",
        "enabled": true,
        "order": 10,
        "content": {
          "title": "Where curiosity meets character",
          "subtitle": "Strong academics, values, and joyful learning — CBSE Nursery to Class VIII, Mirchaibari, Katihar.",
          "imageUrl": "/api/cms/public/media/405cfb99-f8c4-4874-bec3-c73c622bad0f?organizationId=HCP-01",
          "ctaLabel": "Admissions"
        }
      },
      {"type": "LATEST_NEWS", "enabled": true, "order": 20, "content": {}},
      {"type": "UPCOMING_EVENTS", "enabled": true, "order": 30, "content": {}},
      {
        "type": "ADMISSION_CTA",
        "enabled": true,
        "order": 40,
        "content": {
          "title": "Admissions open for 2025-26",
          "ctaLabel": "Apply online"
        }
      },
      {"type": "FOOTER", "enabled": true, "order": 100, "content": {}}
    ]'::jsonb,
    theme_json = COALESCE(theme_json, '{}'::jsonb)
      || jsonb_build_object(
           'primaryColor', COALESCE(theme_json->>'primaryColor', '#0B3D91'),
           'secondaryColor', COALESCE(theme_json->>'secondaryColor', '#F5B700'),
           'logoUrl', COALESCE(
             NULLIF(theme_json->>'logoUrl', ''),
             '/api/cms/public/media/c2dc7564-b500-4ebe-9e87-67abbeedaf36?organizationId=HCP-01'
           ),
           'faviconUrl', COALESCE(
             NULLIF(theme_json->>'faviconUrl', ''),
             '/api/cms/public/media/c2dc7564-b500-4ebe-9e87-67abbeedaf36?organizationId=HCP-01'
           )
         ),
    updated_at = NOW()
WHERE organization_id = 'HCP-01'
  AND branch_id = 'main';
