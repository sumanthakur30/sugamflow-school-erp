-- Ensure HCP homepage includes news/events section types for Phase 3 builder.
UPDATE website_site
SET homepage_json = '[
  {"type":"HERO","enabled":true,"order":10,"content":{"title":"Welcome to HCP School","subtitle":"Excellence in education"}},
  {"type":"LATEST_NEWS","enabled":true,"order":20,"content":{}},
  {"type":"UPCOMING_EVENTS","enabled":true,"order":30,"content":{}},
  {"type":"ADMISSION_CTA","enabled":true,"order":40,"content":{"title":"Admissions Open","ctaLabel":"Apply Now"}},
  {"type":"FOOTER","enabled":true,"order":100,"content":{}}
]'::jsonb,
    updated_at = NOW()
WHERE organization_id = 'HCP-01';
