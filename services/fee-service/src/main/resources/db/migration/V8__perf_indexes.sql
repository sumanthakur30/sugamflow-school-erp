-- Performance indexes for multi-school scale.

-- Finance analytics (sumApprovedAmount / sumByFeeHead / sumByMonth / recentApproved)
-- all filter on organization_id + status + created_at range.
CREATE INDEX IF NOT EXISTS idx_fee_collection_org_status_created
    ON fee_collection (organization_id, status, created_at DESC);

-- Branch/session-scoped paged listing used by the admin fee screens.
CREATE INDEX IF NOT EXISTS idx_fee_collection_org_branch_session
    ON fee_collection (organization_id, branch_id, academic_session_id, updated_at DESC);

-- Student fee lookups (clearance, pending fees, parent portal) resolve rows
-- by the admissionNo stored in answers.
CREATE INDEX IF NOT EXISTS idx_fee_collection_org_admission
    ON fee_collection (organization_id, (answers->>'admissionNo'))
    WHERE coalesce(answers->>'admissionNo', '') <> '';
