CREATE TABLE IF NOT EXISTS library_record (
    id              UUID PRIMARY KEY,
    organization_id VARCHAR(64)  NOT NULL,
    branch_id       VARCHAR(64),
    academic_session_id VARCHAR(64),
    form_key        VARCHAR(128) NOT NULL,
    workflow_key    VARCHAR(128) NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    current_step_sequence INT NOT NULL DEFAULT 1,
    current_step_name VARCHAR(128),
    assignee_role   VARCHAR(64),
    answers         JSONB NOT NULL DEFAULT '{}'::jsonb,
    history         JSONB NOT NULL DEFAULT '[]'::jsonb,
    matched_actions JSONB NOT NULL DEFAULT '[]'::jsonb,
    notification_intents JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_by      VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_library_record_org_status
    ON library_record (organization_id, status, updated_at DESC);

CREATE INDEX idx_library_record_org_created
    ON library_record (organization_id, created_at DESC);
