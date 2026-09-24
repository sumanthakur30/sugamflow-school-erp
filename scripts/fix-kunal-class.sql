UPDATE student_record
SET answers = jsonb_set(
      jsonb_set(COALESCE(answers, '{}'::jsonb), '{classApplied}', '"Grade 8-A"', true),
      '{classSection}', '"Grade 8-A"', true
    ),
    updated_at = NOW()
WHERE deleted_at IS NULL
  AND organization_id = 'demo-school'
  AND COALESCE(answers->>'fullName', '') = 'Kunal Desai'
  AND COALESCE(answers->>'classApplied', '') = '';

SELECT COUNT(*) AS active,
       COUNT(DISTINCT answers->>'fullName') AS unique_names
FROM student_record
WHERE deleted_at IS NULL
  AND organization_id = 'demo-school';
