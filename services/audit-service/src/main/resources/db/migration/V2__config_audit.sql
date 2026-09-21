CREATE TABLE config_change_audit (
    id VARCHAR(64) PRIMARY KEY,
    organization_id VARCHAR(64) NOT NULL,
    branch_id VARCHAR(64),
    entity_type VARCHAR(128),
    entity_key VARCHAR(128),
    status VARCHAR(64) NOT NULL,
    changed_by VARCHAR(128),
    reason TEXT,
    old_value JSONB,
    new_value JSONB,
    rollback_of VARCHAR(64),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_config_change_audit_org_created ON config_change_audit (organization_id, created_at DESC);