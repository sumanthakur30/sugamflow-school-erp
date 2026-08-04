-- Richer HCP demo page / news copy for Website Builder + public site testing.
UPDATE cms_page
SET title = 'About Holly Cross Public School',
    summary = 'Our story, vision, and commitment to every learner.',
    body_html = '<p><img src="https://images.unsplash.com/photo-1509062522246-3755977927d7?w=1200" alt="Classroom" style="max-width:100%;height:auto;border-radius:12px;" /></p>
<p>Holly Cross Public School is committed to academic excellence, character development, and holistic growth for every learner.</p>
<p>Our campus blends strong academics with sports, arts, and community values in a safe and nurturing environment.</p>
<h2>Vision</h2>
<p>To raise confident, compassionate learners who excel in academics and life skills.</p>
<h2>Mission</h2>
<ul>
  <li>Strong foundational learning with modern pedagogy</li>
  <li>Safe campus and caring faculty</li>
  <li>Sports, arts, and community values</li>
</ul>',
    seo_title = 'About | Holly Cross Public School',
    seo_description = 'Learn about HCP vision, mission, and campus life.',
    updated_at = NOW()
WHERE organization_id = 'HCP-01' AND slug = 'about';

UPDATE cms_page
SET title = 'Admission',
    summary = 'How to apply for the new academic session.',
    body_html = '<p>Admissions are open for nursery through Grade 10.</p>
<ol>
  <li>Submit the online application from the website</li>
  <li>Campus visit / interaction</li>
  <li>Document verification and interaction</li>
  <li>Fee confirmation</li>
</ol>
<p><a href="/admission/apply">Apply online</a> or contact the school office for guidance.</p>',
    updated_at = NOW()
WHERE organization_id = 'HCP-01' AND slug = 'admission';

UPDATE cms_page
SET title = 'Contact Us',
    summary = 'Reach the school office.',
    body_html = '<p><strong>Holly Cross Public School</strong></p>
<p>Email: info@hcpschool.com<br/>Phone: +91-8789896189<br/>Hours: Mon-Sat, 9:00 AM - 4:00 PM</p>
<p>For admissions or campus visits, please call or email the school office. We look forward to welcoming your family.</p>',
    updated_at = NOW()
WHERE organization_id = 'HCP-01' AND slug = 'contact';

UPDATE cms_page
SET title = 'Faculty',
    summary = 'Meet our academic team.',
    body_html = '<p>Our teachers bring experience, care, and high academic standards to every classroom.</p>
<ul>
  <li>Principal and leadership team</li>
  <li>Subject teachers across grades</li>
  <li>Sports and activity coaches</li>
</ul>',
    updated_at = NOW()
WHERE organization_id = 'HCP-01' AND slug = 'faculty';

INSERT INTO cms_page (
  id, organization_id, slug, title, summary, body_html, status,
  seo_title, seo_description, published_at, created_at, updated_at
)
SELECT
  'c1000000-0000-4000-8000-000000000010',
  'HCP-01',
  'campus-tour',
  'Campus Tour',
  'A walk through our learning spaces.',
  '<p><img src="https://images.unsplash.com/photo-1580582932707-520aed937b7b?w=1200" alt="Campus" style="max-width:100%;height:auto;border-radius:12px;" /></p>
<p>Explore classrooms, labs, library, and playgrounds on our main campus.</p>',
  'PUBLISHED',
  'Campus Tour | Holly Cross Public School',
  'Tour Holly Cross Public School campus.',
  NOW(), NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM cms_page WHERE organization_id = 'HCP-01' AND slug = 'campus-tour'
);

UPDATE cms_news
SET title = 'New academic session begins',
    summary = 'A warm welcome to students and parents for the new session.',
    body_html = '<p>We warmly welcome all students to the new academic session. Orientation schedules will be shared with parents shortly.</p>',
    updated_at = NOW()
WHERE organization_id = 'HCP-01' AND slug = 'session-begins';
