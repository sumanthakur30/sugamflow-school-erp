-- Standard package price book and proposal controls.
-- Does not change subscription_plan, plan_price, tenant_subscription, entitlements, or invoices.
-- A new standard price closes the previous open row. Customer proposals live on pricing_quote.

ALTER TABLE feature_rate_version
    ADD COLUMN recommended_discount_bps INT NULL,
    ADD COLUMN max_discount_bps INT NULL,
    ADD COLUMN benchmark_source VARCHAR(160) NULL;

ALTER TABLE feature_rate_version
    ADD CONSTRAINT ck_feature_rate_discount_bps CHECK (
        (recommended_discount_bps IS NULL OR (recommended_discount_bps >= 0 AND recommended_discount_bps <= 10000))
        AND (max_discount_bps IS NULL OR (max_discount_bps >= 0 AND max_discount_bps <= 10000))
    );

CREATE TABLE package_rate_version (
    id                         BIGSERIAL    PRIMARY KEY,
    business_type_code         VARCHAR(64)  NOT NULL REFERENCES business_type(code),
    package_name               VARCHAR(160) NOT NULL,
    unit_model                 VARCHAR(40)  NOT NULL DEFAULT 'PER_ORGANIZATION',
    monthly_amount_minor       BIGINT       NOT NULL DEFAULT 0,
    yearly_amount_minor        BIGINT       NOT NULL DEFAULT 0,
    min_selling_minor          BIGINT       NOT NULL DEFAULT 0,
    gst_inclusive              BOOLEAN      NOT NULL DEFAULT FALSE,
    recommended_discount_bps   INT          NOT NULL DEFAULT 1000,
    max_discount_bps           INT          NOT NULL DEFAULT 2000,
    included_summary           VARCHAR(1000) NULL,
    effective_from             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    effective_until            TIMESTAMPTZ  NULL,
    changed_by                 VARCHAR(128) NULL,
    change_reason              VARCHAR(512) NULL,
    benchmark_low_minor        BIGINT       NULL,
    benchmark_average_minor    BIGINT       NULL,
    benchmark_high_minor       BIGINT       NULL,
    benchmark_source           VARCHAR(160) NULL,
    benchmark_notes            VARCHAR(512) NULL,
    benchmark_reviewed_on      DATE         NULL,
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_package_rate_amounts CHECK (
        monthly_amount_minor >= 0 AND yearly_amount_minor >= 0 AND min_selling_minor >= 0
    ),
    CONSTRAINT ck_package_rate_window CHECK (effective_until IS NULL OR effective_until >= effective_from),
    CONSTRAINT ck_package_rate_discount CHECK (
        recommended_discount_bps >= 0 AND recommended_discount_bps <= 10000
        AND max_discount_bps >= 0 AND max_discount_bps <= 10000
    ),
    CONSTRAINT ck_package_rate_unit CHECK (unit_model IN (
        'PER_ORGANIZATION', 'PER_OUTLET', 'PER_USER', 'PER_EMPLOYEE', 'PER_WAREHOUSE',
        'PER_LOCATION', 'PER_CHANNEL', 'PER_TRANSACTION', 'USAGE', 'ONE_TIME', 'ANNUAL',
        'PER_STUDENT'
    ))
);

CREATE UNIQUE INDEX uq_package_rate_open
    ON package_rate_version (business_type_code)
    WHERE effective_until IS NULL;

CREATE INDEX idx_package_rate_type ON package_rate_version (business_type_code, effective_from DESC);

ALTER TABLE pricing_quote
    ADD COLUMN input_mode VARCHAR(16) NOT NULL DEFAULT 'DISCOUNT',
    ADD COLUMN recommended_discount_bps INT NULL,
    ADD COLUMN max_discount_bps INT NULL,
    ADD COLUMN approval_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN discount_amount_minor BIGINT NULL,
    ADD COLUMN gst_amount_minor BIGINT NULL;

ALTER TABLE pricing_quote
    ADD CONSTRAINT ck_pricing_quote_input CHECK (input_mode IN ('DISCOUNT', 'AMOUNT'));

-- Initial standard book for business types that already exist. Internal benchmarks are not customer prices.
-- Amounts are paise, GST exclusive. Yearly is the annual standard, not monthly x 12.
INSERT INTO package_rate_version (
    business_type_code, package_name, unit_model,
    monthly_amount_minor, yearly_amount_minor, min_selling_minor,
    recommended_discount_bps, max_discount_bps, included_summary,
    benchmark_low_minor, benchmark_average_minor, benchmark_high_minor,
    benchmark_source, benchmark_notes, benchmark_reviewed_on,
    changed_by, change_reason
)
SELECT v.business_type_code, v.package_name, v.unit_model,
       v.monthly_amount_minor, v.yearly_amount_minor, v.min_selling_minor,
       v.recommended_discount_bps, v.max_discount_bps, v.included_summary,
       v.benchmark_low_minor, v.benchmark_average_minor, v.benchmark_high_minor,
       'Indian SaaS benchmark, Oct 2026 (internal)',
       v.benchmark_notes,
       DATE '2026-10-07',
       'SYSTEM',
       'Initial standard price book'
FROM (VALUES
    ('RETAIL', 'Retail ERP', 'PER_ORGANIZATION', 249900::bigint, 2499900::bigint, 0::bigint, 1000, 2000,
     'POS, inventory, purchase, customers, GST reports',
     89900::bigint, 199900::bigint, 499900::bigint,
     'Entry billing apps publish lower monthly fees. Multi-store POS and inventory suites sit higher. This standard leaves room to discount.'),
    ('MEDICAL_STORE', 'Medical Shop ERP', 'PER_ORGANIZATION', 199900, 1999900, 0, 1000, 2000,
     'Retail billing, stock, purchase, GST',
     69900, 149900, 299900,
     'Single-counter medical retail is priced below a full pharmacy with batch and expiry control.'),
    ('PHARMACY', 'Pharmacy ERP', 'PER_ORGANIZATION', 349900, 3499900, 0, 1000, 2000,
     'Sales, batch and expiry stock, purchase, GST, suppliers, reports',
     149900, 299900, 599900,
     'Pharmacy suites with batch, expiry, and purchase sit above plain billing apps.'),
    ('MEDICAL_DISTRIBUTOR', 'Distribution ERP', 'PER_ORGANIZATION', 399900, 3999900, 0, 1000, 1500,
     'Distribution, inventory, purchase, sales, GST, warehouse, credit, reports',
     249900, 399900, 799900,
     'Wholesale and distribution pricing is above single-store retail because of credit and warehouse depth.'),
    ('POLYCLINIC', 'Polyclinic ERP', 'PER_ORGANIZATION', 699900, 6999900, 0, 1000, 1500,
     'OPD, doctor, patient, pharmacy, pathology, billing, reports',
     299900, 599900, 1499900,
     'Integrated clinic plus pharmacy plus lab is priced as one campus, not three cheap products.'),
    ('DENTAL_CLINIC', 'Dental Clinic ERP', 'PER_ORGANIZATION', 249900, 2499900, 0, 1000, 2000,
     'Chair-side billing, patients, appointments, reports',
     99900, 199900, 499900,
     'Single-practice clinic pricing, below a multi-doctor polyclinic.'),
    ('PATHLAB', 'Lab ERP', 'PER_ORGANIZATION', 499900, 4999900, 0, 1000, 1500,
     'Registration, samples, results, billing, reports',
     199900, 399900, 999900,
     'LIS products are priced above retail POS because of sample and result workflow.'),
    ('DIAGNOSTIC_CENTER', 'Diagnostics ERP', 'PER_ORGANIZATION', 599900, 5999900, 0, 800, 1500,
     'Multi-modality orders, billing, reports',
     299900, 499900, 1199900,
     'Multi-modality diagnostics sit above a single pathology lab.'),
    ('RADIOLOGY', 'Radiology ERP', 'PER_ORGANIZATION', 449900, 4499900, 0, 800, 1500,
     'Imaging orders, billing, reports',
     199900, 349900, 899900,
     'Imaging-center pricing, between a clinic and a full hospital.'),
    ('HOSPITAL', 'Hospital ERP', 'PER_ORGANIZATION', 999900, 9999900, 0, 500, 1500,
     'Billing, lab, pharmacy, and hospital operations',
     499900, 999900, 2499900,
     'Hospital suites are the top of this book. Discount room is tighter than retail.'),
    ('IVF_CENTER', 'IVF Center ERP', 'PER_ORGANIZATION', 599900, 5999900, 0, 800, 1500,
     'Cycle billing, patients, reports',
     299900, 499900, 999900,
     'Specialty-center pricing, aligned with diagnostics rather than a single clinic.'),
    ('BLOOD_BANK', 'Blood Bank ERP', 'PER_ORGANIZATION', 349900, 3499900, 0, 800, 1500,
     'Inventory, issue, billing, reports',
     149900, 299900, 699900,
     'Specialist operations, priced with pharmacy rather than a general store.'),
    ('SCHOOL', 'School ERP', 'PER_STUDENT', 2500, 25000, 1500000, 1000, 2000,
     'Admission, students, attendance, fees, exams, library, hostel, transport, reports',
     1500, 2500, 4000,
     'Per student. Indian school ERPs commonly publish about Rs 15 to Rs 40 per student per month, with flat annual campus plans as well. Minimum annual organization package is Rs 15,000.'),
    ('CRM', 'CRM', 'PER_ORGANIZATION', 199900, 1999900, 0, 1000, 2000,
     'Leads, pipeline, quotes, campaigns',
     99900, 199900, 499900,
     'Standalone CRM, below a full retail or distribution ERP.'),
    ('JYOTISH', 'Jyotish Desk', 'PER_ORGANIZATION', 149900, 1499900, 0, 1000, 2000,
     'Kundali, matching, reports',
     49900, 99900, 249900,
     'Specialist desk, below a multi-module ERP.'),
    ('CUSTOM', 'Business ERP', 'PER_ORGANIZATION', 299900, 2999900, 0, 1000, 2000,
     'Configured modules for a custom vertical',
     99900, 249900, 599900,
     'Starting standard for a tenant-defined vertical. Edit this row; do not copy it onto another business type.')
) AS v(
    business_type_code, package_name, unit_model,
    monthly_amount_minor, yearly_amount_minor, min_selling_minor,
    recommended_discount_bps, max_discount_bps, included_summary,
    benchmark_low_minor, benchmark_average_minor, benchmark_high_minor,
    benchmark_notes
)
WHERE EXISTS (SELECT 1 FROM business_type b WHERE b.code = v.business_type_code)
ON CONFLICT (business_type_code) WHERE effective_until IS NULL DO NOTHING;
