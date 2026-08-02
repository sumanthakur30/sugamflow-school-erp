-- Phase 6: multi-campus website sites (align with ERP org_branch.branch_key).
-- HCP remains a single default campus (branch_id=main).

ALTER TABLE website_site
    ADD COLUMN IF NOT EXISTS branch_id VARCHAR(64) NOT NULL DEFAULT 'main',
    ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE website_site DROP CONSTRAINT IF EXISTS uq_website_site_org;

ALTER TABLE website_site
    ADD CONSTRAINT uq_website_site_org_branch UNIQUE (organization_id, branch_id);

CREATE INDEX IF NOT EXISTS idx_website_site_org_default
    ON website_site (organization_id, is_default);

ALTER TABLE website_domain
    ADD COLUMN IF NOT EXISTS site_id UUID;

UPDATE website_domain d
SET site_id = s.id
FROM website_site s
WHERE d.site_id IS NULL
  AND s.organization_id = d.organization_id
  AND s.branch_id = 'main';

ALTER TABLE website_domain
    ADD CONSTRAINT fk_website_domain_site
        FOREIGN KEY (site_id) REFERENCES website_site (id);

CREATE INDEX IF NOT EXISTS idx_website_domain_site ON website_domain (site_id);

UPDATE website_site
SET branch_id = 'main', is_default = TRUE, updated_at = NOW()
WHERE organization_id = 'HCP-01';
