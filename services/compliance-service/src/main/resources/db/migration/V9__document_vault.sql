-- Documents Vault: categories, folders, versions, audit. Reuses compliance_document.

ALTER TABLE compliance_document
    ADD COLUMN public_code VARCHAR(40),
    ADD COLUMN category_code VARCHAR(80),
    ADD COLUMN compliance_area VARCHAR(80),
    ADD COLUMN folder_id BIGINT,
    ADD COLUMN academic_session_id VARCHAR(100),
    ADD COLUMN visibility VARCHAR(40) NOT NULL DEFAULT 'SCHOOL',
    ADD COLUMN workflow_status VARCHAR(40) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN version_no INT NOT NULL DEFAULT 1,
    ADD COLUMN tags VARCHAR(500),
    ADD COLUMN description TEXT,
    ADD COLUMN checksum_sha256 VARCHAR(64),
    ADD COLUMN uploaded_by VARCHAR(100),
    ADD COLUMN updated_by VARCHAR(100),
    ADD COLUMN rejection_reason TEXT,
    ADD COLUMN related_entity VARCHAR(80),
    ADD COLUMN related_entity_id VARCHAR(100),
    ADD COLUMN retention_years INT;

UPDATE compliance_document
SET public_code = 'DOC-LEGACY-' || id
WHERE public_code IS NULL;

ALTER TABLE compliance_document ALTER COLUMN public_code SET NOT NULL;

CREATE UNIQUE INDEX uq_compliance_doc_public_code
    ON compliance_document (organization_id, public_code);

CREATE INDEX idx_compliance_doc_workflow
    ON compliance_document (organization_id, workflow_status, active);

CREATE INDEX idx_compliance_doc_checksum
    ON compliance_document (organization_id, checksum_sha256);

CREATE TABLE compliance_document_category (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  VARCHAR(100) NOT NULL,
    code             VARCHAR(80) NOT NULL,
    name             VARCHAR(160) NOT NULL,
    compliance_area  VARCHAR(80),
    sort_order       INT NOT NULL DEFAULT 0,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_doc_category_org_code
    ON compliance_document_category (organization_id, code);

CREATE TABLE compliance_document_folder (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  VARCHAR(100) NOT NULL,
    parent_id        BIGINT REFERENCES compliance_document_folder (id),
    name             VARCHAR(160) NOT NULL,
    description      TEXT,
    visibility       VARCHAR(40) NOT NULL DEFAULT 'SCHOOL',
    compliance_area  VARCHAR(80),
    depth            INT NOT NULL DEFAULT 1,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_doc_folder_org ON compliance_document_folder (organization_id, active);

CREATE TABLE compliance_document_version (
    id               BIGSERIAL PRIMARY KEY,
    document_id      BIGINT NOT NULL REFERENCES compliance_document (id),
    organization_id  VARCHAR(100) NOT NULL,
    version_no       INT NOT NULL,
    file_name        VARCHAR(255),
    storage_path     VARCHAR(1000),
    content_type     VARCHAR(120),
    file_size        BIGINT,
    checksum_sha256  VARCHAR(64),
    change_reason    TEXT,
    uploaded_by      VARCHAR(100),
    uploaded_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_doc_version ON compliance_document_version (document_id, version_no);

CREATE TABLE compliance_document_audit (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  VARCHAR(100) NOT NULL,
    document_id      BIGINT NOT NULL,
    action           VARCHAR(40) NOT NULL,
    actor_user_id    VARCHAR(100),
    detail           TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_doc_audit_doc ON compliance_document_audit (organization_id, document_id, created_at);
