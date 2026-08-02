-- Phase 1: CMS content model (separate from ERP transactional data).

CREATE TABLE cms_page (
    id                UUID         PRIMARY KEY,
    organization_id   VARCHAR(64)  NOT NULL,
    slug              VARCHAR(128) NOT NULL,
    title             VARCHAR(256) NOT NULL,
    summary           VARCHAR(512),
    body_html         TEXT         NOT NULL DEFAULT '',
    status            VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    seo_title         VARCHAR(256),
    seo_description   VARCHAR(512),
    published_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cms_page_org_slug UNIQUE (organization_id, slug),
    CONSTRAINT ck_cms_page_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE INDEX idx_cms_page_org_status ON cms_page (organization_id, status);

CREATE TABLE cms_news (
    id                UUID         PRIMARY KEY,
    organization_id   VARCHAR(64)  NOT NULL,
    slug              VARCHAR(128) NOT NULL,
    title             VARCHAR(256) NOT NULL,
    summary           VARCHAR(1024),
    body_html         TEXT         NOT NULL DEFAULT '',
    cover_image_url   VARCHAR(1024),
    status            VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    published_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cms_news_org_slug UNIQUE (organization_id, slug),
    CONSTRAINT ck_cms_news_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE INDEX idx_cms_news_org_published ON cms_news (organization_id, published_at DESC);

CREATE TABLE cms_event (
    id                UUID         PRIMARY KEY,
    organization_id   VARCHAR(64)  NOT NULL,
    slug              VARCHAR(128) NOT NULL,
    title             VARCHAR(256) NOT NULL,
    summary           VARCHAR(1024),
    body_html         TEXT         NOT NULL DEFAULT '',
    location_text     VARCHAR(256),
    starts_at         TIMESTAMPTZ  NOT NULL,
    ends_at           TIMESTAMPTZ,
    status            VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    published_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cms_event_org_slug UNIQUE (organization_id, slug),
    CONSTRAINT ck_cms_event_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE INDEX idx_cms_event_org_starts ON cms_event (organization_id, starts_at);

CREATE TABLE cms_gallery_item (
    id                UUID         PRIMARY KEY,
    organization_id   VARCHAR(64)  NOT NULL,
    title             VARCHAR(256) NOT NULL,
    caption           VARCHAR(512),
    image_url         VARCHAR(1024) NOT NULL,
    album             VARCHAR(128) NOT NULL DEFAULT 'general',
    sort_order        INT          NOT NULL DEFAULT 0,
    status            VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    published_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_cms_gallery_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE INDEX idx_cms_gallery_org_album ON cms_gallery_item (organization_id, album, sort_order);

CREATE TABLE cms_media_asset (
    id                UUID         PRIMARY KEY,
    organization_id   VARCHAR(64)  NOT NULL,
    file_name         VARCHAR(256) NOT NULL,
    content_type      VARCHAR(128),
    url               VARCHAR(1024) NOT NULL,
    byte_size         BIGINT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cms_media_org ON cms_media_asset (organization_id, created_at DESC);

-- HCP School seed content
INSERT INTO cms_page (id, organization_id, slug, title, summary, body_html, status, seo_title, seo_description, published_at, created_at, updated_at) VALUES
(
  'c1000000-0000-4000-8000-000000000001', 'HCP-01', 'about', 'About HCP School',
  'Our story, vision and commitment to excellence.',
  '<p>HCP School is committed to academic excellence, character development, and holistic growth for every student.</p><p>Our campus blends strong academics with sports, arts, and community values.</p>',
  'PUBLISHED', 'About HCP School', 'Learn about HCP School vision, mission and campus life.', NOW(), NOW(), NOW()
),
(
  'c1000000-0000-4000-8000-000000000002', 'HCP-01', 'admission', 'Admission',
  'Admission process and how to apply.',
  '<p>Admissions are open for the upcoming academic year.</p><ol><li>Submit the online enquiry</li><li>Campus visit / interaction</li><li>Document verification</li><li>Fee confirmation</li></ol><p>For detailed application, use the Apply Now option or contact the school office.</p>',
  'PUBLISHED', 'Admission | HCP School', 'HCP School admission process and requirements.', NOW(), NOW(), NOW()
),
(
  'c1000000-0000-4000-8000-000000000003', 'HCP-01', 'contact', 'Contact Us',
  'Reach the school office.',
  '<p><strong>HCP School</strong></p><p>Email: info@hcpschool.com</p><p>Phone: +91-00000-00000</p><p>Office hours: Mon–Sat, 9:00 AM – 4:00 PM</p>',
  'PUBLISHED', 'Contact | HCP School', 'Contact HCP School office.', NOW(), NOW(), NOW()
),
(
  'c1000000-0000-4000-8000-000000000004', 'HCP-01', 'faculty', 'Faculty',
  'Meet our teachers and academic leaders.',
  '<p>Our faculty bring experience, care, and high academic standards to every classroom.</p>',
  'PUBLISHED', 'Faculty | HCP School', 'HCP School faculty highlights.', NOW(), NOW(), NOW()
);

INSERT INTO cms_news (id, organization_id, slug, title, summary, body_html, status, published_at, created_at, updated_at) VALUES
(
  'c2000000-0000-4000-8000-000000000001', 'HCP-01', 'session-begins', 'New Academic Session Begins',
  'Welcome message for students and parents.',
  '<p>We warmly welcome all students to the new academic session. Orientation schedules will be shared with parents shortly.</p>',
  'PUBLISHED', NOW(), NOW(), NOW()
),
(
  'c2000000-0000-4000-8000-000000000002', 'HCP-01', 'science-fair', 'Inter-House Science Fair Announced',
  'Students can register through class teachers.',
  '<p>The annual Inter-House Science Fair will showcase student projects across grades. Registration closes next Friday.</p>',
  'PUBLISHED', NOW() - INTERVAL '2 days', NOW(), NOW()
);

INSERT INTO cms_event (id, organization_id, slug, title, summary, body_html, location_text, starts_at, ends_at, status, published_at, created_at, updated_at) VALUES
(
  'c3000000-0000-4000-8000-000000000001', 'HCP-01', 'open-day', 'School Open Day',
  'Tour the campus and meet faculty.',
  '<p>Parents and prospective families are invited for a guided campus tour.</p>',
  'Main Campus', NOW() + INTERVAL '14 days', NOW() + INTERVAL '14 days' + INTERVAL '3 hours',
  'PUBLISHED', NOW(), NOW(), NOW()
);

INSERT INTO cms_gallery_item (id, organization_id, title, caption, image_url, album, sort_order, status, published_at, created_at, updated_at) VALUES
(
  'c4000000-0000-4000-8000-000000000001', 'HCP-01', 'Campus Entrance', 'Main gate and reception',
  'https://images.unsplash.com/photo-1580582932707-520aed937b7b?w=1200', 'campus', 10, 'PUBLISHED', NOW(), NOW(), NOW()
),
(
  'c4000000-0000-4000-8000-000000000002', 'HCP-01', 'Smart Classroom', 'Interactive learning spaces',
  'https://images.unsplash.com/photo-1509062522246-3755977927d7?w=1200', 'campus', 20, 'PUBLISHED', NOW(), NOW(), NOW()
),
(
  'c4000000-0000-4000-8000-000000000003', 'HCP-01', 'Sports Day', 'Annual athletics meet',
  'https://images.unsplash.com/photo-1461896836934-ffe607ba6851?w=1200', 'events', 10, 'PUBLISHED', NOW(), NOW(), NOW()
);
