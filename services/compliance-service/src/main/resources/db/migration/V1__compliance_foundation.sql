-- P0 foundation for CBSE Compliance & Regulatory Management.
-- Source-of-truth masters stay in student/staff; this DB owns board metadata + school profile + campaigns.

CREATE TABLE board_definition (
    code            VARCHAR(40) PRIMARY KEY,
    name            VARCHAR(120) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO board_definition (code, name) VALUES
    ('CBSE', 'Central Board of Secondary Education'),
    ('ICSE', 'ICSE / CISCE'),
    ('STATE', 'State Board (generic)')
ON CONFLICT (code) DO NOTHING;

CREATE TABLE school_compliance_profile (
    id                      BIGSERIAL PRIMARY KEY,
    organization_id         VARCHAR(100) NOT NULL,
    board_code              VARCHAR(40) NOT NULL DEFAULT 'CBSE'
        REFERENCES board_definition (code),
    school_name             VARCHAR(255),
    affiliation_number      VARCHAR(80),
    school_code             VARCHAR(80),
    udise_plus              VARCHAR(80),
    dise_code               VARCHAR(80),
    address_line            TEXT,
    city                    VARCHAR(120),
    state_code              VARCHAR(40),
    pincode                 VARCHAR(20),
    principal_name          VARCHAR(160),
    principal_mobile        VARCHAR(30),
    principal_email         VARCHAR(160),
    school_phone            VARCHAR(30),
    school_email            VARCHAR(160),
    bank_account_name       VARCHAR(160),
    bank_account_number     VARCHAR(64),
    bank_ifsc               VARCHAR(20),
    trust_society_name      VARCHAR(255),
    recognition_details     TEXT,
    infrastructure_notes    TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_compliance_profile_org UNIQUE (organization_id)
);

CREATE INDEX idx_compliance_profile_board ON school_compliance_profile (board_code);

CREATE TABLE submission_campaign (
    id                      BIGSERIAL PRIMARY KEY,
    organization_id         VARCHAR(100) NOT NULL,
    academic_session_id     VARCHAR(100),
    board_code              VARCHAR(40) NOT NULL DEFAULT 'CBSE'
        REFERENCES board_definition (code),
    title                   VARCHAR(255) NOT NULL,
    status                  VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    due_at                  TIMESTAMPTZ,
    compliance_score        NUMERIC(5, 2),
    blocker_count           INT NOT NULL DEFAULT 0,
    warn_count              INT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_submission_campaign_org ON submission_campaign (organization_id, status);
