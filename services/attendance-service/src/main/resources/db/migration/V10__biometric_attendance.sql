-- Biometric attendance sits on the existing attendance device registry.
-- Raw fingerprint and face templates are not stored.

ALTER TABLE attendance_device
    ADD COLUMN IF NOT EXISTS vendor VARCHAR(32),
    ADD COLUMN IF NOT EXISTS serial_number VARCHAR(128),
    ADD COLUMN IF NOT EXISTS device_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS location VARCHAR(128),
    ADD COLUMN IF NOT EXISTS gate_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS firmware VARCHAR(64),
    ADD COLUMN IF NOT EXISTS direction VARCHAR(16) NOT NULL DEFAULT 'BOTH',
    ADD COLUMN IF NOT EXISTS time_zone VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
    ADD COLUMN IF NOT EXISTS credential_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS credential_prefix VARCHAR(16),
    ADD COLUMN IF NOT EXISTS last_heartbeat_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_event_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS heartbeat_timeout_seconds INT NOT NULL DEFAULT 120,
    ADD COLUMN IF NOT EXISTS error_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_device_time TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS uq_attendance_device_serial
    ON attendance_device (serial_number)
    WHERE serial_number IS NOT NULL AND serial_number <> '';

CREATE TABLE IF NOT EXISTS biometric_rule (
    id                UUID PRIMARY KEY,
    organization_id   VARCHAR(64) NOT NULL,
    branch_id         VARCHAR(64) NOT NULL DEFAULT '',
    punch_mode        VARCHAR(32) NOT NULL DEFAULT 'FIRST_LAST',
    school_start      TIME NOT NULL DEFAULT '08:30',
    grace_minutes     INT NOT NULL DEFAULT 10,
    school_end        TIME NOT NULL DEFAULT '15:00',
    split_time        TIME NOT NULL DEFAULT '12:00',
    half_day_minutes  INT NOT NULL DEFAULT 240,
    time_zone         VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
    drift_threshold_seconds INT NOT NULL DEFAULT 120,
    notify_on_check_in BOOLEAN NOT NULL DEFAULT TRUE,
    notify_channels   VARCHAR(128) NOT NULL DEFAULT 'IN_APP',
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_biometric_rule_org_branch
    ON biometric_rule (organization_id, branch_id);

CREATE TABLE IF NOT EXISTS biometric_enrollment (
    id                 UUID PRIMARY KEY,
    organization_id    VARCHAR(64) NOT NULL,
    branch_id          VARCHAR(64),
    person_type        VARCHAR(16) NOT NULL,
    person_id          VARCHAR(64),
    person_code        VARCHAR(64),
    display_name       VARCHAR(191) NOT NULL,
    class_section      VARCHAR(64),
    section_id         UUID,
    enrollment_code    VARCHAR(64) NOT NULL,
    verification_type  VARCHAR(32) NOT NULL DEFAULT 'FINGERPRINT',
    status             VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    notify_mobile      VARCHAR(32),
    notify_email       VARCHAR(191),
    device_id          VARCHAR(64),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_biometric_enrollment_code
    ON biometric_enrollment (organization_id, enrollment_code);
CREATE INDEX IF NOT EXISTS idx_biometric_enrollment_person
    ON biometric_enrollment (organization_id, person_type, person_code);

CREATE TABLE IF NOT EXISTS biometric_event (
    id                 UUID PRIMARY KEY,
    organization_id    VARCHAR(64) NOT NULL,
    branch_id          VARCHAR(64),
    device_id          VARCHAR(64),
    serial_number      VARCHAR(128),
    person_code        VARCHAR(64),
    event_time         TIMESTAMPTZ NOT NULL,
    event_type         VARCHAR(32),
    verification_type  VARCHAR(32),
    source             VARCHAR(32) NOT NULL DEFAULT 'BIOMETRIC',
    status             VARCHAR(32) NOT NULL,
    error_message      VARCHAR(512),
    event_hash         VARCHAR(64) NOT NULL,
    source_event_id    VARCHAR(128),
    received_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at       TIMESTAMPTZ,
    notified           BOOLEAN NOT NULL DEFAULT FALSE,
    retry_count        INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_biometric_event_hash ON biometric_event (event_hash);
CREATE INDEX IF NOT EXISTS idx_biometric_event_org_time
    ON biometric_event (organization_id, event_time DESC);
CREATE INDEX IF NOT EXISTS idx_biometric_event_status
    ON biometric_event (organization_id, status, received_at DESC);
CREATE INDEX IF NOT EXISTS idx_biometric_event_device
    ON biometric_event (device_id, received_at DESC);

CREATE TABLE IF NOT EXISTS biometric_day (
    id                 UUID PRIMARY KEY,
    organization_id    VARCHAR(64) NOT NULL,
    branch_id          VARCHAR(64),
    person_type        VARCHAR(16) NOT NULL,
    person_code        VARCHAR(64) NOT NULL,
    display_name       VARCHAR(191),
    class_section      VARCHAR(64),
    attendance_date    DATE NOT NULL,
    first_in           TIMESTAMPTZ,
    last_out           TIMESTAMPTZ,
    status             VARCHAR(32) NOT NULL,
    late_minutes       INT NOT NULL DEFAULT 0,
    early_minutes      INT NOT NULL DEFAULT 0,
    working_minutes    INT NOT NULL DEFAULT 0,
    attendance_mark_id UUID,
    corrected          BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_biometric_day_person
    ON biometric_day (organization_id, person_code, attendance_date);
CREATE INDEX IF NOT EXISTS idx_biometric_day_date
    ON biometric_day (organization_id, attendance_date DESC);

CREATE TABLE IF NOT EXISTS biometric_command (
    id             UUID PRIMARY KEY,
    organization_id VARCHAR(64) NOT NULL,
    device_id      VARCHAR(64) NOT NULL,
    command_text   VARCHAR(256) NOT NULL,
    dangerous      BOOLEAN NOT NULL DEFAULT FALSE,
    requested_by   VARCHAR(128),
    requested_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status         VARCHAR(32) NOT NULL,
    response       VARCHAR(1024),
    completed_at   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_biometric_command_device
    ON biometric_command (device_id, status, requested_at);

CREATE TABLE IF NOT EXISTS biometric_audit (
    id             UUID PRIMARY KEY,
    organization_id VARCHAR(64) NOT NULL,
    user_id        VARCHAR(128),
    action         VARCHAR(64) NOT NULL,
    entity_name    VARCHAR(64) NOT NULL,
    entity_id      VARCHAR(64),
    old_value      VARCHAR(1024),
    new_value      VARCHAR(1024),
    ip_address     VARCHAR(64),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_biometric_audit_org
    ON biometric_audit (organization_id, created_at DESC);

CREATE TABLE IF NOT EXISTS attendance_correction (
    id                UUID PRIMARY KEY,
    organization_id   VARCHAR(64) NOT NULL,
    biometric_day_id  UUID NOT NULL,
    original_status   VARCHAR(32) NOT NULL,
    corrected_status  VARCHAR(32) NOT NULL,
    reason            VARCHAR(512) NOT NULL,
    corrected_by      VARCHAR(128),
    corrected_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_attendance_correction_day
    ON attendance_correction (biometric_day_id, corrected_at DESC);
