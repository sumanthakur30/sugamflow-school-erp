-- Industry-standard classic-school homepage template + demo-school site shell.
-- Also refreshes HCP-01 main campus to the same section layout (content stays school-specific).

UPDATE website_template
SET name = 'Classic School',
    description = 'Industry-standard school site: hero, about, stats, principal message, news, gallery, and admissions CTA. Schools only replace content.',
    theme_json = '{
      "primaryColor":"#0B3D91",
      "secondaryColor":"#F5B700",
      "tagline":"Excellence in education",
      "footerBlurb":"Strong academics, character, and a caring campus community."
    }'::jsonb,
    homepage_json = '[
      {
        "type":"HERO",
        "enabled":true,
        "order":10,
        "content":{
          "title":"Where curiosity meets character",
          "subtitle":"Strong academics, values, and joyful learning for every child.",
          "ctaLabel":"Admissions",
          "imageUrl":"https://images.unsplash.com/photo-1580582932707-520aed937b7b?w=1600"
        }
      },
      {
        "type":"ABOUT_SCHOOL",
        "enabled":true,
        "order":20,
        "content":{
          "title":"A place where learning feels personal",
          "body":"We combine rigorous academics with sports, arts, and values in a safe campus environment.",
          "highlight":"Academics · Values · Campus life",
          "ctaLabel":"Learn more about us"
        }
      },
      {
        "type":"STATISTICS",
        "enabled":true,
        "order":30,
        "content":{
          "title":"At a glance",
          "items":[
            {"label":"Students","value":"500+"},
            {"label":"Teachers","value":"40+"},
            {"label":"Years","value":"25+"},
            {"label":"Clubs","value":"20+"}
          ],
          "value1":"500+","label1":"Students",
          "value2":"40+","label2":"Teachers",
          "value3":"25+","label3":"Years",
          "value4":"20+","label4":"Clubs"
        }
      },
      {
        "type":"PRINCIPAL_MESSAGE",
        "enabled":true,
        "order":40,
        "content":{
          "title":"From the Principal",
          "name":"School Principal",
          "role":"Principal",
          "message":"Every child deserves a safe campus, caring teachers, and the confidence to grow."
        }
      },
      {
        "type":"QUICK_LINKS",
        "enabled":true,
        "order":50,
        "content":{
          "title":"Quick links",
          "links":[
            {"label":"About","path":"/about"},
            {"label":"Admissions","path":"/admission"},
            {"label":"Gallery","path":"/gallery"},
            {"label":"News","path":"/news"},
            {"label":"Contact","path":"/contact"}
          ]
        }
      },
      {"type":"LATEST_NEWS","enabled":true,"order":60,"content":{}},
      {"type":"GALLERY","enabled":true,"order":70,"content":{"title":"Campus life"}},
      {"type":"UPCOMING_EVENTS","enabled":true,"order":80,"content":{}},
      {
        "type":"ADMISSION_CTA",
        "enabled":true,
        "order":90,
        "content":{"title":"Admissions are open","ctaLabel":"Apply online"}
      },
      {"type":"FOOTER","enabled":true,"order":100,"content":{}}
    ]'::jsonb,
    navigation_json = '[
      {"label":"Home","path":"/","order":10},
      {"label":"About","path":"/about","order":20},
      {"label":"Admission","path":"/admission","order":30},
      {"label":"Gallery","path":"/gallery","order":40},
      {"label":"News","path":"/news","order":50},
      {"label":"Contact","path":"/contact","order":60}
    ]'::jsonb,
    seo_json = '{
      "defaultTitle":"School Website",
      "defaultDescription":"Official school website — academics, admissions, news, and campus life."
    }'::jsonb
WHERE code = 'classic-school';

-- Apply the standard layout to HCP (keep HCP-specific copy + media URLs).
UPDATE website_site
SET template_code = 'classic-school',
    homepage_json = '[
      {
        "type":"HERO",
        "enabled":true,
        "order":10,
        "content":{
          "title":"Where curiosity meets character",
          "subtitle":"Strong academics, values, and joyful learning — CBSE Nursery to Class VIII, Mirchaibari, Katihar.",
          "imageUrl":"/api/cms/public/media/405cfb99-f8c4-4874-bec3-c73c622bad0f?organizationId=HCP-01",
          "ctaLabel":"Admissions"
        }
      },
      {
        "type":"ABOUT_SCHOOL",
        "enabled":true,
        "order":20,
        "content":{
          "title":"Holly Cross Public School",
          "body":"A trusted CBSE school focused on academic excellence, character development, and a caring campus community for every learner.",
          "highlight":"CBSE · Nursery–VIII · Katihar",
          "ctaLabel":"Our story"
        }
      },
      {
        "type":"STATISTICS",
        "enabled":true,
        "order":30,
        "content":{
          "title":"At a glance",
          "value1":"Nursery–VIII","label1":"Classes",
          "value2":"CBSE","label2":"Curriculum",
          "value3":"Safe","label3":"Campus",
          "value4":"Open","label4":"Admissions"
        }
      },
      {
        "type":"PRINCIPAL_MESSAGE",
        "enabled":true,
        "order":40,
        "content":{
          "title":"From the Principal",
          "name":"Principal",
          "role":"Holly Cross Public School",
          "message":"We welcome every family seeking strong academics, values, and joyful learning for their child."
        }
      },
      {
        "type":"QUICK_LINKS",
        "enabled":true,
        "order":50,
        "content":{
          "title":"Quick links",
          "links":[
            {"label":"About","path":"/about"},
            {"label":"Admissions","path":"/admission"},
            {"label":"Gallery","path":"/gallery"},
            {"label":"News","path":"/news"},
            {"label":"Contact","path":"/contact"}
          ]
        }
      },
      {"type":"LATEST_NEWS","enabled":true,"order":60,"content":{}},
      {"type":"GALLERY","enabled":true,"order":70,"content":{"title":"Campus life"}},
      {"type":"UPCOMING_EVENTS","enabled":true,"order":80,"content":{}},
      {
        "type":"ADMISSION_CTA",
        "enabled":true,
        "order":90,
        "content":{"title":"Admissions open for 2025-26","ctaLabel":"Apply online"}
      },
      {"type":"FOOTER","enabled":true,"order":100,"content":{}}
    ]'::jsonb,
    navigation_json = '[
      {"label":"Home","path":"/","order":10},
      {"label":"About","path":"/about","order":20},
      {"label":"Admission","path":"/admission","order":30},
      {"label":"Gallery","path":"/gallery","order":40},
      {"label":"News","path":"/news","order":50},
      {"label":"Contact","path":"/contact","order":60}
    ]'::jsonb,
    updated_at = NOW()
WHERE organization_id = 'HCP-01'
  AND branch_id = 'main';

-- Demo school public site (reference tenant for sales + QA).
INSERT INTO website_site (
    id, organization_id, branch_id, is_default, status, template_code, display_name, erp_login_url,
    theme_json, homepage_json, navigation_json, seo_json, created_at, updated_at
)
SELECT
    'a1000000-0000-4000-8000-0000000000d1',
    'demo-school',
    'main',
    TRUE,
    'PUBLISHED',
    'classic-school',
    'SugamFlow Demo School',
    'https://school.sugamflow.com/login',
    '{
      "primaryColor":"#0B3D91",
      "secondaryColor":"#F5B700",
      "tagline":"CBSE · Nursery–XII · Demo City",
      "contactEmail":"demo@sugamflow.com",
      "contactPhone":"+91-90000-00000",
      "workingHours":"Mon–Sat · 9:00 AM – 4:00 PM",
      "address":"Demo Campus, Main Road",
      "addressLine2":"Demo City, India",
      "footerBlurb":"A complete school website demo — replace logo, about, news, and gallery with your content."
    }'::jsonb,
    (SELECT homepage_json FROM website_template WHERE code = 'classic-school'),
    (SELECT navigation_json FROM website_template WHERE code = 'classic-school'),
    '{
      "defaultTitle":"SugamFlow Demo School",
      "defaultDescription":"Industry-standard school website demo powered by SugamFlow.",
      "ogImageUrl":"https://images.unsplash.com/photo-1580582932707-520aed937b7b?w=1600"
    }'::jsonb,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM website_site WHERE organization_id = 'demo-school' AND branch_id = 'main'
);

UPDATE website_site
SET display_name = 'SugamFlow Demo School',
    template_code = 'classic-school',
    status = 'PUBLISHED',
    theme_json = COALESCE(theme_json, '{}'::jsonb) || '{
      "primaryColor":"#0B3D91",
      "secondaryColor":"#F5B700",
      "tagline":"CBSE · Nursery–XII · Demo City",
      "contactEmail":"demo@sugamflow.com",
      "contactPhone":"+91-90000-00000",
      "workingHours":"Mon–Sat · 9:00 AM – 4:00 PM",
      "address":"Demo Campus, Main Road",
      "addressLine2":"Demo City, India",
      "footerBlurb":"A complete school website demo — replace logo, about, news, and gallery with your content."
    }'::jsonb,
    homepage_json = (SELECT homepage_json FROM website_template WHERE code = 'classic-school'),
    navigation_json = (SELECT navigation_json FROM website_template WHERE code = 'classic-school'),
    seo_json = COALESCE(seo_json, '{}'::jsonb) || '{
      "defaultTitle":"SugamFlow Demo School",
      "defaultDescription":"Industry-standard school website demo powered by SugamFlow.",
      "ogImageUrl":"https://images.unsplash.com/photo-1580582932707-520aed937b7b?w=1600"
    }'::jsonb,
    updated_at = NOW()
WHERE organization_id = 'demo-school'
  AND branch_id = 'main';

INSERT INTO website_domain (
    id, organization_id, host, is_primary, status, ssl_status, site_id, created_at, updated_at
)
SELECT
    'b1000000-0000-4000-8000-0000000000d1',
    'demo-school',
    'demo.localhost',
    TRUE,
    'ACTIVE',
    'MANUAL',
    s.id,
    NOW(),
    NOW()
FROM website_site s
WHERE s.organization_id = 'demo-school'
  AND s.branch_id = 'main'
  AND NOT EXISTS (
      SELECT 1 FROM website_domain WHERE host = 'demo.localhost'
  );

INSERT INTO website_domain (
    id, organization_id, host, is_primary, status, ssl_status, site_id, created_at, updated_at
)
SELECT
    'b1000000-0000-4000-8000-0000000000d2',
    'demo-school',
    'demo.sugamflow.com',
    FALSE,
    'ACTIVE',
    'MANUAL',
    s.id,
    NOW(),
    NOW()
FROM website_site s
WHERE s.organization_id = 'demo-school'
  AND s.branch_id = 'main'
  AND NOT EXISTS (
      SELECT 1 FROM website_domain WHERE host = 'demo.sugamflow.com'
  );
