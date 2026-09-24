CREATE TABLE IF NOT EXISTS library_book (
  id UUID PRIMARY KEY,
  organization_id VARCHAR(64) NOT NULL,
  branch_id VARCHAR(64),
  isbn VARCHAR(64),
  title VARCHAR(255) NOT NULL,
  author VARCHAR(191),
  category_key VARCHAR(64),
  copies_total INT NOT NULL DEFAULT 1,
  copies_available INT NOT NULL DEFAULT 1,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_library_book_org ON library_book (organization_id, title);

CREATE TABLE IF NOT EXISTS library_circulation (
  id UUID PRIMARY KEY,
  organization_id VARCHAR(64) NOT NULL,
  branch_id VARCHAR(64),
  book_id UUID NOT NULL REFERENCES library_book(id),
  student_id UUID,
  admission_no VARCHAR(64),
  student_name VARCHAR(191),
  status VARCHAR(32) NOT NULL,
  issued_at TIMESTAMPTZ NOT NULL,
  due_at TIMESTAMPTZ,
  returned_at TIMESTAMPTZ,
  fine_amount NUMERIC(12,2),
  created_by VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_library_circ_org ON library_circulation (organization_id, status);
CREATE INDEX IF NOT EXISTS idx_library_circ_student ON library_circulation (organization_id, admission_no);
