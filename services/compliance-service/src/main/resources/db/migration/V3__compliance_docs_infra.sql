-- P2: infrastructure inventory + compliance document vault.

CREATE TABLE infrastructure_asset (
    id                  BIGSERIAL PRIMARY KEY,
    organization_id     VARCHAR(100) NOT NULL,
    branch_id           VARCHAR(100),
    category            VARCHAR(60) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    quantity            INT NOT NULL DEFAULT 1,
    capacity            INT,
    unit_label          VARCHAR(40),
    condition_code      VARCHAR(40) NOT NULL DEFAULT 'GOOD',
    location_note       VARCHAR(255),
    notes               TEXT,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_infra_asset_org ON infrastructure_asset (organization_id, category, active);

CREATE TABLE compliance_document (
    id                  BIGSERIAL PRIMARY KEY,
    organization_id     VARCHAR(100) NOT NULL,
    branch_id           VARCHAR(100),
    doc_type            VARCHAR(80) NOT NULL,
    title               VARCHAR(255) NOT NULL,
    reference_no        VARCHAR(120),
    issuer              VARCHAR(255),
    issued_on           DATE,
    expires_on          DATE,
    status              VARCHAR(40) NOT NULL DEFAULT 'VALID',
    external_url        VARCHAR(1000),
    file_name           VARCHAR(255),
    storage_path        VARCHAR(1000),
    content_type        VARCHAR(120),
    file_size           BIGINT,
    version_label       VARCHAR(40),
    notes               TEXT,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_compliance_doc_org ON compliance_document (organization_id, doc_type, active);
CREATE INDEX idx_compliance_doc_expiry ON compliance_document (organization_id, expires_on, status);
