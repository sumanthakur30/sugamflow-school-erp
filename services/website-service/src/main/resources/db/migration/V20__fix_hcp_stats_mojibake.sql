-- Fix UTF-8 mojibake in HCP homepage stats / about highlight (Nursery-VIII).
UPDATE website_site
SET homepage_json = replace(
      replace(
        replace(homepage_json::text, 'NurseryÃ¢â‚¬â€œVIII', 'Nursery-VIII'),
        'Nurseryâ€“VIII',
        'Nursery-VIII'
      ),
      'CBSE · Nursery–VIII · Katihar',
      'CBSE | Nursery-VIII | Katihar'
    )::jsonb,
    theme_json = theme_json
      || jsonb_build_object('tagline', 'CBSE Nursery-VIII | Katihar'),
    updated_at = NOW()
WHERE organization_id = 'HCP-01'
  AND branch_id = 'main';

-- Ensure STATISTICS values are clean ASCII hyphens.
UPDATE website_site
SET homepage_json = (
      SELECT jsonb_agg(
        CASE
          WHEN sec->>'type' = 'STATISTICS' THEN
            jsonb_set(
              jsonb_set(
                jsonb_set(
                  jsonb_set(sec, '{content,value1}', '"Nursery-VIII"'),
                  '{content,value2}', '"CBSE"'
                ),
                '{content,label1}', '"Classes"'
              ),
              '{content,label2}', '"Curriculum"'
            )
          WHEN sec->>'type' = 'ABOUT_SCHOOL' THEN
            jsonb_set(sec, '{content,highlight}', '"CBSE | Nursery-VIII | Katihar"')
          ELSE sec
        END
        ORDER BY (sec->>'order')::int
      )
      FROM jsonb_array_elements(homepage_json) AS sec
    ),
    updated_at = NOW()
WHERE organization_id = 'HCP-01'
  AND branch_id = 'main';
