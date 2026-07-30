-- Phase 14: enterprise controls — SSO / white-label / residency / audit export (additive).

CREATE TABLE enterprise_org_settings (
    organization_id         VARCHAR(64)  PRIMARY KEY,
    sso_enabled             BOOLEAN      NOT NULL DEFAULT FALSE,
    sso_provider            VARCHAR(32)  NULL,
    sso_config_json         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    white_label_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    white_label_json        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    residency_region        VARCHAR(16)  NULL,
    ha_options_json         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    notes                   VARCHAR(512) NULL,
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_enterprise_sso_provider CHECK (
        sso_provider IS NULL OR sso_provider IN ('OIDC', 'SAML', 'NONE')
    ),
    CONSTRAINT ck_enterprise_residency CHECK (
        residency_region IS NULL OR residency_region IN ('IN', 'EU', 'US', 'APAC', 'CUSTOM')
    )
);

CREATE TABLE enterprise_audit_event (
    id                      BIGSERIAL    PRIMARY KEY,
    organization_id         VARCHAR(64)  NOT NULL,
    event_type              VARCHAR(64)  NOT NULL,
    actor                   VARCHAR(128) NULL,
    detail_json             JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_enterprise_audit_org_created
    ON enterprise_audit_event (organization_id, created_at DESC);

CREATE TABLE enterprise_audit_export (
    id                      BIGSERIAL    PRIMARY KEY,
    organization_id         VARCHAR(64)  NOT NULL,
    status                  VARCHAR(16)  NOT NULL DEFAULT 'READY',
    from_at                 TIMESTAMPTZ  NULL,
    to_at                   TIMESTAMPTZ  NULL,
    format                  VARCHAR(16)  NOT NULL DEFAULT 'CSV',
    row_count               INT          NOT NULL DEFAULT 0,
    content_text            TEXT         NULL,
    requested_by            VARCHAR(128) NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_enterprise_export_status CHECK (status IN ('PENDING', 'READY', 'FAILED')),
    CONSTRAINT ck_enterprise_export_format CHECK (format IN ('CSV', 'JSON'))
);

CREATE INDEX idx_enterprise_export_org ON enterprise_audit_export (organization_id, created_at DESC);
