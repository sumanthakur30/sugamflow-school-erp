CREATE TABLE IF NOT EXISTS classroom_item (
    id                    UUID PRIMARY KEY,
    organization_id       VARCHAR(64)  NOT NULL,
    branch_id             VARCHAR(64),
    academic_session_id   VARCHAR(64),
    kind                  VARCHAR(32)  NOT NULL,
    status                VARCHAR(32)  NOT NULL,
    title                 VARCHAR(256),
    note                  TEXT,
    payload               JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_by            VARCHAR(128),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_classroom_item_org_kind
    ON classroom_item (organization_id, kind, created_at DESC);

CREATE TABLE IF NOT EXISTS classroom_response (
    id                    UUID PRIMARY KEY,
    organization_id       VARCHAR(64)  NOT NULL,
    item_id               UUID         NOT NULL REFERENCES classroom_item(id),
    admission_no          VARCHAR(64),
    student_name          VARCHAR(191),
    score                 NUMERIC(10,2),
    status                VARCHAR(32)  NOT NULL DEFAULT 'SUBMITTED',
    payload               JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_classroom_response_item
    ON classroom_response (organization_id, item_id);
