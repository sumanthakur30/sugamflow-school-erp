CREATE TABLE IF NOT EXISTS import_job (
  id UUID PRIMARY KEY,
  organization_id VARCHAR(64) NOT NULL,
  branch_id VARCHAR(64),
  academic_session_id VARCHAR(64),
  entity_type VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  file_name VARCHAR(255),
  mapping_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  stats_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_by VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_import_job_org ON import_job (organization_id, created_at DESC);

CREATE TABLE IF NOT EXISTS import_job_row (
  id UUID PRIMARY KEY,
  job_id UUID NOT NULL REFERENCES import_job(id) ON DELETE CASCADE,
  organization_id VARCHAR(64) NOT NULL,
  row_number INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  raw_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  mapped_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  error_message VARCHAR(1024),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_import_job_row_job ON import_job_row (job_id, row_number);
