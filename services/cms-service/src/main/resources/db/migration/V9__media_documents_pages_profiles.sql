-- Additive fields for stable media, documents, page sections, gallery video, and staff cards.

ALTER TABLE cms_media_asset ADD COLUMN IF NOT EXISTS title VARCHAR(256);
ALTER TABLE cms_media_asset ADD COLUMN IF NOT EXISTS description VARCHAR(1024);
ALTER TABLE cms_media_asset ADD COLUMN IF NOT EXISTS alt_text VARCHAR(512);
ALTER TABLE cms_media_asset ADD COLUMN IF NOT EXISTS category VARCHAR(64);

ALTER TABLE cms_document ADD COLUMN IF NOT EXISTS academic_year VARCHAR(32);
ALTER TABLE cms_document ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;
ALTER TABLE cms_document ADD COLUMN IF NOT EXISTS content_type VARCHAR(128);
ALTER TABLE cms_document ADD COLUMN IF NOT EXISTS byte_size BIGINT;
ALTER TABLE cms_document ADD COLUMN IF NOT EXISTS media_asset_id UUID;

ALTER TABLE cms_page ADD COLUMN IF NOT EXISTS sections_json TEXT;
ALTER TABLE cms_gallery_item ADD COLUMN IF NOT EXISTS video_url VARCHAR(1024);

CREATE TABLE IF NOT EXISTS cms_profile (
    id                UUID         PRIMARY KEY,
    organization_id   VARCHAR(64)  NOT NULL,
    site_id           UUID,
    full_name         VARCHAR(256) NOT NULL,
    role              VARCHAR(128),
    photo_url         VARCHAR(1024),
    media_asset_id    UUID,
    sort_order        INT          NOT NULL DEFAULT 0,
    status            VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    published_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_cms_profile_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_cms_profile_org_site ON cms_profile (organization_id, site_id, sort_order);
