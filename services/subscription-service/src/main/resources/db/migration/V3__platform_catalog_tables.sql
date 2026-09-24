-- Phase 1: additive platform catalog (business types, modules, features, limits).
-- Does NOT alter subscription_plan / tenant_subscription or entitlement resolution.

CREATE TABLE business_type (
    code            VARCHAR(64)  PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order      INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE module_definition (
    code                 VARCHAR(64)  PRIMARY KEY,
    business_type_code   VARCHAR(64)  REFERENCES business_type(code),
    name                 VARCHAR(128) NOT NULL,
    description          VARCHAR(512),
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order           INT          NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_module_definition_business_type ON module_definition (business_type_code);

CREATE TABLE feature_definition (
    code            VARCHAR(96)  PRIMARY KEY,
    module_code     VARCHAR(64)  NOT NULL REFERENCES module_definition(code),
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order      INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_feature_definition_module ON feature_definition (module_code);

CREATE TABLE limit_definition (
    code                 VARCHAR(64)  PRIMARY KEY,
    business_type_code   VARCHAR(64)  REFERENCES business_type(code),
    name                 VARCHAR(128) NOT NULL,
    unit                 VARCHAR(64)  NOT NULL DEFAULT 'COUNT',
    aggregation          VARCHAR(32)  NOT NULL DEFAULT 'NUMERIC',
    description          VARCHAR(512),
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order           INT          NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_limit_definition_business_type ON limit_definition (business_type_code);

-- ---------------------------------------------------------------------------
-- Seed: business types (config-driven; no Java hardcoding required to add more)
-- ---------------------------------------------------------------------------
INSERT INTO business_type (code, name, description, sort_order) VALUES
    ('SCHOOL', 'School ERP', 'K-12 / campus education management', 10),
    ('HOSPITAL', 'Hospital', 'Multi-specialty hospital', 20),
    ('POLYCLINIC', 'Polyclinic', 'OPD / clinic polyclinic', 30),
    ('PATHLAB', 'Pathology Lab', 'Diagnostic pathology laboratory', 40),
    ('DIAGNOSTIC_CENTER', 'Diagnostic Center', 'Multi-modality diagnostics', 50),
    ('PHARMACY', 'Pharmacy', 'Retail / hospital pharmacy', 60),
    ('MEDICAL_STORE', 'Medical Shop', 'Medical retail store', 70),
    ('MEDICAL_DISTRIBUTOR', 'Medical Distributor', 'Pharma / medical wholesale', 80),
    ('DENTAL_CLINIC', 'Dental Clinic', 'Dental practice', 90),
    ('IVF_CENTER', 'IVF Center', 'Fertility / IVF center', 100),
    ('RADIOLOGY', 'Radiology', 'Imaging / radiology center', 110),
    ('BLOOD_BANK', 'Blood Bank', 'Blood bank operations', 120),
    ('RETAIL', 'Retail', 'General retail', 130),
    ('CUSTOM', 'Custom', 'Tenant-defined vertical', 999)
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- SCHOOL modules (mirror existing School ERP feature domains)
-- ---------------------------------------------------------------------------
INSERT INTO module_definition (code, business_type_code, name, description, sort_order) VALUES
    ('SCHOOL_CORE', 'SCHOOL', 'Core Administration', 'Admin, branding, forms, workflows', 10),
    ('SCHOOL_ACADEMIC', 'SCHOOL', 'Academic', 'Classes, sessions, subjects, exams', 20),
    ('SCHOOL_ADMISSION', 'SCHOOL', 'Admission', 'Admissions and enrollment', 30),
    ('SCHOOL_STUDENT', 'SCHOOL', 'Student Master', 'Student records', 40),
    ('SCHOOL_STAFF', 'SCHOOL', 'Staff & HR', 'Staff master, payroll, HR', 50),
    ('SCHOOL_FEE', 'SCHOOL', 'Fee', 'Fee collection and billing', 60),
    ('SCHOOL_ATTENDANCE', 'SCHOOL', 'Attendance', 'Attendance and devices', 70),
    ('SCHOOL_EXAM', 'SCHOOL', 'Examinations', 'Exams and marks', 80),
    ('SCHOOL_LIBRARY', 'SCHOOL', 'Library', 'Library operations', 90),
    ('SCHOOL_HOSTEL', 'SCHOOL', 'Hostel', 'Hostel allocation', 100),
    ('SCHOOL_TRANSPORT', 'SCHOOL', 'Transport', 'Transport routes', 110),
    ('SCHOOL_COMMS', 'SCHOOL', 'Communications', 'Parent/teacher apps and hub', 120),
    ('SCHOOL_REPORTS', 'SCHOOL', 'Reports & Analytics', 'Report builder and AI', 130),
    ('SCHOOL_PLATFORM', 'SCHOOL', 'Platform', 'API, white-label, multi-branch', 140)
ON CONFLICT (code) DO NOTHING;

-- SCHOOL features: codes MUST match existing FEATURE_* keys used by School UI/services
INSERT INTO feature_definition (code, module_code, name, sort_order) VALUES
    ('FEATURE_ADMIN_CONFIG', 'SCHOOL_CORE', 'Admin configuration', 10),
    ('FEATURE_FORM_BUILDER', 'SCHOOL_CORE', 'Form builder', 20),
    ('FEATURE_WORKFLOW_BUILDER', 'SCHOOL_CORE', 'Workflow builder', 30),
    ('FEATURE_RULE_ENGINE', 'SCHOOL_CORE', 'Rule engine', 40),
    ('FEATURE_DOCUMENT_STORAGE', 'SCHOOL_CORE', 'Document storage', 50),
    ('FEATURE_CLOUD_BACKUP', 'SCHOOL_CORE', 'Cloud backup', 60),
    ('FEATURE_CUSTOM_BRANDING', 'SCHOOL_CORE', 'Custom branding', 70),
    ('FEATURE_CUSTOM_DOMAIN', 'SCHOOL_CORE', 'Custom domain', 80),
    ('FEATURE_DIGITAL_SIGNATURE', 'SCHOOL_CORE', 'Digital signature', 90),
    ('FEATURE_APPROVAL_WORKFLOW', 'SCHOOL_CORE', 'Approval workflow', 100),
    ('FEATURE_AUDIT_LOGS', 'SCHOOL_CORE', 'Audit logs', 110),
    ('FEATURE_IMPORT_WORKBENCH', 'SCHOOL_CORE', 'Import workbench', 120),
    ('FEATURE_ACADEMIC_LIFECYCLE', 'SCHOOL_ACADEMIC', 'Academic lifecycle', 10),
    ('FEATURE_OPS_DEPTH', 'SCHOOL_ACADEMIC', 'Operations depth', 20),
    ('FEATURE_LMS', 'SCHOOL_ACADEMIC', 'LMS', 30),
    ('FEATURE_ADMISSION', 'SCHOOL_ADMISSION', 'Admission', 10),
    ('FEATURE_STUDENT_MASTER', 'SCHOOL_STUDENT', 'Student master', 10),
    ('FEATURE_STAFF_MASTER', 'SCHOOL_STAFF', 'Staff master', 10),
    ('FEATURE_PAYROLL', 'SCHOOL_STAFF', 'Payroll', 20),
    ('FEATURE_HR', 'SCHOOL_STAFF', 'HR', 30),
    ('FEATURE_ROLE_LIMITS', 'SCHOOL_STAFF', 'Role limits', 40),
    ('FEATURE_FEE', 'SCHOOL_FEE', 'Fee collection', 10),
    ('FEATURE_MULTI_PAYMENT_GATEWAY', 'SCHOOL_FEE', 'Multi payment gateway', 20),
    ('FEATURE_ATTENDANCE', 'SCHOOL_ATTENDANCE', 'Attendance', 10),
    ('FEATURE_BIOMETRIC', 'SCHOOL_ATTENDANCE', 'Biometric', 20),
    ('FEATURE_FACE_RECOGNITION', 'SCHOOL_ATTENDANCE', 'Face recognition', 30),
    ('FEATURE_GPS', 'SCHOOL_ATTENDANCE', 'GPS tracking', 40),
    ('FEATURE_OFFLINE_MODE', 'SCHOOL_ATTENDANCE', 'Offline mode', 50),
    ('FEATURE_EXAM', 'SCHOOL_EXAM', 'Examinations', 10),
    ('FEATURE_LIBRARY', 'SCHOOL_LIBRARY', 'Library', 10),
    ('FEATURE_HOSTEL', 'SCHOOL_HOSTEL', 'Hostel', 10),
    ('FEATURE_TRANSPORT', 'SCHOOL_TRANSPORT', 'Transport', 10),
    ('FEATURE_PARENT_APP', 'SCHOOL_COMMS', 'Parent app', 10),
    ('FEATURE_TEACHER_APP', 'SCHOOL_COMMS', 'Teacher app', 20),
    ('FEATURE_STUDENT_APP', 'SCHOOL_COMMS', 'Student app', 30),
    ('FEATURE_COMMS_HUB', 'SCHOOL_COMMS', 'Communications hub', 40),
    ('FEATURE_VISITOR', 'SCHOOL_COMMS', 'Visitor management', 50),
    ('FEATURE_REPORT_BUILDER', 'SCHOOL_REPORTS', 'Report builder', 10),
    ('FEATURE_AI', 'SCHOOL_REPORTS', 'AI features', 20),
    ('FEATURE_ACCOUNTING', 'SCHOOL_REPORTS', 'Accounting', 30),
    ('FEATURE_INVENTORY', 'SCHOOL_REPORTS', 'Inventory', 40),
    ('FEATURE_MULTI_BRANCH', 'SCHOOL_PLATFORM', 'Multi branch', 10),
    ('FEATURE_API_ACCESS', 'SCHOOL_PLATFORM', 'API access', 20),
    ('FEATURE_WHITE_LABEL', 'SCHOOL_PLATFORM', 'White label', 30)
ON CONFLICT (code) DO NOTHING;

INSERT INTO limit_definition (code, business_type_code, name, unit, aggregation, description, sort_order) VALUES
    ('maxStudents', 'SCHOOL', 'Maximum students', 'COUNT', 'NUMERIC', 'Active student records', 10),
    ('maxTeachers', 'SCHOOL', 'Maximum teachers', 'COUNT', 'NUMERIC', 'Teaching staff', 20),
    ('maxBranches', 'SCHOOL', 'Maximum branches / campuses', 'COUNT', 'NUMERIC', '-1 means unlimited', 30),
    ('maxUsers', 'SCHOOL', 'Maximum users', 'COUNT', 'NUMERIC', 'Login seats', 40),
    ('maxStorageGb', 'SCHOOL', 'Storage', 'GB', 'NUMERIC', 'Document / media storage', 50),
    ('maxApiCalls', 'SCHOOL', 'API calls', 'COUNT', 'MONTHLY', 'Monthly API call budget', 60),
    ('maxSms', 'SCHOOL', 'SMS credits', 'COUNT', 'MONTHLY', 'Monthly SMS', 70),
    ('maxWhatsApp', 'SCHOOL', 'WhatsApp credits', 'COUNT', 'MONTHLY', 'Monthly WhatsApp', 80),
    ('maxEmails', 'SCHOOL', 'Email credits', 'COUNT', 'MONTHLY', 'Monthly emails', 90),
    ('maxReports', 'SCHOOL', 'Custom reports', 'COUNT', 'NUMERIC', 'Saved report definitions', 100),
    ('maxCustomFields', 'SCHOOL', 'Custom fields', 'COUNT', 'NUMERIC', 'Form custom fields', 110),
    ('maxSubjects', 'SCHOOL', 'Subjects', 'COUNT', 'NUMERIC', 'Subject master', 120),
    ('maxSections', 'SCHOOL', 'Sections', 'COUNT', 'NUMERIC', 'Class sections', 130),
    ('maxClasses', 'SCHOOL', 'Classes', 'COUNT', 'NUMERIC', 'Class groups', 140),
    ('maxSessions', 'SCHOOL', 'Academic sessions', 'COUNT', 'NUMERIC', 'Concurrent sessions', 150),
    ('aiUsageUnits', 'SCHOOL', 'AI usage units', 'COUNT', 'MONTHLY', 'AI quota', 160)
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Placeholder modules for other verticals (features can be added via admin later)
-- ---------------------------------------------------------------------------
INSERT INTO module_definition (code, business_type_code, name, description, sort_order) VALUES
    ('HOSPITAL_OPD', 'HOSPITAL', 'OPD', 'Outpatient', 10),
    ('HOSPITAL_IPD', 'HOSPITAL', 'IPD', 'Inpatient / wards', 20),
    ('HOSPITAL_BILLING', 'HOSPITAL', 'Billing', 'Hospital billing', 30),
    ('HOSPITAL_LAB', 'HOSPITAL', 'Laboratory', 'In-house lab', 40),
    ('HOSPITAL_PHARMACY', 'HOSPITAL', 'Pharmacy', 'Hospital pharmacy', 50),
    ('POLY_OPD', 'POLYCLINIC', 'OPD', 'Clinic OPD', 10),
    ('POLY_PHARMACY', 'POLYCLINIC', 'Pharmacy', 'Clinic pharmacy', 20),
    ('POLY_LAB', 'POLYCLINIC', 'Laboratory', 'Clinic lab', 30),
    ('PATHLAB_REGISTRATION', 'PATHLAB', 'Registration', 'Test booking / registration', 10),
    ('PATHLAB_SAMPLE', 'PATHLAB', 'Sample', 'Collection and barcode', 20),
    ('PATHLAB_RESULTS', 'PATHLAB', 'Results', 'Entry and approval', 30),
    ('PATHLAB_BILLING', 'PATHLAB', 'Billing', 'Lab billing', 40),
    ('PHARMACY_SALES', 'PHARMACY', 'Sales', 'Retail sales', 10),
    ('PHARMACY_PURCHASE', 'PHARMACY', 'Purchase', 'Purchase and PO', 20),
    ('PHARMACY_STOCK', 'PHARMACY', 'Stock', 'Batch / expiry stock', 30),
    ('MEDSHOP_SALES', 'MEDICAL_STORE', 'Sales', 'Medical shop sales', 10),
    ('MEDSHOP_PURCHASE', 'MEDICAL_STORE', 'Purchase', 'Purchase', 20),
    ('MEDSHOP_STOCK', 'MEDICAL_STORE', 'Stock', 'Inventory', 30)
ON CONFLICT (code) DO NOTHING;

INSERT INTO feature_definition (code, module_code, name, sort_order) VALUES
    ('HOSPITAL_OPD', 'HOSPITAL_OPD', 'Hospital OPD', 10),
    ('HOSPITAL_IPD', 'HOSPITAL_IPD', 'Hospital IPD', 10),
    ('PATHLAB_BARCODE', 'PATHLAB_SAMPLE', 'PathLab barcode', 10),
    ('PATHLAB_MACHINE', 'PATHLAB_RESULTS', 'Machine integration', 10),
    ('PHARMACY_PURCHASE', 'PHARMACY_PURCHASE', 'Pharmacy purchase', 10),
    ('PHARMACY_PO', 'PHARMACY_PURCHASE', 'Purchase orders', 20),
    ('MEDICALSHOP_BATCH', 'MEDSHOP_STOCK', 'Batch / expiry tracking', 10)
ON CONFLICT (code) DO NOTHING;
