-- School-wide copy for Rampur CMS pages.
-- Homepage JSON is in pps-rampur-website-seed.sql (school_website_db).

UPDATE cms_page SET
  title = 'About the school',
  summary = 'Pratibha Public School, started in 2011 under the Aryabhatta Educational and Social Enhancement Trust.',
  body_html = $html$
<p id="school">Pratibha Public School started functioning in 2011. It runs under the Aryabhatta Educational and Social Enhancement Trust. The school states that it was created to provide equal opportunity and quality education for the community in North Bihar.</p>
<p>The existing Katihar town campus describes classes from I to XII. That class range is not published here as a Rampur Branch fact.</p>
<h2 id="vision">Vision</h2>
<p>The school aims to provide an all-round education as the first stage in a personally fulfilling and socially useful life, and to prepare students for university studies. It says young people need skills and attitudes to be successful life-long learners, not only a body of knowledge.</p>
<h2 id="mission">Mission</h2>
<p>To enable students to achieve their dreams and reach their potential. To provide academic excellence in a humane and caring environment. To promote ethical practices and social responsibility. To establish a community whose strength lies in integrity, loyalty, honesty, and dedication.</p>
<h2>Aims</h2>
<ul>
<li>Help pupils develop a love for learning.</li>
<li>Provide a broad, balanced, and creative curriculum.</li>
<li>Keep high standards in academic, physical, and social behaviour.</li>
<li>Provide a secure, happy, and caring environment.</li>
<li>Help pupils value diversity and respect core Indian values.</li>
<li>Encourage healthy lifestyle choices.</li>
<li>Work in partnership with parents and the wider community.</li>
<li>Allow children to have a voice.</li>
</ul>
<h2 id="founder">Founder Director</h2>
<p>The school site describes the founder, Mr. N. K. Jha, as a visionary and guide. Nikhil Kumar Jha is named as Founder Director. His message says education should lead to the full potential of the child, and that the school aims to develop independent, confident people.</p>
<p>Mr. S. C. Jha is the principal of the existing Katihar town campus, and Prafulla Chandra Jha is named there as administrator. They are not published here as the Rampur Branch principal or administrator.</p>
<p><strong>Rampur Branch</strong><br>At- Rampur, P.O- Hasanganj, P.S- Chandrama Chowk Mufassil, Katihar, Bihar - 854337<br>pratibhapublicschoolrampur@gmail.com</p>
$html$,
  status = 'PUBLISHED',
  seo_title = 'About | Pratibha Public School, Rampur',
  seo_description = 'About Pratibha Public School and the Rampur Branch.',
  updated_at = NOW()
WHERE organization_id = 'PPS-01' AND site_id = 'a1000000-0000-4000-8000-000000000201' AND slug = 'about';

UPDATE cms_page SET
  title = 'Academics',
  summary = 'Subjects published by Pratibha Public School.',
  body_html = $html$
<h2 id="curriculum">Curriculum</h2>
<p>The existing campus publishes this subject pattern. It is school curriculum information. Class timings, the school session, and the affiliation number are not repeated here as Rampur Branch facts.</p>
<h2>Early years</h2>
<p>Pre-school play covers English alphabets, numbers, fruits, vegetables, animals, and colours. Nursery adds Hindi alphabets, EVS, pictures, word formation, counting, and arts. KG covers word formation, Hindi, EVS, Maths, spelling, story, and arts.</p>
<h2 id="classes">Classes</h2>
<h3>Primary, classes I to V</h3>
<p>English literature and grammar, spoken English, Hindi literature and grammar, Maths, Science, Social Studies, Computer Science, Moral Values, General Knowledge, Arts, and Sanskrit.</p>
<h2>Middle, classes VI to X</h2>
<p>The primary subjects continue, with Physics, Chemistry, Biology, History, Geography, and Civics.</p>
<h2>Classes XI and XII</h2>
<p>Subject combinations follow the stream chosen by the student.</p>
<p>The existing campus states that it is affiliated with CBSE, New Delhi, and also refers to the Bihar School Examination Board. The affiliation number is not published on this Rampur page until the branch confirms it.</p>
$html$,
  updated_at = NOW()
WHERE organization_id = 'PPS-01' AND site_id = 'a1000000-0000-4000-8000-000000000201' AND slug = 'academics';

UPDATE cms_page SET
  title = 'Campus and facilities',
  summary = 'Facilities described by the existing Pratibha Public School campus.',
  body_html = $html$
<h2 id="campus">Campus</h2>
<p>These descriptions and photographs come from the existing Pratibha Public School campus. They are the starting images for this website. They are not a confirmed inventory of the Rampur Branch. Replace them in Website CMS when Rampur photographs are available.</p>
<p>The existing campus describes a Wi-Fi campus, playing fields, air-conditioned classrooms and conference halls, libraries, laboratories, and residential accommodation. It also describes 35 classrooms, science, maths and language laboratories, a sound-proof auditorium, an AV room, a sports field, indoor games, a discovery room, and an art gallery.</p>
<h2 id="labs">Library and labs</h2>
<ul>
<li>Science lab, computer lab, and a discovery room for primary classes.</li>
<li>Library.</li>
<li>Boarding, described as open from class II, with separate girls and boys hostels.</li>
<li>Transport, described as a fleet of buses.</li>
<li>Parents meeting hall, corridor, prayer, indoor games, and annual function.</li>
</ul>
$html$,
  updated_at = NOW()
WHERE organization_id = 'PPS-01' AND site_id = 'a1000000-0000-4000-8000-000000000201' AND slug = 'campus';

UPDATE cms_page SET
  title = 'Student life',
  summary = 'Activities shown on the existing Pratibha Public School website.',
  body_html = $html$
<h2 id="sports">Sports and activities</h2>
<p>The existing school website shows prayer, indoor games, sports, and the annual function. Those photographs are in the gallery as the initial pictures for this site.</p>
<p>Rampur Branch activities, clubs, and dates can be added in Website CMS. Nothing beyond the existing site is listed here.</p>
$html$,
  updated_at = NOW()
WHERE organization_id = 'PPS-01' AND site_id = 'a1000000-0000-4000-8000-000000000201' AND slug = 'student-life';

INSERT INTO cms_page (
  id, organization_id, site_id, slug, title, summary, body_html, status,
  seo_title, seo_description, published_at, created_at, updated_at
) VALUES (
  'c1000000-0000-4000-8000-000000000216',
  'PPS-01',
  'a1000000-0000-4000-8000-000000000201',
  'admission',
  'Admissions',
  'Admission steps published by Pratibha Public School.',
  $html$
<p>The existing campus publishes this admission procedure. Fee amounts and admission dates are not listed, because the existing site does not publish them.</p>
<ol id="process">
<li>Obtain the school prospectus from the school office.</li>
<li>Fill the registration form in the prospectus. A parent or local guardian may fill it.</li>
<li>The site states that the fee structure differs by class and is paid when the admission form is submitted. Amounts are not published.</li>
<li>Documents named on the site: the child's birth certificate, six photographs of the child, two photographs each of the father and mother, and the record of previous education.</li>
<li>After registration, the student sits an admission test, then completes the admission form.</li>
</ol>
<p>For Rampur Branch, write to pratibhapublicschoolrampur@gmail.com or use Apply online. A phone number for this branch has not been provided.</p>
$html$,
  'PUBLISHED',
  'Admissions | Pratibha Public School, Rampur',
  'Admission steps for Pratibha Public School, Rampur Branch.',
  NOW(), NOW(), NOW()
) ON CONFLICT (organization_id, site_id, slug) WHERE site_id IS NOT NULL DO UPDATE SET
  title = EXCLUDED.title,
  summary = EXCLUDED.summary,
  body_html = EXCLUDED.body_html,
  status = EXCLUDED.status,
  seo_title = EXCLUDED.seo_title,
  seo_description = EXCLUDED.seo_description,
  updated_at = NOW();
