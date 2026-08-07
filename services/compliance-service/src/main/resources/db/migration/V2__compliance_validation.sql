-- P1: field maps, declarative rules, and validation findings for CBSE readiness.

CREATE TABLE compliance_field_map (
    id              BIGSERIAL PRIMARY KEY,
    board_code      VARCHAR(40) NOT NULL REFERENCES board_definition (code),
    entity_type     VARCHAR(40) NOT NULL,
    field_key       VARCHAR(100) NOT NULL,
    source_path     VARCHAR(200) NOT NULL,
    label           VARCHAR(160) NOT NULL,
    required        BOOLEAN NOT NULL DEFAULT FALSE,
    severity        VARCHAR(20) NOT NULL DEFAULT 'BLOCKER',
    format_regex    VARCHAR(255),
    sort_order      INT NOT NULL DEFAULT 0,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_compliance_field_map UNIQUE (board_code, entity_type, field_key)
);

CREATE INDEX idx_compliance_field_map_board ON compliance_field_map (board_code, entity_type, active);

CREATE TABLE validation_rule (
    id                  BIGSERIAL PRIMARY KEY,
    board_code          VARCHAR(40) NOT NULL REFERENCES board_definition (code),
    rule_code           VARCHAR(80) NOT NULL,
    entity_type         VARCHAR(40) NOT NULL,
    rule_type           VARCHAR(40) NOT NULL,
    config_json         JSONB NOT NULL DEFAULT '{}'::jsonb,
    severity            VARCHAR(20) NOT NULL DEFAULT 'BLOCKER',
    message_template    VARCHAR(500),
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_validation_rule UNIQUE (board_code, rule_code)
);

CREATE INDEX idx_validation_rule_board ON validation_rule (board_code, entity_type, active);

CREATE TABLE validation_finding (
    id                  BIGSERIAL PRIMARY KEY,
    organization_id     VARCHAR(100) NOT NULL,
    campaign_id         BIGINT REFERENCES submission_campaign (id) ON DELETE CASCADE,
    entity_type         VARCHAR(40) NOT NULL,
    entity_id           VARCHAR(100),
    entity_label        VARCHAR(255),
    field_key           VARCHAR(100),
    rule_code           VARCHAR(80) NOT NULL,
    severity            VARCHAR(20) NOT NULL,
    message             TEXT NOT NULL,
    suggestion          TEXT,
    status              VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_validation_finding_org ON validation_finding (organization_id, status, severity);
CREATE INDEX idx_validation_finding_campaign ON validation_finding (campaign_id, entity_type);
CREATE INDEX idx_validation_finding_field ON validation_finding (organization_id, entity_type, field_key);

-- CBSE student required / recommended fields (source = student directory + answers projection).
INSERT INTO compliance_field_map
    (board_code, entity_type, field_key, source_path, label, required, severity, format_regex, sort_order)
VALUES
    ('CBSE', 'STUDENT', 'admissionNo', 'admissionNo', 'Admission No', TRUE, 'BLOCKER', NULL, 10),
    ('CBSE', 'STUDENT', 'fullName', 'fullName', 'Student Name', TRUE, 'BLOCKER', NULL, 20),
    ('CBSE', 'STUDENT', 'gender', 'gender', 'Gender', TRUE, 'BLOCKER', NULL, 30),
    ('CBSE', 'STUDENT', 'dateOfBirth', 'dateOfBirth', 'Date of Birth', TRUE, 'BLOCKER', NULL, 40),
    ('CBSE', 'STUDENT', 'classSection', 'classSection', 'Class / Section', TRUE, 'BLOCKER', NULL, 50),
    ('CBSE', 'STUDENT', 'mobile', 'mobile', 'Mobile', TRUE, 'BLOCKER', '^[6-9][0-9]{9}$', 60),
    ('CBSE', 'STUDENT', 'parentName', 'parentName', 'Parent / Guardian Name', TRUE, 'BLOCKER', NULL, 70),
    ('CBSE', 'STUDENT', 'penNumber', 'penNumber', 'PEN Number', FALSE, 'WARN', NULL, 80),
    ('CBSE', 'STUDENT', 'apaarId', 'apaarId', 'APAAR ID', FALSE, 'WARN', NULL, 90),
    ('CBSE', 'STUDENT', 'aadhaar', 'aadhaar', 'Aadhaar', FALSE, 'WARN', '^[0-9]{12}$', 100),
    ('CBSE', 'STUDENT', 'email', 'email', 'Email', FALSE, 'WARN',
        '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$', 110)
ON CONFLICT (board_code, entity_type, field_key) DO NOTHING;

INSERT INTO compliance_field_map
    (board_code, entity_type, field_key, source_path, label, required, severity, format_regex, sort_order)
VALUES
    ('CBSE', 'STAFF', 'employeeNo', 'employeeNo', 'Employee No', TRUE, 'BLOCKER', NULL, 10),
    ('CBSE', 'STAFF', 'fullName', 'fullName', 'Staff Name', TRUE, 'BLOCKER', NULL, 20),
    ('CBSE', 'STAFF', 'gender', 'gender', 'Gender', TRUE, 'BLOCKER', NULL, 30),
    ('CBSE', 'STAFF', 'designation', 'designation', 'Designation', TRUE, 'BLOCKER', NULL, 40),
    ('CBSE', 'STAFF', 'mobile', 'mobile', 'Mobile', TRUE, 'BLOCKER', '^[6-9][0-9]{9}$', 50),
    ('CBSE', 'STAFF', 'email', 'email', 'Email', FALSE, 'WARN',
        '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$', 60),
    ('CBSE', 'STAFF', 'joiningDate', 'joiningDate', 'Date of Joining', FALSE, 'WARN', NULL, 70),
    ('CBSE', 'STAFF', 'department', 'department', 'Department', FALSE, 'WARN', NULL, 80)
ON CONFLICT (board_code, entity_type, field_key) DO NOTHING;

INSERT INTO validation_rule
    (board_code, rule_code, entity_type, rule_type, config_json, severity, message_template)
VALUES
    ('CBSE', 'STUDENT_UNIQUE_ADMISSION', 'STUDENT', 'UNIQUE',
        '{"field":"admissionNo"}'::jsonb, 'BLOCKER',
        'Duplicate admission number "{value}".'),
    ('CBSE', 'STUDENT_UNIQUE_PEN', 'STUDENT', 'UNIQUE',
        '{"field":"penNumber","skipBlank":true}'::jsonb, 'BLOCKER',
        'Duplicate PEN "{value}".'),
    ('CBSE', 'STUDENT_AGE_CLASS', 'STUDENT', 'CROSS_FIELD',
        '{"dobField":"dateOfBirth","classField":"classSection"}'::jsonb, 'WARN',
        'Age may not match class for {label}.'),
    ('CBSE', 'STAFF_UNIQUE_EMPLOYEE', 'STAFF', 'UNIQUE',
        '{"field":"employeeNo"}'::jsonb, 'BLOCKER',
        'Duplicate employee number "{value}".')
ON CONFLICT (board_code, rule_code) DO NOTHING;
