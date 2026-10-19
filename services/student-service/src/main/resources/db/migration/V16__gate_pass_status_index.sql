CREATE INDEX IF NOT EXISTS ix_campus_desk_org_kind_status
  ON campus_desk_item (organization_id, kind, status);
