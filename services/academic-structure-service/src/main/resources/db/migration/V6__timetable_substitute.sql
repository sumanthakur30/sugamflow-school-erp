CREATE TABLE IF NOT EXISTS timetable_substitute (
  id UUID PRIMARY KEY,
  organization_id VARCHAR(64) NOT NULL,
  slot_id UUID NOT NULL,
  substitute_date DATE NOT NULL,
  teacher_username VARCHAR(128) NOT NULL,
  note VARCHAR(256),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_timetable_substitute_slot_date
  ON timetable_substitute (slot_id, substitute_date);
