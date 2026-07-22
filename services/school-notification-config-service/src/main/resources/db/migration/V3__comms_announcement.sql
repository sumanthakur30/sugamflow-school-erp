CREATE TABLE IF NOT EXISTS comms_announcement (
  id UUID PRIMARY KEY,
  organization_id VARCHAR(64) NOT NULL,
  branch_id VARCHAR(64),
  title VARCHAR(255) NOT NULL,
  body TEXT NOT NULL,
  channel VARCHAR(32) NOT NULL,
  audience VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  delivery_json JSONB NOT NULL DEFAULT '[]'::jsonb,
  created_by VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_comms_announcement_org
  ON comms_announcement (organization_id, created_at DESC);
