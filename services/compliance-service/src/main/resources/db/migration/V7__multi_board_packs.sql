-- P6: versioned board templates + ICSE / STATE field & rule packs.

CREATE TABLE compliance_template (
    id                  BIGSERIAL PRIMARY KEY,
    pack_key            VARCHAR(80) NOT NULL,
    board_code          VARCHAR(40) NOT NULL REFERENCES board_definition (code),
    version_label       VARCHAR(40) NOT NULL,
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    status              VARCHAR(40) NOT NULL DEFAULT 'PUBLISHED',
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    published_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_compliance_template_pack UNIQUE (pack_key)
);

CREATE INDEX idx_compliance_template_board ON compliance_template (board_code, active, status);

ALTER TABLE school_compliance_profile
    ADD COLUMN IF NOT EXISTS active_pack_key VARCHAR(80);

ALTER TABLE submission_campaign
    ADD COLUMN IF NOT EXISTS pack_key VARCHAR(80);

INSERT INTO compliance_template (pack_key, board_code, version_label, title, description, status, active, published_at)
VALUES
    ('CBSE-2026.1', 'CBSE', '2026.1', 'CBSE Compliance Pack 2026.1',
     'Default CBSE student/staff readiness and export field maps.', 'PUBLISHED', TRUE, NOW()),
    ('ICSE-2026.1', 'ICSE', '2026.1', 'ICSE / CISCE Compliance Pack 2026.1',
     'ICSE-oriented masters projection (registration / UID emphasis).', 'PUBLISHED', TRUE, NOW()),
    ('STATE-2026.1', 'STATE', '2026.1', 'State Board Compliance Pack 2026.1',
     'Generic state-board pack with Samagra / enrollment emphasis.', 'PUBLISHED', TRUE, NOW())
ON CONFLICT (pack_key) DO NOTHING;

-- Backfill profile pack keys from board.
UPDATE school_compliance_profile
SET active_pack_key = CASE board_code
    WHEN 'ICSE' THEN 'ICSE-2026.1'
    WHEN 'STATE' THEN 'STATE-2026.1'
    ELSE 'CBSE-2026.1'
END
WHERE active_pack_key IS NULL;

-- ICSE student / staff field maps
INSERT INTO compliance_field_map
    (board_code, entity_type, field_key, source_path, label, required, severity, format_regex, sort_order)
VALUES
    ('ICSE', 'STUDENT', 'admissionNo', 'admissionNo', 'Admission No', TRUE, 'BLOCKER', NULL, 10),
    ('ICSE', 'STUDENT', 'fullName', 'fullName', 'Student Name', TRUE, 'BLOCKER', NULL, 20),
    ('ICSE', 'STUDENT', 'gender', 'gender', 'Gender', TRUE, 'BLOCKER', NULL, 30),
    ('ICSE', 'STUDENT', 'dateOfBirth', 'dateOfBirth', 'Date of Birth', TRUE, 'BLOCKER', NULL, 40),
    ('ICSE', 'STUDENT', 'classSection', 'classSection', 'Class / Section', TRUE, 'BLOCKER', NULL, 50),
    ('ICSE', 'STUDENT', 'mobile', 'mobile', 'Mobile', TRUE, 'BLOCKER', '^[6-9][0-9]{9}$', 60),
    ('ICSE', 'STUDENT', 'parentName', 'parentName', 'Parent / Guardian Name', TRUE, 'BLOCKER', NULL, 70),
    ('ICSE', 'STUDENT', 'schoolStudentId', 'schoolStudentId', 'School Student ID / UID', FALSE, 'WARN', NULL, 80),
    ('ICSE', 'STUDENT', 'email', 'email', 'Email', FALSE, 'WARN',
        '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$', 90),
    ('ICSE', 'STUDENT', 'aadhaar', 'aadhaar', 'Aadhaar', FALSE, 'WARN', '^[0-9]{12}$', 100),
    ('ICSE', 'STAFF', 'employeeNo', 'employeeNo', 'Employee No', TRUE, 'BLOCKER', NULL, 10),
    ('ICSE', 'STAFF', 'fullName', 'fullName', 'Staff Name', TRUE, 'BLOCKER', NULL, 20),
    ('ICSE', 'STAFF', 'gender', 'gender', 'Gender', TRUE, 'BLOCKER', NULL, 30),
    ('ICSE', 'STAFF', 'designation', 'designation', 'Designation', TRUE, 'BLOCKER', NULL, 40),
    ('ICSE', 'STAFF', 'mobile', 'mobile', 'Mobile', TRUE, 'BLOCKER', '^[6-9][0-9]{9}$', 50),
    ('ICSE', 'STAFF', 'email', 'email', 'Email', TRUE, 'WARN',
        '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$', 60),
    ('ICSE', 'STAFF', 'joiningDate', 'joiningDate', 'Date of Joining', FALSE, 'WARN', NULL, 70),
    ('ICSE', 'STAFF', 'department', 'department', 'Department', FALSE, 'WARN', NULL, 80)
ON CONFLICT (board_code, entity_type, field_key) DO NOTHING;

INSERT INTO validation_rule
    (board_code, rule_code, entity_type, rule_type, config_json, severity, message_template)
VALUES
    ('ICSE', 'STUDENT_UNIQUE_ADMISSION', 'STUDENT', 'UNIQUE',
        '{"field":"admissionNo"}'::jsonb, 'BLOCKER',
        'Duplicate admission number "{value}".'),
    ('ICSE', 'STUDENT_UNIQUE_SCHOOL_ID', 'STUDENT', 'UNIQUE',
        '{"field":"schoolStudentId","skipBlank":true}'::jsonb, 'WARN',
        'Duplicate school student ID "{value}".'),
    ('ICSE', 'STUDENT_AGE_CLASS', 'STUDENT', 'CROSS_FIELD',
        '{"dobField":"dateOfBirth","classField":"classSection"}'::jsonb, 'WARN',
        'Age may not match class for {label}.'),
    ('ICSE', 'STAFF_UNIQUE_EMPLOYEE', 'STAFF', 'UNIQUE',
        '{"field":"employeeNo"}'::jsonb, 'BLOCKER',
        'Duplicate employee number "{value}".')
ON CONFLICT (board_code, rule_code) DO NOTHING;

-- STATE board packs
INSERT INTO compliance_field_map
    (board_code, entity_type, field_key, source_path, label, required, severity, format_regex, sort_order)
VALUES
    ('STATE', 'STUDENT', 'admissionNo', 'admissionNo', 'Admission No', TRUE, 'BLOCKER', NULL, 10),
    ('STATE', 'STUDENT', 'fullName', 'fullName', 'Student Name', TRUE, 'BLOCKER', NULL, 20),
    ('STATE', 'STUDENT', 'gender', 'gender', 'Gender', TRUE, 'BLOCKER', NULL, 30),
    ('STATE', 'STUDENT', 'dateOfBirth', 'dateOfBirth', 'Date of Birth', TRUE, 'BLOCKER', NULL, 40),
    ('STATE', 'STUDENT', 'classSection', 'classSection', 'Class / Section', TRUE, 'BLOCKER', NULL, 50),
    ('STATE', 'STUDENT', 'mobile', 'mobile', 'Mobile', TRUE, 'BLOCKER', '^[6-9][0-9]{9}$', 60),
    ('STATE', 'STUDENT', 'parentName', 'parentName', 'Parent / Guardian Name', TRUE, 'BLOCKER', NULL, 70),
    ('STATE', 'STUDENT', 'samagraId', 'samagraId', 'Samagra / Enrollment ID', FALSE, 'WARN', NULL, 80),
    ('STATE', 'STUDENT', 'penNumber', 'penNumber', 'PEN Number', FALSE, 'WARN', NULL, 90),
    ('STATE', 'STUDENT', 'aadhaar', 'aadhaar', 'Aadhaar', FALSE, 'WARN', '^[0-9]{12}$', 100),
    ('STATE', 'STAFF', 'employeeNo', 'employeeNo', 'Employee No', TRUE, 'BLOCKER', NULL, 10),
    ('STATE', 'STAFF', 'fullName', 'fullName', 'Staff Name', TRUE, 'BLOCKER', NULL, 20),
    ('STATE', 'STAFF', 'gender', 'gender', 'Gender', TRUE, 'BLOCKER', NULL, 30),
    ('STATE', 'STAFF', 'designation', 'designation', 'Designation', TRUE, 'BLOCKER', NULL, 40),
    ('STATE', 'STAFF', 'mobile', 'mobile', 'Mobile', TRUE, 'BLOCKER', '^[6-9][0-9]{9}$', 50),
    ('STATE', 'STAFF', 'email', 'email', 'Email', FALSE, 'WARN',
        '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$', 60),
    ('STATE', 'STAFF', 'joiningDate', 'joiningDate', 'Date of Joining', FALSE, 'WARN', NULL, 70),
    ('STATE', 'STAFF', 'department', 'department', 'Department', FALSE, 'WARN', NULL, 80)
ON CONFLICT (board_code, entity_type, field_key) DO NOTHING;

INSERT INTO validation_rule
    (board_code, rule_code, entity_type, rule_type, config_json, severity, message_template)
VALUES
    ('STATE', 'STUDENT_UNIQUE_ADMISSION', 'STUDENT', 'UNIQUE',
        '{"field":"admissionNo"}'::jsonb, 'BLOCKER',
        'Duplicate admission number "{value}".'),
    ('STATE', 'STUDENT_UNIQUE_SAMAGRA', 'STUDENT', 'UNIQUE',
        '{"field":"samagraId","skipBlank":true}'::jsonb, 'WARN',
        'Duplicate Samagra / enrollment ID "{value}".'),
    ('STATE', 'STUDENT_AGE_CLASS', 'STUDENT', 'CROSS_FIELD',
        '{"dobField":"dateOfBirth","classField":"classSection"}'::jsonb, 'WARN',
        'Age may not match class for {label}.'),
    ('STATE', 'STAFF_UNIQUE_EMPLOYEE', 'STAFF', 'UNIQUE',
        '{"field":"employeeNo"}'::jsonb, 'BLOCKER',
        'Duplicate employee number "{value}".')
ON CONFLICT (board_code, rule_code) DO NOTHING;
