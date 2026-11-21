ALTER TABLE transport_route ADD COLUMN IF NOT EXISTS stops VARCHAR(1000);

DROP INDEX IF EXISTS uq_transport_route_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_transport_route_campus_key
  ON transport_route (organization_id, COALESCE(branch_id, ''), route_key);
