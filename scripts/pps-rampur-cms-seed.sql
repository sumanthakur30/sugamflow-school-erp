-- Idempotent CMS pages for Pratibha Public School, Rampur.
-- Run against the cms-service database AFTER Flyway V8 (site_id column).
-- Published pages use only verified Rampur facts.
-- Organization notes stay DRAFT and are not shown on the public site.

INSERT INTO cms_page (
    id, organization_id, site_id, slug, title, summary, body_html, status,
    seo_title, seo_description, published_at, created_at, updated_at
) VALUES
(
    'c1000000-0000-4000-8000-000000000211',
    'PPS-01',
    'a1000000-0000-4000-8000-000000000201',
    'about',
    'About the school',
    'Pratibha Public School, Rampur Branch.',
    '<p>Pratibha Public School, Rampur Branch, is at Rampur, Post Hasanganj, Police Station Chandrama Chowk Mufassil, Katihar, Bihar 854337.</p><p>Email: pratibhapublicschoolrampur@gmail.com</p><p>Vision, mission, leadership, and history for this branch are added by the school in Website CMS. They are not copied from another campus.</p>',
    'PUBLISHED',
    'About | Pratibha Public School, Rampur',
    'About Pratibha Public School, Rampur Branch, Katihar.',
    NOW(), NOW(), NOW()
),
(
    'c1000000-0000-4000-8000-000000000212',
    'PPS-01',
    'a1000000-0000-4000-8000-000000000201',
    'academics',
    'Academics',
    'Classes and curriculum for this branch.',
    '<p>Classes, subjects, and the academic calendar for Rampur Branch are published here after the school configures them. Nothing is assumed from another campus.</p>',
    'PUBLISHED',
    'Academics | Pratibha Public School, Rampur',
    'Academic information for Pratibha Public School, Rampur Branch.',
    NOW(), NOW(), NOW()
),
(
    'c1000000-0000-4000-8000-000000000213',
    'PPS-01',
    'a1000000-0000-4000-8000-000000000201',
    'campus',
    'Campus and facilities',
    'Campus details for Rampur Branch.',
    '<p>Campus photos and facility details will appear when the school uploads them in Website CMS. Gallery images from another campus are not shown here.</p>',
    'PUBLISHED',
    'Campus | Pratibha Public School, Rampur',
    'Campus and facilities at Pratibha Public School, Rampur Branch.',
    NOW(), NOW(), NOW()
),
(
    'c1000000-0000-4000-8000-000000000214',
    'PPS-01',
    'a1000000-0000-4000-8000-000000000201',
    'student-life',
    'Student life',
    'Activities at Rampur Branch.',
    '<p>Activities, clubs, and student programmes for Rampur Branch can be added by the school. This page starts without borrowed content.</p>',
    'PUBLISHED',
    'Student life | Pratibha Public School, Rampur',
    'Student life at Pratibha Public School, Rampur Branch.',
    NOW(), NOW(), NOW()
),
(
    'c1000000-0000-4000-8000-000000000215',
    'PPS-01',
    'a1000000-0000-4000-8000-000000000201',
    'organization-notes',
    'Organization notes (not for the public site)',
    'Editor reference. Do not publish until verified for Rampur.',
    '<p>Reference only, from the existing organization website. Do not publish this page, and do not treat it as Rampur Branch fact.</p><ul><li>Trust name on the existing site: Aryabhatta Educational and Social Enhancement Trust</li><li>Existing campus started in 2011 (Katihar town campus, not Rampur)</li><li>Founder director named on that site: Nikhil Kumar Jha</li></ul><p>Do not copy that campus address, phone numbers, email, affiliation number, principal, administrator, or gallery onto Rampur.</p>',
    'DRAFT',
    'Organization notes',
    'Internal reference. Not for publication.',
    NULL, NOW(), NOW()
)
ON CONFLICT (organization_id, site_id, slug) WHERE site_id IS NOT NULL DO UPDATE SET
    title = EXCLUDED.title,
    summary = EXCLUDED.summary,
    body_html = EXCLUDED.body_html,
    seo_title = EXCLUDED.seo_title,
    seo_description = EXCLUDED.seo_description,
    updated_at = NOW();
