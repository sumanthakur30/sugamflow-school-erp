CREATE TABLE IF NOT EXISTS school_privacy_notice (
    id           UUID PRIMARY KEY,
    notice_code  VARCHAR(80)  NOT NULL,
    version      VARCHAR(40)  NOT NULL,
    title        VARCHAR(255) NOT NULL,
    body         TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_school_privacy_notice_version UNIQUE (notice_code, version)
);

CREATE TABLE IF NOT EXISTS school_privacy_consent (
    id              UUID PRIMARY KEY,
    organization_id VARCHAR(64)  NOT NULL,
    subject_ref     VARCHAR(128) NOT NULL,
    purpose_code    VARCHAR(80)  NOT NULL,
    legal_ground    VARCHAR(20)  NOT NULL,
    notice_code     VARCHAR(80)  NOT NULL,
    notice_version  VARCHAR(40)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    channel         VARCHAR(40)  NOT NULL,
    granted_at      TIMESTAMPTZ,
    withdrawn_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_school_privacy_consent UNIQUE (organization_id, subject_ref, purpose_code)
);

CREATE TABLE IF NOT EXISTS school_privacy_request (
    id               UUID PRIMARY KEY,
    request_number   VARCHAR(40)  NOT NULL,
    organization_id  VARCHAR(64)  NOT NULL,
    subject_ref      VARCHAR(128) NOT NULL,
    data_category    VARCHAR(20)  NOT NULL,
    subject_label    VARCHAR(80),
    request_type     VARCHAR(40)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    detail           TEXT,
    action_taken     VARCHAR(40)  NOT NULL,
    response_note    TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_school_privacy_request_org
    ON school_privacy_request (organization_id, created_at DESC);
