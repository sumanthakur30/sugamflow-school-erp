-- Performance indexes for multi-school scale.

-- Branch/session-scoped paged listing used by the admin exam screens.
CREATE INDEX IF NOT EXISTS idx_exam_record_org_branch_session
    ON exam_record (organization_id, branch_id, academic_session_id, updated_at DESC);

-- Parent/student result views resolve marks by admission number.
CREATE INDEX IF NOT EXISTS idx_exam_mark_org_admission
    ON exam_mark (organization_id, admission_no, updated_at DESC)
    WHERE admission_no IS NOT NULL;

-- Parent portal homework: submissions are looked up per student across homeworks.
CREATE INDEX IF NOT EXISTS idx_lms_submission_org_admission
    ON lms_homework_submission (organization_id, admission_no, submitted_at DESC)
    WHERE admission_no IS NOT NULL;

-- Exam definitions listed by status (published exams for portals).
CREATE INDEX IF NOT EXISTS idx_exam_definition_org_status
    ON exam_definition (organization_id, status, updated_at DESC);
