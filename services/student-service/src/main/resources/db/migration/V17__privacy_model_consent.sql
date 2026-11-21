ALTER TABLE school_privacy_consent
    ADD COLUMN IF NOT EXISTS child_ref VARCHAR(64);

ALTER TABLE school_privacy_consent
    ADD COLUMN IF NOT EXISTS purpose_text TEXT;
