-- Parent alerts for roster attendance: mark-level idempotency for submit notifications.
ALTER TABLE attendance_mark ADD COLUMN IF NOT EXISTS last_notified_status VARCHAR(32);
ALTER TABLE attendance_mark ADD COLUMN IF NOT EXISTS last_notified_at TIMESTAMPTZ;
