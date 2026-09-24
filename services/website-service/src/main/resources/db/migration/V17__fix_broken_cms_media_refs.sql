-- Replace broken CMS local-media URLs (files lost when cms-service was recreated
-- without a persistent volume). Prefer Design Studio / Unsplash until re-upload.

UPDATE website_site
SET seo_json = jsonb_set(
      COALESCE(seo_json, '{}'::jsonb),
      '{ogImageUrl}',
      '"https://images.unsplash.com/photo-1580582932707-520aed937b7b?w=1600"'
    ),
    theme_json = theme_json
      || jsonb_build_object(
           'logoUrl', '/api/config/design-studio/assets/b4cc02b5-7e57-4900-9f1d-99a98671e5ed/content',
           'faviconUrl', '/api/config/design-studio/assets/b4cc02b5-7e57-4900-9f1d-99a98671e5ed/content'
         ),
    homepage_json = (
      SELECT jsonb_agg(
        CASE
          WHEN sec->>'type' = 'HERO' THEN
            jsonb_set(
              sec,
              '{content,imageUrl}',
              '"https://images.unsplash.com/photo-1580582932707-520aed937b7b?w=1600"'
            )
          ELSE sec
        END
        ORDER BY (sec->>'order')::int
      )
      FROM jsonb_array_elements(homepage_json) AS sec
    ),
    updated_at = NOW()
WHERE organization_id = 'HCP-01'
  AND branch_id = 'main';
