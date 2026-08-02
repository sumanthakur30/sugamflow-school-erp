-- Phase 6: alumni directory + template marketplace seed.
CREATE TABLE cms_alumni_profile (
    id                UUID         PRIMARY KEY,
    organization_id   VARCHAR(64)  NOT NULL,
    branch_id         VARCHAR(64),
    slug              VARCHAR(128) NOT NULL,
    full_name         VARCHAR(256) NOT NULL,
    batch_year        INT,
    headline          VARCHAR(512),
    bio_html          TEXT         NOT NULL DEFAULT '',
    photo_url         VARCHAR(1024),
    status            VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    published_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cms_alumni_org_slug UNIQUE (organization_id, slug),
    CONSTRAINT ck_cms_alumni_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE INDEX idx_cms_alumni_org_status ON cms_alumni_profile (organization_id, status, batch_year DESC);

INSERT INTO cms_alumni_profile (
  id, organization_id, branch_id, slug, full_name, batch_year, headline, bio_html, status, published_at, created_at, updated_at
) VALUES (
  'c6000000-0000-4000-8000-000000000001',
  'HCP-01',
  'main',
  'ananya-sharma',
  'Ananya Sharma',
  2018,
  'Software Engineer · Alumni Ambassador',
  '<p>Ananya graduated from HCP School in 2018 and now mentors students in STEM careers.</p>',
  'PUBLISHED',
  NOW(),
  NOW(),
  NOW()
);
