CREATE TABLE IF NOT EXISTS student_desk_note (
  id UUID PRIMARY KEY,
  organization_id VARCHAR(64) NOT NULL,
  branch_id VARCHAR(64),
  student_id UUID NOT NULL,
  kind VARCHAR(32) NOT NULL,
  channel VARCHAR(32),
  body TEXT NOT NULL,
  created_by VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_student_desk_note_student
  ON student_desk_note (organization_id, student_id, kind, created_at DESC);
