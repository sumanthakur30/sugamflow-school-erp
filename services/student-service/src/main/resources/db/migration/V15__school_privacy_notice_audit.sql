INSERT INTO school_privacy_notice (id, notice_code, version, title, body, created_at)
SELECT
    '6f0c2a1e-7b44-4d1a-9c31-2a8e5b70d4f1'::uuid,
    'SCHOOL_STUDENT',
    '2026.10',
    'Student and parent information',
    'This school uses student and parent details to run admission, attendance, fees, exams, and required school notices. Those activities are part of operating the school and cannot be switched off from this page. Optional school updates are separate and can be allowed or withdrawn here. A request to access, correct, or erase information is recorded for the school office. Recording or completing an erasure request does not delete admission, attendance, fee, exam, or backup records. This page does not collect identity documents and does not block admission.',
    NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM school_privacy_notice
    WHERE notice_code = 'SCHOOL_STUDENT' AND version = '2026.10'
);

CREATE TABLE IF NOT EXISTS school_privacy_audit (
    id               UUID PRIMARY KEY,
    organization_id  VARCHAR(64)  NOT NULL,
    actor            VARCHAR(128) NOT NULL,
    action           VARCHAR(40)  NOT NULL,
    target_ref       VARCHAR(80),
    detail           VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_school_privacy_audit_org
    ON school_privacy_audit (organization_id, created_at DESC);
