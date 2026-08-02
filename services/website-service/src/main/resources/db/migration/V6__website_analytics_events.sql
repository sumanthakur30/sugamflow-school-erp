-- Phase 4: lightweight public analytics events for school websites.
CREATE TABLE website_analytics_event (
    id                UUID         PRIMARY KEY,
    organization_id   VARCHAR(64)  NOT NULL,
    host              VARCHAR(255),
    event_type        VARCHAR(64)  NOT NULL,
    path              VARCHAR(512),
    referrer          VARCHAR(1024),
    user_agent        VARCHAR(512),
    meta_json         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_website_analytics_org_created
    ON website_analytics_event (organization_id, created_at DESC);

CREATE INDEX idx_website_analytics_org_type_created
    ON website_analytics_event (organization_id, event_type, created_at DESC);
