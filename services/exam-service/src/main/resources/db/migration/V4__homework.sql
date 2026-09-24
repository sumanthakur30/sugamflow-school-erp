CREATE TABLE IF NOT EXISTS lms_homework (
    id                    UUID PRIMARY KEY,
    organization_id       VARCHAR(64)  NOT NULL,
    branch_id             VARCHAR(64),
    academic_session_id   VARCHAR(64),
    title                 VARCHAR(255) NOT NULL,
    description           TEXT,
    subject_key           VARCHAR(64),
    class_section         VARCHAR(128),
    section_id            UUID,
    due_at                TIMESTAMPTZ,
    status                VARCHAR(32)  NOT NULL DEFAULT 'PUBLISHED',
    created_by            VARCHAR(128),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lms_homework_org
    ON lms_homework (organization_id, status, due_at);

CREATE TABLE IF NOT EXISTS lms_homework_submission (
    id                    UUID PRIMARY KEY,
    organization_id       VARCHAR(64)  NOT NULL,
    homework_id           UUID         NOT NULL REFERENCES lms_homework(id),
    student_id            UUID,
    admission_no          VARCHAR(64),
    student_name          VARCHAR(191),
    body                  TEXT,
    status                VARCHAR(32)  NOT NULL DEFAULT 'SUBMITTED',
    submitted_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    graded_marks          NUMERIC(10,2),
    feedback              TEXT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_lms_submission_homework_student
    ON lms_homework_submission (homework_id, student_id)
    WHERE student_id IS NOT NULL;

CREATE UNIQUE INDEX uq_lms_submission_homework_admission
    ON lms_homework_submission (homework_id, admission_no)
    WHERE admission_no IS NOT NULL;

CREATE INDEX idx_lms_submission_org
    ON lms_homework_submission (organization_id, homework_id);
