-- =============================================================================
-- Fix Flyway V5 conflict: table was created manually, Flyway tries CREATE again
-- DB: school_subscription_db
--
-- Safe path: drop the manually created table (+ indexes), then restart
-- subscription-service so Flyway V5 applies cleanly and backfill runs.
-- HCP-01 plan assignment in tenant_subscription is NOT dropped.
-- =============================================================================

-- 1) See Flyway state
SELECT installed_rank, version, description, success, checksum
FROM flyway_schema_history_school_subscription
ORDER BY installed_rank;

-- 2) Drop manual table so V5 can recreate it
DROP TABLE IF EXISTS tenant_subscription_lifecycle CASCADE;

-- 3) After this, on EC2:
-- docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production \
--   up -d --force-recreate subscription-service
--
-- Wait for Up, then:
-- curl with X-Tenant-Id + X-Gateway-Verified + X-Internal-Service → FEATURE_ADMISSION
