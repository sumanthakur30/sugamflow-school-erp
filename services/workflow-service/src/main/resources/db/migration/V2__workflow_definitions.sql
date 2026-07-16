CREATE TABLE workflow_definition (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id VARCHAR(64),
    workflow_key VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE NULLS NOT DISTINCT (organization_id, workflow_key)
);