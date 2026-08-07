-- Demo-school CMS seed + light HCP principal-ready gallery caption polish.
-- Schools edit News / Gallery / About from CMS; no Body HTML required for those flows.

INSERT INTO cms_page (
  id, organization_id, slug, title, summary, body_html, status,
  seo_title, seo_description, published_at, created_at, updated_at
)
SELECT
  'c1000000-0000-4000-8000-0000000000d1',
  'demo-school',
  'about',
  'About Demo School',
  'Our story, vision, and commitment to every learner.',
  '<p>SugamFlow Demo School showcases the standard school website. Replace this About copy with your school story, vision, and mission.</p><h2>Vision</h2><p>Confident, compassionate learners who excel in academics and life skills.</p><h2>Mission</h2><ul><li>Strong foundational learning</li><li>Safe campus and caring faculty</li><li>Sports, arts, and community values</li></ul>',
  'PUBLISHED',
  'About | SugamFlow Demo School',
  'Learn about the demo school vision and campus life.',
  NOW(), NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM cms_page WHERE organization_id = 'demo-school' AND slug = 'about'
);

INSERT INTO cms_page (
  id, organization_id, slug, title, summary, body_html, status,
  seo_title, seo_description, published_at, created_at, updated_at
)
SELECT
  'c1000000-0000-4000-8000-0000000000d2',
  'demo-school',
  'admission',
  'Admission',
  'How to apply for the new academic session.',
  '<p>Admissions are open for nursery through Grade 12.</p><ol><li>Submit the online application</li><li>Campus visit / interaction</li><li>Document verification</li><li>Fee confirmation</li></ol><p><a href="/admission/apply">Apply online</a> or contact the school office.</p>',
  'PUBLISHED',
  'Admission | SugamFlow Demo School',
  'Demo school admission process.',
  NOW(), NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM cms_page WHERE organization_id = 'demo-school' AND slug = 'admission'
);

INSERT INTO cms_page (
  id, organization_id, slug, title, summary, body_html, status,
  seo_title, seo_description, published_at, created_at, updated_at
)
SELECT
  'c1000000-0000-4000-8000-0000000000d3',
  'demo-school',
  'contact',
  'Contact Us',
  'Reach the school office.',
  '<p>Contact details on this page are also driven by Website → Theme (email, phone, address).</p>',
  'PUBLISHED',
  'Contact | SugamFlow Demo School',
  'Contact Demo School office.',
  NOW(), NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM cms_page WHERE organization_id = 'demo-school' AND slug = 'contact'
);

INSERT INTO cms_news (
  id, organization_id, slug, title, summary, body_html, cover_image_url, status,
  published_at, created_at, updated_at
)
SELECT
  'c2000000-0000-4000-8000-0000000000d1',
  'demo-school',
  'welcome-parents',
  'Welcome to the new academic session',
  'A warm note for students and parents as the year begins.',
  '<p>We welcome every family to Demo School. Orientation schedules and class circulars will appear here — replace this story with your real announcement.</p>',
  'https://images.unsplash.com/photo-1509062522246-3755977927d7?w=1200',
  'PUBLISHED',
  NOW(), NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM cms_news WHERE organization_id = 'demo-school' AND slug = 'welcome-parents'
);

INSERT INTO cms_news (
  id, organization_id, slug, title, summary, body_html, cover_image_url, status,
  published_at, created_at, updated_at
)
SELECT
  'c2000000-0000-4000-8000-0000000000d2',
  'demo-school',
  'sports-day',
  'Annual sports day highlights',
  'Team spirit, medals, and joyful competition across grades.',
  '<p>Sports day brought energy across the campus. Upload your own photos in Gallery and link them from news stories like this one.</p>',
  'https://images.unsplash.com/photo-1461896836934-ffe607ba6851?w=1200',
  'PUBLISHED',
  NOW(), NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM cms_news WHERE organization_id = 'demo-school' AND slug = 'sports-day'
);

INSERT INTO cms_news (
  id, organization_id, slug, title, summary, body_html, cover_image_url, status,
  published_at, created_at, updated_at
)
SELECT
  'c2000000-0000-4000-8000-0000000000d3',
  'demo-school',
  'admissions-open',
  'Admissions open for the new session',
  'Apply online — our office team will guide you through the next steps.',
  '<p>Admissions are open. Families can apply from the website and visit campus for interaction.</p>',
  'https://images.unsplash.com/photo-1580582932707-520aed937b7b?w=1200',
  'PUBLISHED',
  NOW(), NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM cms_news WHERE organization_id = 'demo-school' AND slug = 'admissions-open'
);

INSERT INTO cms_gallery_item (
  id, organization_id, title, caption, image_url, album, sort_order, status,
  published_at, created_at, updated_at
)
SELECT
  'c3000000-0000-4000-8000-0000000000d1',
  'demo-school',
  'Main campus',
  'Front view of the learning spaces.',
  'https://images.unsplash.com/photo-1580582932707-520aed937b7b?w=1200',
  'campus',
  10,
  'PUBLISHED',
  NOW(), NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM cms_gallery_item
  WHERE organization_id = 'demo-school' AND title = 'Main campus' AND album = 'campus'
);

INSERT INTO cms_gallery_item (
  id, organization_id, title, caption, image_url, album, sort_order, status,
  published_at, created_at, updated_at
)
SELECT
  'c3000000-0000-4000-8000-0000000000d2',
  'demo-school',
  'Classroom learning',
  'Focused, joyful classrooms.',
  'https://images.unsplash.com/photo-1509062522246-3755977927d7?w=1200',
  'campus',
  20,
  'PUBLISHED',
  NOW(), NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM cms_gallery_item
  WHERE organization_id = 'demo-school' AND title = 'Classroom learning' AND album = 'campus'
);

INSERT INTO cms_gallery_item (
  id, organization_id, title, caption, image_url, album, sort_order, status,
  published_at, created_at, updated_at
)
SELECT
  'c3000000-0000-4000-8000-0000000000d3',
  'demo-school',
  'Library',
  'Quiet spaces for reading and research.',
  'https://images.unsplash.com/photo-1521587760476-6c12a4b040da?w=1200',
  'campus',
  30,
  'PUBLISHED',
  NOW(), NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM cms_gallery_item
  WHERE organization_id = 'demo-school' AND title = 'Library' AND album = 'campus'
);

INSERT INTO cms_gallery_item (
  id, organization_id, title, caption, image_url, album, sort_order, status,
  published_at, created_at, updated_at
)
SELECT
  'c3000000-0000-4000-8000-0000000000d4',
  'demo-school',
  'Sports ground',
  'Games and fitness for every age.',
  'https://images.unsplash.com/photo-1461896836934-ffe607ba6851?w=1200',
  'sports',
  40,
  'PUBLISHED',
  NOW(), NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM cms_gallery_item
  WHERE organization_id = 'demo-school' AND title = 'Sports ground' AND album = 'sports'
);

-- Ensure HCP has at least one extra published news item for the homepage strip.
INSERT INTO cms_news (
  id, organization_id, slug, title, summary, body_html, cover_image_url, status,
  published_at, created_at, updated_at
)
SELECT
  'c2000000-0000-4000-8000-000000000011',
  'HCP-01',
  'admissions-open-2025',
  'Admissions open for 2025-26',
  'Apply online — our office will guide your family through the process.',
  '<p>Admissions are open for Nursery through Class VIII. Apply from the website or call the school office.</p>',
  NULL,
  'PUBLISHED',
  NOW(), NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM cms_news WHERE organization_id = 'HCP-01' AND slug = 'admissions-open-2025'
);
