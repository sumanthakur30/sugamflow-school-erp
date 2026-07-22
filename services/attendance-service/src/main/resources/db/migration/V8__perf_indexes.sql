-- Performance indexes for multi-school scale.

-- Branch/session-scoped paged listing used by the admin attendance screens.
CREATE INDEX IF NOT EXISTS idx_attendance_record_org_branch_session
    ON attendance_record (organization_id, branch_id, academic_session_id, updated_at DESC);

-- Parent portal "my child's attendance" resolves marks by admission number.
CREATE INDEX IF NOT EXISTS idx_attendance_mark_org_admission
    ON attendance_mark (organization_id, admission_no, marked_at DESC)
    WHERE admission_no IS NOT NULL;
