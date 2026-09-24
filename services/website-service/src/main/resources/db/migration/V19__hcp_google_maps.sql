-- Per-tenant Google Maps fields on theme_json (Contact page + footer).
-- HCP: share link; embed falls back to name+address until an embed src is pasted.

UPDATE website_site
SET theme_json = theme_json
  || jsonb_build_object(
    'mapUrl', 'https://goo.gl/maps/tJZZswki59DcWkkV6?g_st=aw',
    'mapEmbedUrl',
      'https://maps.google.com/maps?q=Holly%20Cross%20Public%20School%2C%20Mirchaibari%2C%20Katihar&hl=en&z=16&output=embed'
  ),
    updated_at = NOW()
WHERE organization_id = 'HCP-01'
  AND branch_id = 'main';
