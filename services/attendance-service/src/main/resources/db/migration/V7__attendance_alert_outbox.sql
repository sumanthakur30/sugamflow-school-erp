-- Durable per-channel outbox for roster parent alerts (crash-safe idempotency + retry).
CREATE TABLE IF NOT EXISTS attendance_alert_outbox (
  id UUID PRIMARY KEY,
  organization_id VARCHAR(64) NOT NULL,
  branch_id VARCHAR(64),
  academic_session_id VARCHAR(64),
  session_id UUID NOT NULL,
  mark_id UUID NOT NULL,
  admission_no VARCHAR(64),
  student_name VARCHAR(191),
  mark_status VARCHAR(32) NOT NULL,
  intent VARCHAR(64) NOT NULL,
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

-- One outbox row per mark + notified status + channel + recipient.
CREATE UNIQUE INDEX IF NOT EXISTS ux_att_alert_outbox_dedup
  ON attendance_alert_outbox (mark_id, mark_status, channel, recipient);

CREATE INDEX IF NOT EXISTS ix_att_alert_outbox_session
  ON attendance_alert_outbox (session_id);

CREATE INDEX IF NOT EXISTS ix_att_alert_outbox_retry
  ON attendance_alert_outbox (status, attempts, created_at);
