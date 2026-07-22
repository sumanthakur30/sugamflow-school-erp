-- Performance indexes for multi-school scale (100 schools x ~2000 students).

-- Tenant-scoped listing with branch + academic session (used by paged directory queries).
CREATE INDEX IF NOT EXISTS idx_student_org_branch_session_active
    ON student_record (organization_id, branch_id, academic_session_id, updated_at DESC)
    WHERE deleted_at IS NULL;

-- Case-insensitive admission-number lookup (repository uses AdmissionNoIgnoreCase,
-- which the plain admission_no index cannot serve).
CREATE INDEX IF NOT EXISTS idx_student_org_admission_lower_active
    ON student_record (organization_id, lower(admission_no))
    WHERE deleted_at IS NULL AND admission_no IS NOT NULL AND admission_no <> '';
