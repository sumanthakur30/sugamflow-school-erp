-- Industry payroll correction lifecycle: immutable history + linked corrections.
ALTER TABLE payroll_record
    ADD COLUMN IF NOT EXISTS run_type VARCHAR(32) NOT NULL DEFAULT 'REGULAR',
    ADD COLUMN IF NOT EXISTS parent_record_id UUID NULL,
    ADD COLUMN IF NOT EXISTS payout_status VARCHAR(32) NOT NULL DEFAULT 'UNPAID';

CREATE INDEX IF NOT EXISTS idx_payroll_record_parent
    ON payroll_record (parent_record_id);

CREATE INDEX IF NOT EXISTS idx_payroll_record_org_run_type
    ON payroll_record (organization_id, run_type, updated_at DESC);
