CREATE TABLE admission_lead (
    id UUID PRIMARY KEY,
    organization_id VARCHAR(64) NOT NULL,
    student_name VARCHAR(160) NOT NULL,
    father_name VARCHAR(160),
    mother_name VARCHAR(160),
    phone VARCHAR(32) NOT NULL,
    father_phone VARCHAR(32),
    mother_phone VARCHAR(32),
    address VARCHAR(400),
    class_applied_for VARCHAR(80),
    admission_no VARCHAR(64),
    created_by VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL,
    scheduled_at TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL,
    remark VARCHAR(1000),
    assigned_to VARCHAR(160),
    example_seed BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_admission_lead_org_phone ON admission_lead (organization_id, phone);
CREATE INDEX idx_admission_lead_org_status ON admission_lead (organization_id, status);
CREATE INDEX idx_admission_lead_org_created ON admission_lead (organization_id, created_at DESC);
