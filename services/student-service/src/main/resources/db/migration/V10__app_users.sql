CREATE TABLE IF NOT EXISTS app_user (
    id                UUID PRIMARY KEY,
    organization_id   VARCHAR(64)  NOT NULL,
    role_code         VARCHAR(32)  NOT NULL,
    subject_ref       VARCHAR(128),
    display_name      VARCHAR(191),
    username          VARCHAR(128),
    installed         BOOLEAN      NOT NULL DEFAULT FALSE,
    last_seen_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_app_user_org
    ON app_user (organization_id, role_code);
