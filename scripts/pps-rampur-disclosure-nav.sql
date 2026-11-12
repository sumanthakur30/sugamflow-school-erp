-- Add the disclosure link under About for Pratibha Rampur only.
-- Does not replace homepage content and does not touch Holly Cross.
UPDATE website_site AS site
SET navigation_json = patched.nav,
    updated_at = NOW()
FROM (
  SELECT jsonb_agg(
           CASE
             WHEN elem->>'path' = '/about'
              AND NOT EXISTS (
                SELECT 1
                FROM jsonb_array_elements(COALESCE(elem->'children', '[]'::jsonb)) AS child
                WHERE child->>'path' = '/mandatory-public-disclosure'
              )
             THEN jsonb_set(
               elem,
               '{children}',
               COALESCE(elem->'children', '[]'::jsonb)
                 || jsonb_build_array(
                      jsonb_build_object(
                        'label', 'Mandatory Public Disclosure',
                        'path', '/mandatory-public-disclosure'
                      )
                    )
             )
             ELSE elem
           END
           ORDER BY ord
         ) AS nav
  FROM website_site AS source,
       jsonb_array_elements(source.navigation_json) WITH ORDINALITY AS item(elem, ord)
  WHERE source.organization_id = 'PPS-01'
    AND source.branch_id = 'rampur'
) AS patched
WHERE site.organization_id = 'PPS-01'
  AND site.branch_id = 'rampur';
