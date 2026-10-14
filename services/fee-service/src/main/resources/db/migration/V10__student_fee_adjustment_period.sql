-- Recurring concessions stay in force. A period concession applies to one fee period only.
ALTER TABLE student_fee_adjustment
    ADD COLUMN IF NOT EXISTS adjustment_kind VARCHAR(32) NOT NULL DEFAULT 'RECURRING';

ALTER TABLE student_fee_adjustment
    ADD COLUMN IF NOT EXISTS period_key VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_student_fee_adj_kind
    ON student_fee_adjustment (organization_id, admission_no, adjustment_kind, status);
