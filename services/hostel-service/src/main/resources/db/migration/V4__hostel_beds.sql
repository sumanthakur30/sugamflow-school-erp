CREATE TABLE IF NOT EXISTS hostel_bed (
  id UUID PRIMARY KEY,
  organization_id VARCHAR(64) NOT NULL,
  branch_id VARCHAR(64),
  block_key VARCHAR(64) NOT NULL,
  room_no VARCHAR(32) NOT NULL,
  bed_no INT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'VACANT',
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_hostel_bed_slot
  ON hostel_bed (organization_id, block_key, room_no, bed_no);

CREATE INDEX IF NOT EXISTS idx_hostel_bed_org_status
  ON hostel_bed (organization_id, status);

CREATE TABLE IF NOT EXISTS hostel_occupancy (
  id UUID PRIMARY KEY,
  organization_id VARCHAR(64) NOT NULL,
  branch_id VARCHAR(64),
  bed_id UUID NOT NULL REFERENCES hostel_bed(id),
  student_id UUID,
  admission_no VARCHAR(64) NOT NULL,
  student_name VARCHAR(191),
  status VARCHAR(32) NOT NULL,
  allocated_at TIMESTAMPTZ NOT NULL,
  released_at TIMESTAMPTZ,
  created_by VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_hostel_occ_org_status
  ON hostel_occupancy (organization_id, status);

CREATE INDEX IF NOT EXISTS idx_hostel_occ_admission
  ON hostel_occupancy (organization_id, admission_no);
