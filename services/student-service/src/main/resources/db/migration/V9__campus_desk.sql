CREATE TABLE IF NOT EXISTS campus_desk_item (
  id UUID PRIMARY KEY,
  organization_id VARCHAR(64) NOT NULL,
  branch_id VARCHAR(64),
  academic_session_id VARCHAR(64),
  kind VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  subject_type VARCHAR(32) NOT NULL,
  subject_ref VARCHAR(128),
  subject_name VARCHAR(256),
  title VARCHAR(256),
  note TEXT,
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_by VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_campus_desk_org_kind
  ON campus_desk_item (organization_id, kind, created_at DESC);
