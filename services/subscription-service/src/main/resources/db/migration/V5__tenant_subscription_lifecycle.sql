-- Phase 3: tenant subscription lifecycle (additive).
-- Defaults: ACTIVE forever, enforcement off → School entitlements unchanged until configured.

CREATE TABLE tenant_subscription_lifecycle (
    organization_id      VARCHAR(64)  PRIMARY KEY
        REFERENCES tenant_subscription(organization_id) ON DELETE CASCADE,
    status               VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    trial_ends_at        TIMESTAMPTZ  NULL,
    expires_at           TIMESTAMPTZ  NULL,
    grace_ends_at        TIMESTAMPTZ  NULL,
    grace_days           INT          NOT NULL DEFAULT 7,
    enforcement_enabled  BOOLEAN      NOT NULL DEFAULT FALSE,
    notes                VARCHAR(512) NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tenant_lifecycle_status ON tenant_subscription_lifecycle (status);
CREATE INDEX idx_tenant_lifecycle_expires ON tenant_subscription_lifecycle (expires_at);

-- Existing School tenants: ACTIVE forever, no enforcement.
INSERT INTO tenant_subscription_lifecycle (
    organization_id, status, trial_ends_at, expires_at, grace_ends_at,
    grace_days, enforcement_enabled, notes
)
SELECT
    ts.organization_id,
    'ACTIVE',
    NULL,
    NULL,
    NULL,
    7,
    FALSE,
    'Phase 3 backfill: ACTIVE forever (enforcement off)'
FROM tenant_subscription ts
ON CONFLICT (organization_id) DO NOTHING;
