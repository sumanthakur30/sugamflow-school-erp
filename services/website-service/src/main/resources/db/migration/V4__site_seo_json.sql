-- Phase 3: site-level SEO defaults for public website.
ALTER TABLE website_site
    ADD COLUMN IF NOT EXISTS seo_json JSONB NOT NULL DEFAULT '{}'::jsonb;

UPDATE website_site
SET seo_json = jsonb_build_object(
        'defaultTitle', display_name,
        'defaultDescription', 'Official school website for ' || display_name,
        'ogImageUrl', null
    ),
    updated_at = NOW()
WHERE organization_id = 'HCP-01';
