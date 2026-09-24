CREATE TABLE IF NOT EXISTS staff_record (
    id              UUID PRIMARY KEY,
    organization_id VARCHAR(64)  NOT NULL,
    branch_id       VARCHAR(64),
    form_key        VARCHAR(128) NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    employee_no     VARCHAR(64),
    answers         JSONB NOT NULL DEFAULT '{}'::jsonb,
    history         JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_by      VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_staff_record_org
    ON staff_record (organization_id, updated_at DESC);

CREATE INDEX idx_staff_record_org_branch
    ON staff_record (organization_id, branch_id, updated_at DESC);

CREATE INDEX idx_staff_record_employee_no
    ON staff_record (organization_id, employee_no);
