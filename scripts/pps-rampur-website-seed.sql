-- Idempotent seed for Pratibha Public School, Rampur branch.
-- Run against school_website_db AFTER Flyway V8+ on website-service.
-- Does not touch Holly Cross (HCP-01). Does not copy the Katihar main-campus site.
-- Re-running updates shell copy but does not force a published site back to draft.

INSERT INTO website_site (
    id, organization_id, branch_id, is_default, status, template_code, display_name, erp_login_url,
    theme_json, homepage_json, navigation_json, seo_json, created_at, updated_at
) VALUES (
    'a1000000-0000-4000-8000-000000000201',
    'PPS-01',
    'rampur',
    TRUE,
    'DRAFT',
    'classic-school',
    'Pratibha Public School',
    'https://school.sugamflow.com/login',
    '{
      "skin": "premium",
      "primaryColor": "#1B4D3E",
      "secondaryColor": "#C4A35A",
      "admissionNotice": "Admissions",
      "sessionLabel": "",
      "logoUrl": "/pps/logo.jpeg",
      "faviconUrl": "/pps/logo.jpeg",
      "tagline": "Rampur Branch",
      "contactEmail": "pratibhapublicschoolrampur@gmail.com",
      "contactPhone": "",
      "workingHours": "",
      "address": "At- Rampur, P.O- Hasanganj, P.S- Chandrama Chowk Mufassil, Katihar, Bihar - 854337",
      "addressLine2": "",
      "footerBlurb": "Pratibha Public School, Rampur Branch.",
      "socialFacebook": "",
      "socialInstagram": "",
      "socialYoutube": "",
      "socialWhatsapp": "",
      "mapUrl": "",
      "mapEmbedUrl": "",
      "latitude": "",
      "longitude": "",
      "poweredByLabel": "Powered by SugamFlow",
      "poweredByUrl": "https://sugamflow.com"
    }'::jsonb,
    '[
      {"type":"HERO","enabled":true,"order":10,"content":{"eyebrow":"Rampur Branch","title":"Where curiosity becomes confidence.","subtitle":"A school of the Aryabhatta Educational and Social Enhancement Trust, started in 2011.","ctaLabel":"Apply for Admission","secondaryCtaLabel":"Explore Our School","imageUrl":"/pps/images/facilities/school-campus.jpg"}},
      {"type":"ABOUT_SCHOOL","enabled":true,"order":20,"content":{"title":"About the school","body":"Pratibha Public School runs under the Aryabhatta Educational and Social Enhancement Trust and started in 2011. Its stated vision is equal opportunity and quality education. The Katihar town address, phone numbers, and campus principal are not published as Rampur Branch facts.","ctaLabel":"About the school"}},
      {"type":"PRINCIPAL_MESSAGE","enabled":true,"order":30,"content":{"title":"From the Founder Director","name":"Nikhil Kumar Jha","role":"Founder Director","message":"Education is a complete process that leads to the attainment of the full potential of the child. Our endeavor is to equip our students with skills to face the real world. Our mission is to develop individuals who are independent, confident, and capable of taking decisions.","photoUrl":"/pps/images/management/founder-director.jpg"}},
      {"type":"QUICK_LINKS","enabled":true,"order":40,"content":{"title":"Quick links","links":[{"label":"Admissions","path":"/admission"},{"label":"Gallery","path":"/gallery"},{"label":"Notices","path":"/notices"},{"label":"Contact","path":"/contact"}]}},
      {"type":"FACILITIES","enabled":true,"order":50,"content":{"title":"Facilities","items":[{"title":"Science lab","body":"Photograph from the existing school campus."},{"title":"Library","body":"Photograph from the existing school campus."},{"title":"Computer lab","body":"Photograph from the existing school campus."},{"title":"Parents meeting hall","body":"Photograph from the existing school campus."}]}},
      {"type":"ACADEMIC_PROGRAMS","enabled":true,"order":60,"content":{"title":"Academics","items":[{"title":"Primary","body":"Classes I to V on the existing campus include English, Hindi, Mathematics, Science, Social Studies, Computer Science, Moral Values, General Knowledge, Arts, and Sanskrit."},{"title":"Middle","body":"Classes VI to X on the existing campus add Physics, Chemistry, Biology, History, Geography, and Civics."},{"title":"Senior","body":"Classes XI and XII on the existing campus follow subject combinations by stream."}]}},
      {"type":"WHY_CHOOSE","enabled":true,"order":70,"content":{"title":"Aims","items":[{"title":"Love for learning","body":"The school states that teachers help pupils develop a love for learning."},{"title":"A caring environment","body":"The school states that it provides a secure, happy, and caring environment."},{"title":"Partnership with parents","body":"The school states that it works in partnership with parents and the wider community."}]}},
      {"type":"LATEST_NEWS","enabled":true,"order":80,"content":{}},
      {"type":"GALLERY","enabled":true,"order":90,"content":{"title":"Gallery"}},
      {"type":"ADMISSION_CTA","enabled":true,"order":100,"content":{"title":"Give your child a place to grow","body":"Fee amounts, eligibility, and important dates are published here when the Rampur office confirms them.","ctaLabel":"Start application"}}
    ]'::jsonb,
    '[
      {"label":"Home","path":"/","order":10},
      {"label":"About","path":"/about","order":20,"children":[{"label":"About the school","path":"/about","fragment":"school"},{"label":"Founder Director","path":"/about","fragment":"founder"},{"label":"Vision and mission","path":"/about","fragment":"vision"},{"label":"Infrastructure","path":"/campus","fragment":"labs"}]},
      {"label":"Academics","path":"/academics","order":30,"children":[{"label":"Curriculum","path":"/academics","fragment":"curriculum"},{"label":"Classes","path":"/academics","fragment":"classes"},{"label":"Academic calendar","path":"/notices","fragment":"calendar"},{"label":"Results","path":"/notices","fragment":"results"}]},
      {"label":"Admissions","path":"/admission","order":40,"children":[{"label":"Admission process","path":"/admission","fragment":"process"},{"label":"Apply online","path":"/admission/apply"},{"label":"Enquiry","path":"/contact"}]},
      {"label":"Campus Life","path":"/campus","order":50,"children":[{"label":"Campus","path":"/campus","fragment":"campus"},{"label":"Library and labs","path":"/campus","fragment":"labs"},{"label":"Sports","path":"/student-life","fragment":"sports"}]},
      {"label":"Student Life","path":"/student-life","order":60},
      {"label":"News & Events","path":"/notices","order":70},
      {"label":"Gallery","path":"/gallery","order":80},
      {"label":"Contact","path":"/contact","order":100}
    ]'::jsonb,
    '{
      "defaultTitle": "Pratibha Public School, Rampur Branch",
      "defaultDescription": "Pratibha Public School, Rampur Branch, Hasanganj, Katihar, Bihar.",
      "ogImageUrl": ""
    }'::jsonb,
    NOW(),
    NOW()
)
ON CONFLICT (organization_id, branch_id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    template_code = EXCLUDED.template_code,
    erp_login_url = EXCLUDED.erp_login_url,
    theme_json = EXCLUDED.theme_json,
    homepage_json = EXCLUDED.homepage_json,
    navigation_json = EXCLUDED.navigation_json,
    seo_json = EXCLUDED.seo_json,
    updated_at = NOW();

INSERT INTO website_domain (
    id, organization_id, site_id, host, is_primary, status, ssl_status, created_at, updated_at
) VALUES
    ('b1000000-0000-4000-8000-000000000201', 'PPS-01', 'a1000000-0000-4000-8000-000000000201', 'pratibhapublicschoolrampur.com', TRUE, 'PENDING', 'MANUAL', NOW(), NOW()),
    ('b1000000-0000-4000-8000-000000000202', 'PPS-01', 'a1000000-0000-4000-8000-000000000201', 'www.pratibhapublicschoolrampur.com', FALSE, 'PENDING', 'MANUAL', NOW(), NOW()),
    ('b1000000-0000-4000-8000-000000000203', 'PPS-01', 'a1000000-0000-4000-8000-000000000201', 'pps-rampur.localhost', FALSE, 'ACTIVE', 'MANUAL', NOW(), NOW())
ON CONFLICT (host) DO UPDATE SET
    organization_id = EXCLUDED.organization_id,
    site_id = EXCLUDED.site_id,
    is_primary = EXCLUDED.is_primary,
    updated_at = NOW();
