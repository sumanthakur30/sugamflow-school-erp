-- Phase 0: School Website Platform foundation (domain registry + site shell).
-- Content CMS lives in cms-service (Phase 1); do not store ERP transactional data here.

CREATE TABLE website_site (
    id                UUID         PRIMARY KEY,
    organization_id   VARCHAR(64)  NOT NULL,
    status            VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    template_code     VARCHAR(64),
    display_name      VARCHAR(256) NOT NULL,
    erp_login_url     VARCHAR(512),
    theme_json        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    homepage_json     JSONB        NOT NULL DEFAULT '[]'::jsonb,
    navigation_json   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_website_site_org UNIQUE (organization_id),
    CONSTRAINT ck_website_site_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'SUSPENDED'))
);

CREATE INDEX idx_website_site_status ON website_site (status);

CREATE TABLE website_domain (
    id                UUID         PRIMARY KEY,
    organization_id   VARCHAR(64)  NOT NULL,
    host              VARCHAR(255) NOT NULL,
    is_primary        BOOLEAN      NOT NULL DEFAULT FALSE,
    status            VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    ssl_status        VARCHAR(32)  NOT NULL DEFAULT 'MANUAL',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_website_domain_host UNIQUE (host),
    CONSTRAINT ck_website_domain_status CHECK (status IN ('PENDING', 'ACTIVE', 'DISABLED')),
    CONSTRAINT ck_website_domain_ssl CHECK (ssl_status IN ('MANUAL', 'PENDING', 'ACTIVE', 'FAILED'))
);

CREATE INDEX idx_website_domain_org ON website_domain (organization_id);
CREATE INDEX idx_website_domain_status ON website_domain (status);

-- HCP School — first tenant (reference architecture).
INSERT INTO website_site (
    id, organization_id, status, template_code, display_name, erp_login_url,
    theme_json, homepage_json, navigation_json, created_at, updated_at
) VALUES (
    'a1000000-0000-4000-8000-000000000001',
    'HCP-01',
    'PUBLISHED',
    'classic-school',
    'HCP School',
    'https://school.sugamflow.com/login',
    '{"primaryColor":"#0B3D91","secondaryColor":"#F5B700","logoUrl":null,"faviconUrl":null}'::jsonb,
    '[
      {"type":"HERO","enabled":true,"order":10,"content":{"title":"Welcome to HCP School","subtitle":"Excellence in education"}},
      {"type":"ANNOUNCEMENTS","enabled":true,"order":20,"content":{}},
      {"type":"QUICK_LINKS","enabled":true,"order":30,"content":{}},
      {"type":"ADMISSION_CTA","enabled":true,"order":40,"content":{"title":"Admissions Open","ctaLabel":"Apply Now"}},
      {"type":"FOOTER","enabled":true,"order":100,"content":{}}
    ]'::jsonb,
    '[
      {"label":"Home","path":"/","order":10},
      {"label":"About","path":"/about","order":20},
      {"label":"Admission","path":"/admission","order":30},
      {"label":"Gallery","path":"/gallery","order":40},
      {"label":"News","path":"/news","order":50},
      {"label":"Contact","path":"/contact","order":60},
      {"label":"Parent Login","path":"/login","order":70,"external":false}
    ]'::jsonb,
    NOW(),
    NOW()
);

INSERT INTO website_domain (
    id, organization_id, host, is_primary, status, ssl_status, created_at, updated_at
) VALUES
    ('b1000000-0000-4000-8000-000000000001', 'HCP-01', 'hcpschool.com', TRUE, 'ACTIVE', 'MANUAL', NOW(), NOW()),
    ('b1000000-0000-4000-8000-000000000002', 'HCP-01', 'www.hcpschool.com', FALSE, 'ACTIVE', 'MANUAL', NOW(), NOW()),
    ('b1000000-0000-4000-8000-000000000003', 'HCP-01', 'hcp.localhost', FALSE, 'ACTIVE', 'MANUAL', NOW(), NOW());
