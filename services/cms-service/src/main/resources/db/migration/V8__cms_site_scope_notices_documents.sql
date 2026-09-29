-- Branch/site isolation for public CMS content.
-- Existing rows keep site_id NULL and continue to appear on the organization's default site.

ALTER TABLE cms_page ADD COLUMN IF NOT EXISTS site_id UUID;
ALTER TABLE cms_news ADD COLUMN IF NOT EXISTS site_id UUID;
ALTER TABLE cms_event ADD COLUMN IF NOT EXISTS site_id UUID;
ALTER TABLE cms_gallery_item ADD COLUMN IF NOT EXISTS site_id UUID;
ALTER TABLE cms_media_asset ADD COLUMN IF NOT EXISTS site_id UUID;

ALTER TABLE cms_page DROP CONSTRAINT IF EXISTS uq_cms_page_org_slug;
ALTER TABLE cms_news DROP CONSTRAINT IF EXISTS uq_cms_news_org_slug;
ALTER TABLE cms_event DROP CONSTRAINT IF EXISTS uq_cms_event_org_slug;

CREATE UNIQUE INDEX IF NOT EXISTS uq_cms_page_org_slug_legacy
    ON cms_page (organization_id, slug) WHERE site_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_cms_page_org_site_slug
    ON cms_page (organization_id, site_id, slug) WHERE site_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_cms_news_org_slug_legacy
    ON cms_news (organization_id, slug) WHERE site_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_cms_news_org_site_slug
    ON cms_news (organization_id, site_id, slug) WHERE site_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_cms_event_org_slug_legacy
    ON cms_event (organization_id, slug) WHERE site_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_cms_event_org_site_slug
    ON cms_event (organization_id, site_id, slug) WHERE site_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cms_page_org_site_status ON cms_page (organization_id, site_id, status);
CREATE INDEX IF NOT EXISTS idx_cms_news_org_site_status ON cms_news (organization_id, site_id, status);
CREATE INDEX IF NOT EXISTS idx_cms_event_org_site_status ON cms_event (organization_id, site_id, status);
CREATE INDEX IF NOT EXISTS idx_cms_gallery_org_site_status ON cms_gallery_item (organization_id, site_id, status);
CREATE INDEX IF NOT EXISTS idx_cms_media_org_site ON cms_media_asset (organization_id, site_id, created_at DESC);

ALTER TABLE cms_news ADD COLUMN IF NOT EXISTS category VARCHAR(64) NOT NULL DEFAULT 'NOTICE';
ALTER TABLE cms_news ADD COLUMN IF NOT EXISTS priority INT NOT NULL DEFAULT 0;
ALTER TABLE cms_news ADD COLUMN IF NOT EXISTS audience VARCHAR(32) NOT NULL DEFAULT 'PUBLIC';
ALTER TABLE cms_news ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;

ALTER TABLE cms_event ADD COLUMN IF NOT EXISTS category VARCHAR(64) NOT NULL DEFAULT 'EVENT';
ALTER TABLE cms_event ADD COLUMN IF NOT EXISTS priority INT NOT NULL DEFAULT 0;
ALTER TABLE cms_event ADD COLUMN IF NOT EXISTS audience VARCHAR(32) NOT NULL DEFAULT 'PUBLIC';

CREATE TABLE IF NOT EXISTS cms_document (
    id                UUID         PRIMARY KEY,
    organization_id   VARCHAR(64)  NOT NULL,
    site_id           UUID,
    title             VARCHAR(256) NOT NULL,
    category          VARCHAR(64)  NOT NULL DEFAULT 'GENERAL',
    summary           VARCHAR(512),
    file_url          VARCHAR(1024) NOT NULL,
    file_name         VARCHAR(256),
    status            VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    audience          VARCHAR(32)  NOT NULL DEFAULT 'PUBLIC',
    published_at      TIMESTAMPTZ,
    expires_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_cms_document_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_cms_document_org_site_status
    ON cms_document (organization_id, site_id, status);
