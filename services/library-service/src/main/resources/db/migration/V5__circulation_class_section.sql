ALTER TABLE library_circulation
    ADD COLUMN IF NOT EXISTS class_section VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_library_circulation_student
    ON library_circulation (organization_id, admission_no, class_section);
