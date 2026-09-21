-- P4: CMS mandatory disclosure binding + last published snapshot.

CREATE TABLE disclosure_binding (
    id                      BIGSERIAL PRIMARY KEY,
    organization_id         VARCHAR(100) NOT NULL,
    section_key             VARCHAR(80) NOT NULL DEFAULT 'MAIN',
    cms_slug                VARCHAR(160) NOT NULL DEFAULT 'mandatory-public-disclosure',
    cms_page_id             VARCHAR(64),
    title                   VARCHAR(255) NOT NULL DEFAULT 'Mandatory Public Disclosure',
    last_published_at       TIMESTAMPTZ,
    last_publish_status     VARCHAR(40),
    last_publish_message    TEXT,
    public_url_hint         VARCHAR(500),
    published_html          TEXT,
    published_snapshot      JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_disclosure_binding_org_section UNIQUE (organization_id, section_key)
);

CREATE INDEX idx_disclosure_binding_org ON disclosure_binding (organization_id);
CREATE INDEX idx_disclosure_binding_slug ON disclosure_binding (organization_id, cms_slug);
