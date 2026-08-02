-- Phase 5: add Blog to HCP public navigation.
UPDATE website_site
SET navigation_json = '[
  {"label":"Home","path":"/","order":10},
  {"label":"About","path":"/about","order":20},
  {"label":"Admission","path":"/admission","order":30},
  {"label":"Blog","path":"/blog","order":35},
  {"label":"Gallery","path":"/gallery","order":40},
  {"label":"News","path":"/news","order":50},
  {"label":"Contact","path":"/contact","order":60},
  {"label":"Parent Login","path":"/login","order":70,"external":true}
]'::jsonb,
    updated_at = NOW()
WHERE organization_id = 'HCP-01';
