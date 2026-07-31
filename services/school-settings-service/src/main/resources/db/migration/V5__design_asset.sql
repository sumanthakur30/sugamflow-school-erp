CREATE TABLE IF NOT EXISTS design_asset (
    id              UUID PRIMARY KEY,
    organization_id VARCHAR(64)  NOT NULL,
    branch_id       VARCHAR(64),
    asset_type      VARCHAR(64)  NOT NULL,
    file_name       VARCHAR(255),
    content_type    VARCHAR(128),
    content_base64  TEXT         NOT NULL,
    byte_length     INT,
    created_by      VARCHAR(128),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_design_asset_org_type
    ON design_asset (organization_id, asset_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_design_asset_org_branch_type
    ON design_asset (organization_id, branch_id, asset_type);
