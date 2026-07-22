CREATE TABLE IF NOT EXISTS academic_class (
    id                    UUID PRIMARY KEY,
    organization_id       VARCHAR(64)  NOT NULL,
    branch_id             VARCHAR(64),
    academic_session_id   VARCHAR(64),
    name                  VARCHAR(128) NOT NULL,
    code                  VARCHAR(64),
    sequence_no           INTEGER      NOT NULL DEFAULT 0,
    status                VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    attributes            JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_academic_class_org
    ON academic_class (organization_id, sequence_no, name);

CREATE TABLE IF NOT EXISTS class_section (
    id                     UUID PRIMARY KEY,
    organization_id        VARCHAR(64)  NOT NULL,
    branch_id              VARCHAR(64),
    academic_session_id    VARCHAR(64),
    class_id               UUID         NOT NULL,
    name                   VARCHAR(128) NOT NULL,
    code                   VARCHAR(64),
    student_label          VARCHAR(191),
    class_teacher_username  VARCHAR(128),
    room                   VARCHAR(64),
    capacity               INTEGER,
    status                 VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    attributes             JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_class_section_org ON class_section (organization_id, name);
CREATE INDEX idx_class_section_class ON class_section (organization_id, class_id);
CREATE INDEX idx_class_section_teacher ON class_section (organization_id, class_teacher_username);

CREATE TABLE IF NOT EXISTS subject (
    id                    UUID PRIMARY KEY,
    organization_id       VARCHAR(64)  NOT NULL,
    branch_id             VARCHAR(64),
    academic_session_id   VARCHAR(64),
    name                  VARCHAR(128) NOT NULL,
    code                  VARCHAR(64),
    subject_type          VARCHAR(32)  DEFAULT 'CORE',
    status                VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    attributes            JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subject_org ON subject (organization_id, name);

CREATE TABLE IF NOT EXISTS teaching_assignment (
    id                    UUID PRIMARY KEY,
    organization_id       VARCHAR(64)  NOT NULL,
    branch_id             VARCHAR(64),
    academic_session_id   VARCHAR(64),
    section_id            UUID         NOT NULL,
    subject_id            UUID,
    teacher_username      VARCHAR(128) NOT NULL,
    is_class_teacher      BOOLEAN      NOT NULL DEFAULT FALSE,
    status                VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_teaching_assignment_org ON teaching_assignment (organization_id, created_at DESC);
CREATE INDEX idx_teaching_assignment_section ON teaching_assignment (organization_id, section_id);
CREATE INDEX idx_teaching_assignment_teacher ON teaching_assignment (organization_id, teacher_username);

CREATE TABLE IF NOT EXISTS timetable_period (
    id                    UUID PRIMARY KEY,
    organization_id       VARCHAR(64)  NOT NULL,
    branch_id             VARCHAR(64),
    academic_session_id   VARCHAR(64),
    period_no             INTEGER      NOT NULL DEFAULT 0,
    label                 VARCHAR(64)  NOT NULL,
    start_time            VARCHAR(8),
    end_time              VARCHAR(8),
    is_break              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_timetable_period_org ON timetable_period (organization_id, period_no);

CREATE TABLE IF NOT EXISTS timetable_slot (
    id                    UUID PRIMARY KEY,
    organization_id       VARCHAR(64)  NOT NULL,
    branch_id             VARCHAR(64),
    academic_session_id   VARCHAR(64),
    section_id            UUID         NOT NULL,
    day_of_week           INTEGER      NOT NULL DEFAULT 1,
    period_id             UUID         NOT NULL,
    subject_id            UUID,
    teacher_username      VARCHAR(128),
    room                  VARCHAR(64),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_timetable_slot_section ON timetable_slot (organization_id, section_id, day_of_week);
CREATE INDEX idx_timetable_slot_teacher ON timetable_slot (organization_id, teacher_username, day_of_week);
