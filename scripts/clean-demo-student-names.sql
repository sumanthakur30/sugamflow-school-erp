-- Demo cleanup: meaningful student names for presentation
-- DB: school_student_db

BEGIN;

-- 1) Soft-delete smoke-test / lifecycle / phase students
UPDATE student_record
SET deleted_at = NOW(),
    deleted_by = 'demo-cleanup',
    delete_reason = 'Demo cleanup: smoke-test student',
    updated_at = NOW()
WHERE deleted_at IS NULL
  AND organization_id = 'demo-school'
  AND (
    COALESCE(answers->>'fullName', '') ~* '^(ReportCard|Alert|Gradebook|Roster|Lifecycle|Phase[0-9]+)'
    OR COALESCE(answers->>'fullName', '') ~* 'Smoke'
    OR COALESCE(answers->>'classApplied', '') ~* '^(ReportCard|Alert|Gradebook|Roster)-'
  );

-- 2) Strip "(Grade …)" suffixes from display names
UPDATE student_record
SET answers = jsonb_set(
      answers,
      '{fullName}',
      to_jsonb(TRIM(REGEXP_REPLACE(answers->>'fullName', '\s*\([^)]*\)\s*$', ''))),
      true
    ),
    updated_at = NOW()
WHERE deleted_at IS NULL
  AND organization_id = 'demo-school'
  AND answers IS NOT NULL
  AND COALESCE(answers->>'fullName', '') ~ '\(';

-- 3) Soft-delete exact name+class duplicates (keep newest)
WITH ranked AS (
  SELECT id,
         ROW_NUMBER() OVER (
           PARTITION BY
             LOWER(TRIM(answers->>'fullName')),
             LOWER(TRIM(COALESCE(answers->>'classApplied', '')))
           ORDER BY updated_at DESC NULLS LAST, created_at DESC NULLS LAST
         ) AS rn
  FROM student_record
  WHERE deleted_at IS NULL
    AND organization_id = 'demo-school'
)
UPDATE student_record s
SET deleted_at = NOW(),
    deleted_by = 'demo-cleanup',
    delete_reason = 'Demo cleanup: duplicate student',
    updated_at = NOW()
FROM ranked r
WHERE s.id = r.id
  AND r.rn > 1;

-- 4) Normalize odd class labels
UPDATE student_record
SET answers = jsonb_set(
      jsonb_set(answers, '{classApplied}', '"Grade 3-A"', true),
      '{classSection}', '"Grade 3-A"', true
    ),
    updated_at = NOW()
WHERE deleted_at IS NULL
  AND organization_id = 'demo-school'
  AND TRIM(COALESCE(answers->>'classApplied', '')) IN ('III', '3');

UPDATE student_record
SET answers = jsonb_set(
      jsonb_set(answers, '{classApplied}', '"Grade 4-A"', true),
      '{classSection}', '"Grade 4-A"', true
    ),
    updated_at = NOW()
WHERE deleted_at IS NULL
  AND organization_id = 'demo-school'
  AND TRIM(COALESCE(answers->>'classApplied', '')) IN ('IV', '4');

UPDATE student_record
SET answers = jsonb_set(
      jsonb_set(answers, '{classApplied}', '"Grade 5-A"', true),
      '{classSection}', '"Grade 5-A"', true
    ),
    updated_at = NOW()
WHERE deleted_at IS NULL
  AND organization_id = 'demo-school'
  AND TRIM(COALESCE(answers->>'classApplied', '')) IN ('V', '5');

-- 5) Fill missing father/parent names from student surname
UPDATE student_record
SET answers = jsonb_set(
      jsonb_set(
        answers,
        '{fatherName}',
        to_jsonb('Rajesh ' || COALESCE(NULLIF(SPLIT_PART(answers->>'fullName', ' ', -1), ''), 'Parent')),
        true
      ),
      '{parentName}',
      to_jsonb('Rajesh ' || COALESCE(NULLIF(SPLIT_PART(answers->>'fullName', ' ', -1), ''), 'Parent')),
      true
    ),
    updated_at = NOW()
WHERE deleted_at IS NULL
  AND organization_id = 'demo-school'
  AND answers IS NOT NULL
  AND COALESCE(answers->>'fullName', '') <> ''
  AND (
    COALESCE(answers->>'fatherName', '') = ''
    OR COALESCE(answers->>'fatherName', '') ~* '^Mr\.?\s*Parent'
    OR COALESCE(answers->>'fatherName', '') ~* '^Mrs\.?\s*Parent'
  );

COMMIT;

-- Preview
SELECT admission_no,
       answers->>'fullName' AS full_name,
       answers->>'classApplied' AS class,
       answers->>'fatherName' AS father
FROM student_record
WHERE deleted_at IS NULL
  AND organization_id = 'demo-school'
ORDER BY updated_at DESC
LIMIT 25;

SELECT COUNT(*) AS active_students
FROM student_record
WHERE deleted_at IS NULL
  AND organization_id = 'demo-school';
