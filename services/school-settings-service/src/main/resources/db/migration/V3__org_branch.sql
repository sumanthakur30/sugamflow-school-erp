-- Phase 14: first-class org branch / campus registry.

CREATE TABLE org_branch (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id  VARCHAR(64)  NOT NULL,
    branch_key       VARCHAR(64)  NOT NULL,
    name             VARCHAR(128) NOT NULL,
    code             VARCHAR(64),
    city             VARCHAR(128),
    address          TEXT,
    status           VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    is_default       BOOLEAN      NOT NULL DEFAULT FALSE,
    payload          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, branch_key)
);

CREATE INDEX idx_org_branch_org ON org_branch (organization_id);
