-- Durable per-recipient outbox for fee due reminders.
CREATE TABLE IF NOT EXISTS fee_due_reminder_outbox (
  id UUID PRIMARY KEY,
  organization_id VARCHAR(64) NOT NULL,
  branch_id VARCHAR(64),
  academic_session_id VARCHAR(64),
  student_key VARCHAR(128) NOT NULL,
  admission_no VARCHAR(64),
  collection_id UUID,
  period_key VARCHAR(128) NOT NULL,
  amount NUMERIC(12,2),
  channel VARCHAR(20) NOT NULL,
  recipient VARCHAR(191) NOT NULL,
  guardian_name VARCHAR(191),
  subject VARCHAR(255) NOT NULL,
  body TEXT NOT NULL,
  status VARCHAR(20) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  last_error VARCHAR(512),
  notification_id VARCHAR(64),
  sent_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_fee_due_reminder_dedup
  ON fee_due_reminder_outbox (organization_id, student_key, period_key, channel, recipient);

CREATE INDEX IF NOT EXISTS ix_fee_due_reminder_retry
  ON fee_due_reminder_outbox (status, attempts, created_at);

CREATE INDEX IF NOT EXISTS ix_fee_due_reminder_admission
  ON fee_due_reminder_outbox (organization_id, admission_no, created_at DESC);
