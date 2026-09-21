CREATE TABLE IF NOT EXISTS exam_definition (
    id                    UUID PRIMARY KEY,
    organization_id       VARCHAR(64)  NOT NULL,
    branch_id             VARCHAR(64),
    academic_session_id   VARCHAR(64),
    term_key              VARCHAR(64)  NOT NULL,
    name                  VARCHAR(191) NOT NULL,
    subject_id            UUID         NOT NULL,
    section_id            UUID         NOT NULL,
    max_marks             NUMERIC(10,2) NOT NULL,
    status                VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    created_by            VARCHAR(128),
    published_at          TIMESTAMPTZ,
    locked_at             TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_exam_definition_org
    ON exam_definition (organization_id, section_id, subject_id);

CREATE TABLE IF NOT EXISTS exam_mark (
    id                    UUID PRIMARY KEY,
    organization_id       VARCHAR(64)  NOT NULL,
    exam_definition_id    UUID         NOT NULL,
    student_id            UUID,
    admission_no          VARCHAR(64),
    student_name          VARCHAR(191),
    marks_obtained        NUMERIC(10,2),
    grade                 VARCHAR(32),
    updated_by            VARCHAR(128),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_exam_mark_student
    ON exam_mark (exam_definition_id, student_id)
    WHERE student_id IS NOT NULL;

CREATE UNIQUE INDEX uq_exam_mark_admission
    ON exam_mark (exam_definition_id, admission_no)
    WHERE admission_no IS NOT NULL AND student_id IS NULL;

CREATE INDEX idx_exam_mark_definition ON exam_mark (exam_definition_id);
CREATE INDEX idx_exam_mark_org_student ON exam_mark (organization_id, student_id);
