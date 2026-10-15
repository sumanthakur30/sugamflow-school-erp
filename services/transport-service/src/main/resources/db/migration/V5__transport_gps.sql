CREATE TABLE IF NOT EXISTS transport_gps_ping (
    id                UUID PRIMARY KEY,
    organization_id   VARCHAR(64)  NOT NULL,
    vehicle_no        VARCHAR(64)  NOT NULL,
    latitude          NUMERIC(10,6) NOT NULL,
    longitude         NUMERIC(10,6) NOT NULL,
    note              VARCHAR(256),
    recorded_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_transport_gps_vehicle
    ON transport_gps_ping (organization_id, vehicle_no, recorded_at DESC);
