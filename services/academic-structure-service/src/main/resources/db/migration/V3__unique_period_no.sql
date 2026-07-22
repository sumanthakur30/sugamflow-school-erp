-- Dedupe period_no within org/branch/session, remapping slots to the kept row,
-- then enforce uniqueness going forward.

-- Prefer the oldest period as the canonical row for each (org, branch, session, period_no).
WITH ranked AS (
  SELECT
    id,
    organization_id,
    COALESCE(branch_id, '') AS branch_key,
    COALESCE(academic_session_id, '') AS session_key,
    period_no,
    ROW_NUMBER() OVER (
      PARTITION BY organization_id,
                   COALESCE(branch_id, ''),
                   COALESCE(academic_session_id, ''),
                   period_no
      ORDER BY created_at ASC, id ASC
    ) AS rn
  FROM timetable_period
),
keepers AS (
  SELECT id, organization_id, branch_key, session_key, period_no
  FROM ranked
  WHERE rn = 1
),
dupes AS (
  SELECT r.id AS dupe_id, k.id AS keep_id
  FROM ranked r
  JOIN keepers k
    ON k.organization_id = r.organization_id
   AND k.branch_key = r.branch_key
   AND k.session_key = r.session_key
   AND k.period_no = r.period_no
  WHERE r.rn > 1
)
UPDATE timetable_slot s
SET period_id = d.keep_id,
    updated_at = NOW()
FROM dupes d
WHERE s.period_id = d.dupe_id;

WITH ranked AS (
  SELECT
    id,
    ROW_NUMBER() OVER (
      PARTITION BY organization_id,
                   COALESCE(branch_id, ''),
                   COALESCE(academic_session_id, ''),
                   period_no
      ORDER BY created_at ASC, id ASC
    ) AS rn
  FROM timetable_period
)
DELETE FROM timetable_period
WHERE id IN (SELECT id FROM ranked WHERE rn > 1);

CREATE UNIQUE INDEX IF NOT EXISTS uq_timetable_period_scope_no
  ON timetable_period (
    organization_id,
    COALESCE(branch_id, ''),
    COALESCE(academic_session_id, ''),
    period_no
  );
