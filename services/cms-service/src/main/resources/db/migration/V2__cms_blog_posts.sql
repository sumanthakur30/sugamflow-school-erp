-- Phase 5: School website blog posts (FEATURE_WEBSITE_BLOG).
CREATE TABLE cms_blog_post (
    id                UUID         PRIMARY KEY,
    organization_id   VARCHAR(64)  NOT NULL,
    slug              VARCHAR(128) NOT NULL,
    title             VARCHAR(256) NOT NULL,
    summary           VARCHAR(1024),
    body_html         TEXT         NOT NULL,
    cover_image_url   VARCHAR(1024),
    status            VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    published_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cms_blog_org_slug UNIQUE (organization_id, slug),
    CONSTRAINT ck_cms_blog_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE INDEX idx_cms_blog_org_status ON cms_blog_post (organization_id, status, published_at DESC);

INSERT INTO cms_blog_post (
  id, organization_id, slug, title, summary, body_html, cover_image_url, status, published_at, created_at, updated_at
) VALUES (
  'c5000000-0000-4000-8000-000000000001',
  'HCP-01',
  'welcome-to-our-blog',
  'Welcome to the HCP School Blog',
  'Stories from campus life, academics, and community.',
  '<p>We are excited to share updates from HCP School — academics, sports, arts, and parent community highlights.</p>',
  NULL,
  'PUBLISHED',
  NOW(),
  NOW(),
  NOW()
);
