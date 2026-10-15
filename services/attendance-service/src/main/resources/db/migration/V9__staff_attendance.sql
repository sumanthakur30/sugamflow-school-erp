CREATE TABLE staff_attendance_month (
    id UUID PRIMARY KEY,
    organization_id VARCHAR(64) NOT NULL,
    branch_id VARCHAR(64) NOT NULL DEFAULT '',
    year_month VARCHAR(7) NOT NULL,
    status VARCHAR(32) NOT NULL,
    marks JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_staff_attendance_month
    ON staff_attendance_month (organization_id, branch_id, year_month);
