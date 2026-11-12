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
      "accentColor": "#F4EFE6",
      "fontSans": "Manrope",
      "copyrightText": "",
      "poweredByLabel": "Powered by SugamFlow",
      "poweredByUrl": "https://sugamflow.com"
    }'::jsonb,
    '[
      {"type":"HERO","enabled":true,"order":10,"content":{"eyebrow":"Rampur Branch","title":"Where curiosity becomes confidence.","subtitle":"A school of the Aryabhatta Educational and Social Enhancement Trust, started in 2011.","ctaLabel":"Apply for Admission","ctaPath":"/admission/apply","secondaryCtaLabel":"Explore Our School","secondaryCtaPath":"/about","imageUrl":"/pps/images/facilities/school-campus.jpg","imageAlt":"Pratibha Public School campus","images":[{"imageUrl":"/pps/images/facilities/school-campus.jpg","alt":"School campus"},{"imageUrl":"/pps/images/facilities/school-campus-2.jpg","alt":"School building"},{"imageUrl":"/pps/images/facilities/library.jpg","alt":"Library"}]}},
      {"type":"ABOUT_SCHOOL","enabled":true,"order":20,"content":{"eyebrow":"Welcome","title":"About the school","body":"Pratibha Public School runs under the Aryabhatta Educational and Social Enhancement Trust and started in 2011. Its stated vision is equal opportunity and quality education. The Katihar town address, phone numbers, and campus principal are not published as Rampur Branch facts.","ctaLabel":"About the school","ctaPath":"/about","imageUrl":"/pps/images/facilities/corridor.jpg","imageAlt":"School corridor"}},
      {"type":"DIRECTOR_MESSAGE","enabled":true,"order":30,"content":{"title":"From the Founder Director","name":"Nikhil Kumar Jha","role":"Founder Director","message":"Education is a complete process that leads to the attainment of the full potential of the child. Our endeavor is to equip our students with skills to face the real world. Our mission is to develop individuals who are independent, confident, and capable of taking decisions.","photoUrl":"/pps/images/management/founder-director.jpg"}},
      {"type":"PRINCIPAL_MESSAGE","enabled":false,"order":35,"content":{"title":"From the Principal","name":"","role":"","message":"","photoUrl":"","placeholderBody":"The principal message is published here when the Rampur office adds it."}},
      {"type":"WHY_CHOOSE","enabled":true,"order":40,"content":{"eyebrow":"The school","title":"What Pratibha stands for","items":[{"title":"Love for learning","body":"The school states that teachers help pupils develop a love for learning."},{"title":"A caring environment","body":"The school states that it provides a secure, happy, and caring environment."},{"title":"Partnership with parents","body":"The school states that it works in partnership with parents and the wider community."}]}},
      {"type":"QUICK_LINKS","enabled":true,"order":50,"content":{"eyebrow":"Look around","title":"Explore Pratibha","links":[{"label":"Academics","path":"/academics","cta":"Learn","imageUrl":"/pps/images/facilities/computer-lab.jpg"},{"label":"Campus","path":"/campus","cta":"Discover","imageUrl":"/pps/images/facilities/school-campus.jpg"},{"label":"Gallery","path":"/gallery","cta":"See photos","imageUrl":"/pps/images/facilities/library.jpg"},{"label":"Contact","path":"/contact","cta":"Visit","imageUrl":"/pps/images/facilities/parents-meeting-hall.jpg"}]}},
      {"type":"FACILITIES","enabled":true,"order":60,"content":{"eyebrow":"Campus","title":"A campus designed for discovery","body":"Library, science lab, computer lab, and activity spaces are shown here from photographs on the existing Pratibha website. They are a starting gallery for Rampur and can be replaced when Rampur photographs are ready.","imageUrl":"/pps/images/facilities/science-lab.jpg","imageAlt":"Science laboratory","ctaLabel":"See campus notes","ctaPath":"/campus","items":[{"title":"Science lab"},{"title":"Library"},{"title":"Computer lab"},{"title":"Parents meeting hall"}]}},
      {"type":"ACADEMIC_PROGRAMS","enabled":true,"order":70,"content":{"eyebrow":"Learning","title":"Academics","items":[{"title":"Primary","body":"Classes I to V on the existing campus include English, Hindi, Mathematics, Science, Social Studies, Computer Science, Moral Values, General Knowledge, Arts, and Sanskrit.","imageUrl":"/pps/images/facilities/computer-lab.jpg"},{"title":"Middle","body":"Classes VI to X on the existing campus add Physics, Chemistry, Biology, History, Geography, and Civics."},{"title":"Senior","body":"Classes XI and XII on the existing campus follow subject combinations by stream."}]}},
      {"type":"GALLERY","enabled":true,"order":80,"content":{"eyebrow":"Photographs","title":"Life at school","placeholderTitle":"Gallery is empty","placeholderBody":"Photographs appear here when the school publishes them."}},
      {"type":"LATEST_NEWS","enabled":true,"order":90,"content":{"eyebrow":"News","title":"What’s happening at Pratibha","placeholderTitle":"No announcement yet","placeholderBody":"Notices and news appear here when the school publishes them."}},
      {"type":"UPCOMING_EVENTS","enabled":true,"order":95,"content":{"eyebrow":"Calendar","title":"Events","placeholderTitle":"No upcoming event","placeholderBody":"Events appear here when the school publishes them."}},
      {"type":"DOCUMENTS","enabled":true,"order":100,"content":{"eyebrow":"Downloads","title":"Important documents","placeholderTitle":"No documents yet","placeholderBody":"Mandatory disclosure, calendars, and forms appear here when the school uploads them."}},
      {"type":"TESTIMONIALS","enabled":true,"order":110,"content":{"eyebrow":"Community","title":"What our school community says","placeholderTitle":"Add a parent or student testimonial","placeholderBody":"Names and quotes are shown only after the school publishes them.","items":[]}},
      {"type":"ADMISSION_CTA","enabled":true,"order":120,"content":{"title":"Give your child a place to grow","body":"Fee amounts, eligibility, and important dates are published here when the Rampur office confirms them.","ctaLabel":"Start application","ctaPath":"/admission/apply","secondaryCtaLabel":"Talk to admissions","secondaryCtaPath":"/contact","steps":[{"label":"Enquire"},{"label":"Visit"},{"label":"Apply"}]}},
      {"type":"CONTACT","enabled":true,"order":130,"content":{"eyebrow":"Visit","title":"Contact the school"}}
    ]'::jsonb,
    '[
      {"label":"Home","path":"/","order":10},
      {"label":"About","path":"/about","order":20,"children":[{"label":"About the school","path":"/about","fragment":"school"},{"label":"Founder Director","path":"/about","fragment":"founder"},{"label":"Vision and mission","path":"/about","fragment":"vision"},{"label":"Infrastructure","path":"/campus","fragment":"labs"},{"label":"Mandatory Public Disclosure","path":"/mandatory-public-disclosure"}]},
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
