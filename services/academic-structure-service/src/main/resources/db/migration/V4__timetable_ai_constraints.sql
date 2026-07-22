ALTER TABLE teaching_assignment
    ADD COLUMN IF NOT EXISTS weekly_periods INTEGER NOT NULL DEFAULT 4,
    ADD COLUMN IF NOT EXISTS max_daily_periods INTEGER NOT NULL DEFAULT 2,
    ADD COLUMN IF NOT EXISTS preferred_room VARCHAR(64),
    ADD COLUMN IF NOT EXISTS unavailable_slots JSONB NOT NULL DEFAULT '[]'::jsonb;

CREATE TABLE IF NOT EXISTS timetable_room (
    id                  UUID PRIMARY KEY,
    organization_id     VARCHAR(64) NOT NULL,
    branch_id           VARCHAR(64),
    academic_session_id VARCHAR(64),
    name                VARCHAR(64) NOT NULL,
    capacity            INTEGER NOT NULL DEFAULT 1,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_timetable_room_scope_name
    ON timetable_room (
        organization_id,
        COALESCE(branch_id, ''),
        COALESCE(academic_session_id, ''),
        LOWER(name)
    );

CREATE INDEX IF NOT EXISTS idx_timetable_room_scope
    ON timetable_room (organization_id, branch_id, academic_session_id, status);

CREATE UNIQUE INDEX IF NOT EXISTS uq_timetable_slot_section_cell
    ON timetable_slot (organization_id, section_id, day_of_week, period_id);

CREATE INDEX IF NOT EXISTS idx_timetable_slot_teacher_cell
    ON timetable_slot (organization_id, teacher_username, day_of_week, period_id);

CREATE INDEX IF NOT EXISTS idx_timetable_slot_room_cell
    ON timetable_slot (organization_id, room, day_of_week, period_id);
