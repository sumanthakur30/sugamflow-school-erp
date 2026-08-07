-- Fix About page hero image: CMS local media IDs were lost when the container
-- was recreated (no persistent volume). Use a stable public campus photo until
-- schools re-upload via Media and insert again.

UPDATE cms_page
SET body_html = regexp_replace(
      body_html,
      '<img src="/api/cms/public/media/[^"]+"',
      '<img src="https://images.unsplash.com/photo-1580582932707-520aed937b7b?w=1200"',
      'g'
    ),
    updated_at = NOW()
WHERE organization_id = 'HCP-01'
  AND slug = 'about'
  AND body_html LIKE '%/api/cms/public/media/%';

UPDATE cms_page
SET body_html = regexp_replace(
      body_html,
      '<img src="/api/cms/public/media/[^"]+"',
      '<img src="https://images.unsplash.com/photo-1580582932707-520aed937b7b?w=1200"',
      'g'
    ),
    updated_at = NOW()
WHERE organization_id = 'demo-school'
  AND slug IN ('about', 'campus-tour')
  AND body_html LIKE '%/api/cms/public/media/%';
