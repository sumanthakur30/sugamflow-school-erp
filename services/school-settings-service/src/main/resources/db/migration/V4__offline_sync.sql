-- Phase 16: offline mode sync outbox (server-side receipt of client queues).

CREATE TABLE offline_sync_batch (
    id               VARCHAR(64)  PRIMARY KEY,
    organization_id  VARCHAR(64)  NOT NULL,
    branch_id        VARCHAR(64),
    client_id        VARCHAR(128),
    status           VARCHAR(32)  NOT NULL,
    item_count       INT          NOT NULL DEFAULT 0,
    success_count    INT          NOT NULL DEFAULT 0,
    failure_count    INT          NOT NULL DEFAULT 0,
    payload_json     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    result_json      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_offline_sync_batch_org_created
    ON offline_sync_batch (organization_id, created_at DESC);

CREATE TABLE offline_sync_item (
    id               VARCHAR(64)  PRIMARY KEY,
    batch_id         VARCHAR(64)  NOT NULL,
    organization_id  VARCHAR(64)  NOT NULL,
    client_item_id   VARCHAR(128),
    entity_type      VARCHAR(64)  NOT NULL,
    status           VARCHAR(32)  NOT NULL,
    request_json     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    response_json    JSONB,
    error_message    TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_offline_sync_item_batch ON offline_sync_item (batch_id);
CREATE INDEX idx_offline_sync_item_org ON offline_sync_item (organization_id, created_at DESC);
