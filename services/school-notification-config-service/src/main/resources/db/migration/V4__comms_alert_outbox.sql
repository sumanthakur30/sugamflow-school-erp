-- Durable per-recipient outbox for Comms Hub guardian fan-out.
CREATE TABLE IF NOT EXISTS comms_alert_outbox (
  id UUID PRIMARY KEY,
  organization_id VARCHAR(64) NOT NULL,
  branch_id VARCHAR(64),
  announcement_id UUID NOT NULL,
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

CREATE UNIQUE INDEX IF NOT EXISTS ux_comms_alert_outbox_dedup
  ON comms_alert_outbox (announcement_id, channel, recipient);

CREATE INDEX IF NOT EXISTS ix_comms_alert_outbox_retry
  ON comms_alert_outbox (status, attempts, created_at);

CREATE INDEX IF NOT EXISTS ix_comms_alert_outbox_announcement
  ON comms_alert_outbox (announcement_id);
