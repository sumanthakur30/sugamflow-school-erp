CREATE TABLE IF NOT EXISTS attendance_session (
    id                    UUID PRIMARY KEY,
    organization_id       VARCHAR(64)  NOT NULL,
    branch_id             VARCHAR(64),
    academic_session_id   VARCHAR(64),
    section_id            UUID         NOT NULL,
    period_id             UUID,
    attendance_date       DATE         NOT NULL,
    status                VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    marked_by             VARCHAR(128),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_attendance_session_day
    ON attendance_session (organization_id, section_id, attendance_date)
    WHERE period_id IS NULL;

CREATE UNIQUE INDEX uq_attendance_session_period
    ON attendance_session (organization_id, section_id, attendance_date, period_id)
    WHERE period_id IS NOT NULL;

CREATE INDEX idx_attendance_session_org_date
    ON attendance_session (organization_id, attendance_date DESC);

CREATE TABLE IF NOT EXISTS attendance_mark (
    id                    UUID PRIMARY KEY,
    organization_id       VARCHAR(64)  NOT NULL,
    session_id            UUID         NOT NULL,
    student_id            UUID,
    admission_no          VARCHAR(64),
    student_name          VARCHAR(191),
    status                VARCHAR(32)  NOT NULL,
    remark                VARCHAR(512),
    marked_by             VARCHAR(128),
    marked_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_attendance_mark_student
    ON attendance_mark (session_id, student_id)
    WHERE student_id IS NOT NULL;

CREATE UNIQUE INDEX uq_attendance_mark_admission
    ON attendance_mark (session_id, admission_no)
    WHERE admission_no IS NOT NULL AND student_id IS NULL;

CREATE INDEX idx_attendance_mark_session ON attendance_mark (session_id);
CREATE INDEX idx_attendance_mark_org_student ON attendance_mark (organization_id, student_id);
