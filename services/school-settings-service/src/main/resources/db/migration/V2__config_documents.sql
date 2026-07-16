-- Phase 2: persistent configuration documents (JSONB payloads).

CREATE TABLE design_theme (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id  VARCHAR(64)  NOT NULL,
    branch_id        VARCHAR(64),
    version          VARCHAR(32)  NOT NULL DEFAULT '1',
    status           VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    payload          JSONB        NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE NULLS NOT DISTINCT (organization_id, branch_id)
);

CREATE TABLE module_settings (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id  VARCHAR(64)  NOT NULL,
    branch_id        VARCHAR(64),
    academic_session_id VARCHAR(64),
    module_key       VARCHAR(64)  NOT NULL,
    payload          JSONB        NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE NULLS NOT DISTINCT (organization_id, branch_id, module_key)
);

CREATE TABLE menu_config (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id  VARCHAR(64)  NOT NULL,
    payload          JSONB        NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id)
);

CREATE TABLE localization_settings (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id  VARCHAR(64)  NOT NULL,
    branch_id        VARCHAR(64),
    payload          JSONB        NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE NULLS NOT DISTINCT (organization_id, branch_id)
);

CREATE TABLE ui_screen_config (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id  VARCHAR(64)  NOT NULL,
    screen_key       VARCHAR(128) NOT NULL,
    payload          JSONB        NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, screen_key)
);

CREATE TABLE ai_settings (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id  VARCHAR(64)  NOT NULL,
    payload          JSONB        NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id)
);

CREATE TABLE role_dashboard_config (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id  VARCHAR(64)  NOT NULL,
    role_code        VARCHAR(64)  NOT NULL,
    payload          JSONB        NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, role_code)
);
