-- Student correction / soft-delete support (enterprise SIS pattern).
-- Soft-deleted rows stay in student_record; never lost by default.

ALTER TABLE student_record
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(128),
    ADD COLUMN IF NOT EXISTS delete_reason TEXT,
    ADD COLUMN IF NOT EXISTS restored_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS restored_by VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_student_org_active
    ON student_record (organization_id, updated_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_student_org_deleted
    ON student_record (organization_id, deleted_at DESC)
    WHERE deleted_at IS NOT NULL;

-- Admission number lookup among active students (uniqueness enforced in service).
DROP INDEX IF EXISTS idx_student_org_admission_no;
CREATE INDEX IF NOT EXISTS idx_student_org_admission_no_active
    ON student_record (organization_id, admission_no)
    WHERE deleted_at IS NULL AND admission_no IS NOT NULL AND admission_no <> '';

CREATE INDEX IF NOT EXISTS idx_student_answers_aadhaar
    ON student_record (organization_id, (answers->>'aadhaar'))
    WHERE deleted_at IS NULL AND coalesce(answers->>'aadhaar', '') <> '';

CREATE INDEX IF NOT EXISTS idx_student_answers_roll
    ON student_record (organization_id, (answers->>'classApplied'), (answers->>'rollNo'))
    WHERE deleted_at IS NULL AND coalesce(answers->>'rollNo', '') <> '';

-- Immutable field-change audit (never overwrite).
CREATE TABLE IF NOT EXISTS student_field_audit (
    id                UUID PRIMARY KEY,
    organization_id   VARCHAR(64)  NOT NULL,
    student_id        UUID         NOT NULL REFERENCES student_record (id) ON DELETE CASCADE,
    field_name        VARCHAR(128) NOT NULL,
    old_value         TEXT,
    new_value         TEXT,
    changed_by        VARCHAR(128),
    changed_role      VARCHAR(64),
    changed_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    reason            TEXT,
    ip_address        VARCHAR(64),
    user_agent        VARCHAR(512),
    event_type        VARCHAR(64)  NOT NULL DEFAULT 'FIELD_CHANGE'
);

CREATE INDEX IF NOT EXISTS idx_student_field_audit_student
    ON student_field_audit (organization_id, student_id, changed_at DESC);

CREATE INDEX IF NOT EXISTS idx_student_field_audit_org
    ON student_field_audit (organization_id, changed_at DESC);
