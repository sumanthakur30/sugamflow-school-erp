-- Add Faculty to public navigation (CMS page /faculty already exists for HCP + demo).

UPDATE website_template
SET navigation_json = '[
  {"label":"Home","path":"/","order":10},
  {"label":"About","path":"/about","order":20},
  {"label":"Faculty","path":"/faculty","order":25},
  {"label":"Admission","path":"/admission","order":30},
  {"label":"Gallery","path":"/gallery","order":40},
  {"label":"News","path":"/news","order":50},
  {"label":"Contact","path":"/contact","order":60}
]'::jsonb
WHERE code = 'classic-school';

UPDATE website_site
SET navigation_json = '[
  {"label":"Home","path":"/","order":10},
  {"label":"About","path":"/about","order":20},
  {"label":"Faculty","path":"/faculty","order":25},
  {"label":"Admission","path":"/admission","order":30},
  {"label":"Gallery","path":"/gallery","order":40},
  {"label":"News","path":"/news","order":50},
  {"label":"Contact","path":"/contact","order":60}
]'::jsonb,
    updated_at = NOW()
WHERE organization_id IN ('HCP-01', 'demo-school')
  AND branch_id = 'main';

-- Keep homepage quick links in sync when the section exists.
UPDATE website_site
SET homepage_json = (
      SELECT jsonb_agg(
        CASE
          WHEN sec->>'type' = 'QUICK_LINKS' THEN
            jsonb_set(
              sec,
              '{content,links}',
              '[
                {"label":"About","path":"/about"},
                {"label":"Faculty","path":"/faculty"},
                {"label":"Admissions","path":"/admission"},
                {"label":"Gallery","path":"/gallery"},
                {"label":"News","path":"/news"},
                {"label":"Contact","path":"/contact"}
              ]'::jsonb
            )
          ELSE sec
        END
        ORDER BY (sec->>'order')::int
      )
      FROM jsonb_array_elements(homepage_json) AS sec
    ),
    updated_at = NOW()
WHERE organization_id IN ('HCP-01', 'demo-school')
  AND branch_id = 'main'
  AND homepage_json @> '[{"type":"QUICK_LINKS"}]'::jsonb;
