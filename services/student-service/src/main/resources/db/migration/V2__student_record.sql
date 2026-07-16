CREATE TABLE IF NOT EXISTS student_record (
    id                    UUID PRIMARY KEY,
    organization_id       VARCHAR(64)  NOT NULL,
    branch_id             VARCHAR(64),
    academic_session_id   VARCHAR(64),
    form_key              VARCHAR(128) NOT NULL,
    status                VARCHAR(32)  NOT NULL,
    admission_no          VARCHAR(64),
    source_application_id UUID,
    answers               JSONB NOT NULL DEFAULT '{}'::jsonb,
    history               JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_by            VARCHAR(128),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_student_source_application
    ON student_record (organization_id, source_application_id)
    WHERE source_application_id IS NOT NULL;

CREATE INDEX idx_student_org_updated
    ON student_record (organization_id, updated_at DESC);

CREATE INDEX idx_student_org_admission_no
    ON student_record (organization_id, admission_no);
