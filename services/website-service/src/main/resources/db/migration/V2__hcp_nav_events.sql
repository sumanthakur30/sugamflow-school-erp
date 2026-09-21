-- Add Events to HCP public navigation (Phase 1).
UPDATE website_site
SET navigation_json = '[
  {"label":"Home","path":"/","order":10},
  {"label":"About","path":"/about","order":20},
  {"label":"Admission","path":"/admission","order":30},
  {"label":"Gallery","path":"/gallery","order":40},
  {"label":"News","path":"/news","order":50},
  {"label":"Events","path":"/events","order":55},
  {"label":"Faculty","path":"/faculty","order":58},
  {"label":"Contact","path":"/contact","order":60},
  {"label":"Parent Login","path":"/login","external":true,"order":70}
]'::jsonb,
    updated_at = NOW()
WHERE organization_id = 'HCP-01';
