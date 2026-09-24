CREATE TABLE IF NOT EXISTS transport_route (
  id UUID PRIMARY KEY,
  organization_id VARCHAR(64) NOT NULL,
  branch_id VARCHAR(64),
  route_key VARCHAR(64) NOT NULL,
  route_name VARCHAR(191) NOT NULL,
  vehicle_no VARCHAR(64),
  capacity INT NOT NULL DEFAULT 40,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_transport_route_key
  ON transport_route (organization_id, route_key);

CREATE INDEX IF NOT EXISTS idx_transport_route_org
  ON transport_route (organization_id, status);

CREATE TABLE IF NOT EXISTS transport_assignment (
  id UUID PRIMARY KEY,
  organization_id VARCHAR(64) NOT NULL,
  branch_id VARCHAR(64),
  route_id UUID NOT NULL REFERENCES transport_route(id),
  student_id UUID,
  admission_no VARCHAR(64) NOT NULL,
  student_name VARCHAR(191),
  stop_name VARCHAR(191),
  pickup_time VARCHAR(32),
  status VARCHAR(32) NOT NULL,
  assigned_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ,
  created_by VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_transport_assign_org_status
  ON transport_assignment (organization_id, status);

CREATE INDEX IF NOT EXISTS idx_transport_assign_admission
  ON transport_assignment (organization_id, admission_no);
