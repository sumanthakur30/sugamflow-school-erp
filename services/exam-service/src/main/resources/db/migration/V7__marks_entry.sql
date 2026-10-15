ALTER TABLE exam_definition
    ADD COLUMN IF NOT EXISTS passing_marks NUMERIC(10,2),
    ADD COLUMN IF NOT EXISTS exam_date DATE,
    ADD COLUMN IF NOT EXISTS components JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE exam_mark
    ADD COLUMN IF NOT EXISTS entry_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS remarks VARCHAR(500),
    ADD COLUMN IF NOT EXISTS roll_no VARCHAR(64),
    ADD COLUMN IF NOT EXISTS component_marks JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE TABLE IF NOT EXISTS exam_mark_audit (
    id                  UUID PRIMARY KEY,
    organization_id     VARCHAR(64)  NOT NULL,
    exam_definition_id  UUID         NOT NULL,
    student_id          UUID,
    admission_no        VARCHAR(64),
    student_name        VARCHAR(191),
    previous_marks      NUMERIC(10,2),
    new_marks           NUMERIC(10,2),
    previous_status     VARCHAR(32),
    new_status          VARCHAR(32),
    reason              VARCHAR(500),
    changed_by          VARCHAR(128),
    changed_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_exam_mark_audit_definition
    ON exam_mark_audit (exam_definition_id, changed_at DESC);

CREATE INDEX IF NOT EXISTS idx_exam_mark_audit_student
    ON exam_mark_audit (organization_id, student_id, changed_at DESC);

CREATE TABLE IF NOT EXISTS grading_scheme (
    id                    UUID PRIMARY KEY,
    organization_id       VARCHAR(64)  NOT NULL,
    academic_session_id   VARCHAR(64),
    class_id              UUID,
    name                  VARCHAR(191) NOT NULL,
    bands                 JSONB        NOT NULL,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_grading_scheme_org
    ON grading_scheme (organization_id, active);

CREATE TABLE IF NOT EXISTS exam_entry_policy (
    organization_id            VARCHAR(64) PRIMARY KEY,
    absent_as_zero             BOOLEAN     NOT NULL DEFAULT FALSE,
    allow_incomplete_submit    BOOLEAN     NOT NULL DEFAULT FALSE,
    allow_marks_when_excused   BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
