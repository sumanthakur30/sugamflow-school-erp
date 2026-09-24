-- HCP demo website polish: theme, SEO hero image, homepage copy (safe re-apply for HCP-01 only).
UPDATE website_site
SET display_name = 'Holly Cross Public School',
    template_code = 'classic-school',
    status = 'PUBLISHED',
    theme_json = '{
      "primaryColor":"#0B3D91",
      "secondaryColor":"#F5B700",
      "logoUrl":null,
      "faviconUrl":null
    }'::jsonb,
    seo_json = '{
      "defaultTitle":"Holly Cross Public School",
      "defaultDescription":"A trusted CBSE school focused on excellence, values, and joyful learning. Admissions open for the new session.",
      "ogImageUrl":"https://images.unsplash.com/photo-1580582932707-520aed937b7b?w=1600"
    }'::jsonb,
    homepage_json = '[
      {
        "type":"HERO",
        "enabled":true,
        "order":10,
        "content":{
          "title":"Welcome to Holly Cross Public School",
          "subtitle":"Nurturing curious minds with strong academics, character, and campus life.",
          "imageUrl":"https://images.unsplash.com/photo-1580582932707-520aed937b7b?w=1600"
        }
      },
      {"type":"LATEST_NEWS","enabled":true,"order":20,"content":{}},
      {"type":"UPCOMING_EVENTS","enabled":true,"order":30,"content":{}},
      {
        "type":"ADMISSION_CTA",
        "enabled":true,
        "order":40,
        "content":{"title":"Admissions open for 2025-26","ctaLabel":"Apply online"}
      },
      {"type":"FOOTER","enabled":true,"order":100,"content":{}}
    ]'::jsonb,
    navigation_json = '[
      {"label":"Home","path":"/","order":10},
      {"label":"About","path":"/about","order":20},
      {"label":"Admission","path":"/admission","order":30},
      {"label":"Blog","path":"/blog","order":35},
      {"label":"Alumni","path":"/alumni","order":38},
      {"label":"Gallery","path":"/gallery","order":40},
      {"label":"News","path":"/news","order":50},
      {"label":"Contact","path":"/contact","order":60},
      {"label":"Parent Login","path":"/login","order":70,"external":true}
    ]'::jsonb,
    updated_at = NOW()
WHERE organization_id = 'HCP-01' AND branch_id = 'main';
