-- Phase 2.3: shop vertical catalog parity (HOSPITAL / POLY / PATHLAB / PHARMACY / RETAIL).
-- Additive only — does not modify School starter/basic/... plan JSON.

-- ---------------------------------------------------------------------------
-- Feature definitions (link plan flags → module_definition for projection)
-- ---------------------------------------------------------------------------
INSERT INTO feature_definition (code, module_code, name, description, sort_order) VALUES
    ('HOSPITAL_BILLING', 'HOSPITAL_BILLING', 'Hospital billing', 'Hospital billing module', 10),
    ('HOSPITAL_LAB', 'HOSPITAL_LAB', 'Hospital lab', 'In-house laboratory', 10),
    ('HOSPITAL_PHARMACY', 'HOSPITAL_PHARMACY', 'Hospital pharmacy', 'Hospital pharmacy', 10),
    ('POLY_OPD', 'POLY_OPD', 'Polyclinic OPD', 'Clinic OPD', 10),
    ('POLY_PHARMACY', 'POLY_PHARMACY', 'Polyclinic pharmacy', 'Clinic pharmacy', 10),
    ('POLY_LAB', 'POLY_LAB', 'Polyclinic lab', 'Clinic lab', 10),
    ('PATHLAB_REGISTRATION', 'PATHLAB_REGISTRATION', 'PathLab registration', 'Test booking', 10),
    ('PATHLAB_SAMPLE', 'PATHLAB_SAMPLE', 'PathLab sample', 'Collection / barcode', 10),
    ('PATHLAB_RESULTS', 'PATHLAB_RESULTS', 'PathLab results', 'Results entry', 10),
    ('PATHLAB_BILLING', 'PATHLAB_BILLING', 'PathLab billing', 'Lab billing', 10),
    ('PHARMACY_SALES', 'PHARMACY_SALES', 'Pharmacy sales', 'Retail pharmacy sales', 10),
    ('PHARMACY_STOCK', 'PHARMACY_STOCK', 'Pharmacy stock', 'Batch / expiry stock', 10)
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- RETAIL modules + features (type already exists in V3)
-- ---------------------------------------------------------------------------
INSERT INTO module_definition (code, business_type_code, name, description, sort_order) VALUES
    ('RETAIL_POS', 'RETAIL', 'POS / Billing', 'Point of sale and invoices', 10),
    ('RETAIL_INVENTORY', 'RETAIL', 'Inventory', 'Stock and products', 20),
    ('RETAIL_PURCHASE', 'RETAIL', 'Purchase', 'Purchase and suppliers', 30),
    ('RETAIL_CUSTOMERS', 'RETAIL', 'Customers', 'Customer master', 40),
    ('RETAIL_REPORTS', 'RETAIL', 'Reports', 'Sales and GST reports', 50)
ON CONFLICT (code) DO NOTHING;

INSERT INTO feature_definition (code, module_code, name, description, sort_order) VALUES
    ('RETAIL_POS', 'RETAIL_POS', 'Retail POS', 'Billing / POS', 10),
    ('RETAIL_INVENTORY', 'RETAIL_INVENTORY', 'Retail inventory', 'Products and stock', 10),
    ('RETAIL_PURCHASE', 'RETAIL_PURCHASE', 'Retail purchase', 'PO / suppliers', 10),
    ('RETAIL_CUSTOMERS', 'RETAIL_CUSTOMERS', 'Retail customers', 'Customer master', 10),
    ('RETAIL_REPORTS', 'RETAIL_REPORTS', 'Retail reports', 'Reports pack', 10)
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Sellable vertical plans (shop-service bridge assigns by shopId as X-Tenant-Id)
-- ---------------------------------------------------------------------------
INSERT INTO subscription_plan (id, code, name, plan_type, active, limits_json, feature_flags_json, updated_at)
VALUES (
    'hospital-starter',
    'HOSPITAL_STARTER',
    'Hospital Starter',
    'HOSPITAL_STARTER',
    TRUE,
    '{"maxUsers": 25, "maxBranches": 1, "maxStorageGb": 10, "maxApiCalls": 50000}',
    '{
      "HOSPITAL_OPD": true,
      "HOSPITAL_BILLING": true,
      "HOSPITAL_LAB": false,
      "HOSPITAL_PHARMACY": false,
      "HOSPITAL_IPD": false
    }',
    NOW()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO subscription_plan (id, code, name, plan_type, active, limits_json, feature_flags_json, updated_at)
VALUES (
    'hospital-pro',
    'HOSPITAL_PRO',
    'Hospital Professional',
    'HOSPITAL_PRO',
    TRUE,
    '{"maxUsers": 100, "maxBranches": 5, "maxStorageGb": 50, "maxApiCalls": 200000}',
    '{
      "HOSPITAL_OPD": true,
      "HOSPITAL_IPD": true,
      "HOSPITAL_BILLING": true,
      "HOSPITAL_LAB": true,
      "HOSPITAL_PHARMACY": true
    }',
    NOW()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO subscription_plan (id, code, name, plan_type, active, limits_json, feature_flags_json, updated_at)
VALUES (
    'poly-starter',
    'POLY_STARTER',
    'Polyclinic Starter',
    'POLY_STARTER',
    TRUE,
    '{"maxUsers": 30, "maxBranches": 2, "maxStorageGb": 15, "maxApiCalls": 80000}',
    '{
      "POLY_OPD": true,
      "POLY_PHARMACY": true,
      "POLY_LAB": true
    }',
    NOW()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO subscription_plan (id, code, name, plan_type, active, limits_json, feature_flags_json, updated_at)
VALUES (
    'pharmacy-starter',
    'PHARMACY_STARTER',
    'Pharmacy Starter',
    'PHARMACY_STARTER',
    TRUE,
    '{"maxUsers": 15, "maxBranches": 2, "maxStorageGb": 10, "maxApiCalls": 50000}',
    '{
      "PHARMACY_SALES": true,
      "PHARMACY_PURCHASE": true,
      "PHARMACY_STOCK": true,
      "PHARMACY_PO": true
    }',
    NOW()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO subscription_plan (id, code, name, plan_type, active, limits_json, feature_flags_json, updated_at)
VALUES (
    'pathlab-starter',
    'PATHLAB_STARTER',
    'PathLab Starter',
    'PATHLAB_STARTER',
    TRUE,
    '{"maxUsers": 20, "maxBranches": 2, "maxStorageGb": 20, "maxApiCalls": 80000}',
    '{
      "PATHLAB_REGISTRATION": true,
      "PATHLAB_SAMPLE": true,
      "PATHLAB_RESULTS": true,
      "PATHLAB_BILLING": true,
      "PATHLAB_BARCODE": true
    }',
    NOW()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO subscription_plan (id, code, name, plan_type, active, limits_json, feature_flags_json, updated_at)
VALUES (
    'retail-starter',
    'RETAIL_STARTER',
    'Retail Starter',
    'RETAIL_STARTER',
    TRUE,
    '{"maxUsers": 20, "maxBranches": 3, "maxStorageGb": 10, "maxApiCalls": 50000}',
    '{
      "RETAIL_POS": true,
      "RETAIL_INVENTORY": true,
      "RETAIL_PURCHASE": true,
      "RETAIL_CUSTOMERS": true,
      "RETAIL_REPORTS": true
    }',
    NOW()
)
ON CONFLICT (id) DO NOTHING;
