CREATE TABLE IF NOT EXISTS student_document (
  id UUID PRIMARY KEY,
  organization_id VARCHAR(64) NOT NULL,
  branch_id VARCHAR(64),
  academic_session_id VARCHAR(64),
  student_id UUID NOT NULL,
  admission_no VARCHAR(64),
  document_type VARCHAR(64) NOT NULL,
  template_key VARCHAR(128) NOT NULL,
  reference_no VARCHAR(64) NOT NULL,
  verification_token VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  file_name VARCHAR(255),
  content_type VARCHAR(128),
  content_base64 TEXT,
  byte_length INTEGER,
  issued_by VARCHAR(128),
  issued_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  revoke_reason TEXT,
  meta JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_student_document_token
  ON student_document (verification_token);

CREATE INDEX IF NOT EXISTS ix_student_document_org_student
  ON student_document (organization_id, student_id, issued_at DESC);

CREATE INDEX IF NOT EXISTS ix_student_document_org_type
  ON student_document (organization_id, document_type, status);
