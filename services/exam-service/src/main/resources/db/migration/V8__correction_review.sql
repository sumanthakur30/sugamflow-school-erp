ALTER TABLE exam_definition
    ADD COLUMN IF NOT EXISTS status_before_correction VARCHAR(32);
