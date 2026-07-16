-- Phase 15: AI attendance / device adapters (config-driven, no vendor SDKs).

CREATE TABLE attendance_device (
    id               VARCHAR(64)  PRIMARY KEY,
    organization_id  VARCHAR(64)  NOT NULL,
    branch_id        VARCHAR(64),
    device_key       VARCHAR(128) NOT NULL,
    name             VARCHAR(256) NOT NULL,
    adapter_type     VARCHAR(64)  NOT NULL,
    status           VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    config_json      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, device_key)
);

CREATE INDEX idx_attendance_device_org ON attendance_device (organization_id, branch_id);

CREATE TABLE attendance_device_event (
    id               VARCHAR(64)  PRIMARY KEY,
    organization_id  VARCHAR(64)  NOT NULL,
    branch_id        VARCHAR(64),
    device_id        VARCHAR(64)  NOT NULL,
    adapter_type     VARCHAR(64)  NOT NULL,
    status           VARCHAR(32)  NOT NULL,
    payload_json     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    attendance_record_id VARCHAR(64),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_attendance_device_event_org_created
    ON attendance_device_event (organization_id, created_at DESC);
CREATE INDEX idx_attendance_device_event_device
    ON attendance_device_event (device_id, created_at DESC);
