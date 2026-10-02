CREATE TABLE academic_calendar (
    id UUID PRIMARY KEY,
    organization_id VARCHAR(64) NOT NULL,
    branch_id VARCHAR(64) NOT NULL DEFAULT '',
    academic_session_id VARCHAR(64) NOT NULL DEFAULT '',
    working_days JSONB NOT NULL,
    holidays JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_academic_calendar_scope
    ON academic_calendar (organization_id, branch_id, academic_session_id);
