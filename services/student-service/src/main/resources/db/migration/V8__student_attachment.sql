-- KYC / photo vault (distinct from issued certificates in student_document)
CREATE TABLE IF NOT EXISTS student_attachment (
  id UUID PRIMARY KEY,
  organization_id VARCHAR(64) NOT NULL,
  branch_id VARCHAR(64),
  academic_session_id VARCHAR(64),
  student_id UUID NOT NULL,
  admission_no VARCHAR(64),
  attachment_type VARCHAR(64) NOT NULL,
  file_name VARCHAR(255),
  content_type VARCHAR(128),
  content_base64 TEXT NOT NULL,
  byte_length INTEGER,
  created_by VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL,
  meta JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS ix_student_attachment_org_student
  ON student_attachment (organization_id, student_id, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_student_attachment_org_type
  ON student_attachment (organization_id, student_id, attachment_type);

-- Optional gov-ID expression indexes (NULL-safe; answers JSONB)
CREATE INDEX IF NOT EXISTS ix_student_answers_pen
  ON student_record (organization_id, (answers->>'penNumber'))
  WHERE deleted_at IS NULL AND coalesce(answers->>'penNumber', '') <> '';

CREATE INDEX IF NOT EXISTS ix_student_answers_apaar
  ON student_record (organization_id, (answers->>'apaarId'))
  WHERE deleted_at IS NULL AND coalesce(answers->>'apaarId', '') <> '';
