-- Comprehensive Feature Catalog metadata + Retail/common seeds.
-- Additive only. Does not rewrite School / CRM / existing plan JSON.

ALTER TABLE feature_definition
    ADD COLUMN IF NOT EXISTS category VARCHAR(32) NOT NULL DEFAULT 'OTHER',
    ADD COLUMN IF NOT EXISTS availability VARCHAR(24) NOT NULL DEFAULT 'BASE',
    ADD COLUMN IF NOT EXISTS billing_type VARCHAR(24) NOT NULL DEFAULT 'FREE',
    ADD COLUMN IF NOT EXISTS price_minor INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS tier VARCHAR(24) NOT NULL DEFAULT 'BASIC',
    ADD COLUMN IF NOT EXISTS is_addon BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS dependencies_json VARCHAR(512) NOT NULL DEFAULT '[]';

CREATE TABLE IF NOT EXISTS feature_business_type (
    feature_code VARCHAR(96) NOT NULL REFERENCES feature_definition (code) ON DELETE CASCADE,
    business_type_code VARCHAR(64) NOT NULL REFERENCES business_type (code) ON DELETE CASCADE,
    PRIMARY KEY (feature_code, business_type_code)
);

INSERT INTO business_type (code, name, description, sort_order) VALUES
    ('PLATFORM', 'Platform common', 'Shared finance, comms, AI, security', 5)
ON CONFLICT (code) DO NOTHING;

INSERT INTO module_definition (code, business_type_code, name, description, sort_order) VALUES
    ('RETAIL_OPERATIONS', 'RETAIL', 'Retail operations', 'Core retail POS / stock / sales', 12),
    ('PLATFORM_FINANCE', 'PLATFORM', 'Finance', 'Income, expense, cashbook, GST', 10),
    ('PLATFORM_COMMS', 'PLATFORM', 'Communication & WhatsApp', 'WhatsApp, SMS, email', 20),
    ('PLATFORM_CRM', 'PLATFORM', 'CRM', 'Customers, loyalty, campaigns', 30),
    ('PLATFORM_REPORTS', 'PLATFORM', 'Reports & analytics', 'Dashboards and exports', 40),
    ('PLATFORM_AI', 'PLATFORM', 'AI & automation', 'AI assistants and OCR', 50),
    ('PLATFORM_INTEGRATIONS', 'PLATFORM', 'Integrations', 'Gateways and APIs', 60),
    ('PLATFORM_ENTERPRISE', 'PLATFORM', 'Enterprise', 'Multi-branch and approvals', 70),
    ('PLATFORM_SECURITY', 'PLATFORM', 'Admin & security', 'Roles, audit, permissions', 80)
ON CONFLICT (code) DO NOTHING;

-- Existing Retail five: keep codes, attach metadata
UPDATE feature_definition SET
    category = 'RETAIL_OPS', availability = 'BASE', billing_type = 'FREE', tier = 'BASIC', is_addon = FALSE,
    description = COALESCE(description, name)
WHERE code IN ('RETAIL_POS', 'RETAIL_INVENTORY', 'RETAIL_PURCHASE', 'RETAIL_CUSTOMERS', 'RETAIL_REPORTS');

-- Retail operations (new codes; existing five stay)
INSERT INTO feature_definition (code, module_code, name, description, sort_order, category, availability, billing_type, price_minor, tier, is_addon, dependencies_json) VALUES
    ('RETAIL_SALES', 'RETAIL_OPERATIONS', 'Retail Sales', 'Sales desk beyond POS checkout', 20, 'RETAIL_OPS', 'BASE', 'FREE', 0, 'BASIC', FALSE, '["RETAIL_POS"]'),
    ('RETAIL_SUPPLIERS', 'RETAIL_OPERATIONS', 'Retail Suppliers', 'Supplier master for purchase', 30, 'RETAIL_OPS', 'BASE', 'FREE', 0, 'BASIC', FALSE, '["RETAIL_PURCHASE"]'),
    ('RETAIL_PRODUCT_CATALOG', 'RETAIL_OPERATIONS', 'Retail Product Catalog', 'SKU, variants, pricing', 40, 'RETAIL_OPS', 'BASE', 'FREE', 0, 'BASIC', FALSE, '["RETAIL_INVENTORY"]'),
    ('RETAIL_BARCODE', 'RETAIL_OPERATIONS', 'Barcode / QR Code', 'Scan and generate barcodes', 50, 'RETAIL_OPS', 'ADDON', 'MONTHLY', 14900, 'BASIC', TRUE, '["RETAIL_INVENTORY"]'),
    ('RETAIL_LABEL_PRINT', 'RETAIL_OPERATIONS', 'Barcode & Label Printing', 'Shelf and barcode labels', 60, 'RETAIL_OPS', 'ADDON', 'MONTHLY', 19900, 'BASIC', TRUE, '["RETAIL_BARCODE"]'),
    ('RETAIL_STOCK_ADJUSTMENT', 'RETAIL_OPERATIONS', 'Stock Adjustment', 'Manual stock corrections', 70, 'RETAIL_OPS', 'BASE', 'FREE', 0, 'BASIC', FALSE, '["RETAIL_INVENTORY"]'),
    ('RETAIL_STOCK_TRANSFER', 'RETAIL_OPERATIONS', 'Stock Transfer', 'Move stock between locations', 80, 'RETAIL_OPS', 'ADDON', 'MONTHLY', 29900, 'PREMIUM', TRUE, '["RETAIL_INVENTORY"]'),
    ('RETAIL_STOCK_ALERTS', 'RETAIL_OPERATIONS', 'Stock Alerts', 'Threshold notifications', 90, 'RETAIL_OPS', 'BASE', 'FREE', 0, 'BASIC', FALSE, '["RETAIL_INVENTORY"]'),
    ('RETAIL_LOW_STOCK_ALERTS', 'RETAIL_OPERATIONS', 'Low Stock Alerts', 'Reorder reminders', 100, 'RETAIL_OPS', 'BASE', 'FREE', 0, 'BASIC', FALSE, '["RETAIL_STOCK_ALERTS"]'),
    ('RETAIL_EXPIRY', 'RETAIL_OPERATIONS', 'Expiry Management', 'Near-expiry tracking', 110, 'RETAIL_OPS', 'ADDON', 'MONTHLY', 19900, 'BASIC', TRUE, '["RETAIL_INVENTORY"]'),
    ('RETAIL_BATCH', 'RETAIL_OPERATIONS', 'Batch Management', 'Batch / lot tracking', 120, 'RETAIL_OPS', 'ADDON', 'MONTHLY', 19900, 'BASIC', TRUE, '["RETAIL_INVENTORY"]'),
    ('RETAIL_PURCHASE_ORDERS', 'RETAIL_OPERATIONS', 'Purchase Orders', 'PO workflow', 130, 'RETAIL_OPS', 'BASE', 'FREE', 0, 'BASIC', FALSE, '["RETAIL_PURCHASE"]'),
    ('RETAIL_SUPPLIER_PRICE_COMPARE', 'RETAIL_OPERATIONS', 'Supplier Price Comparison', 'Compare supplier quotes', 140, 'RETAIL_OPS', 'ADDON', 'MONTHLY', 24900, 'PREMIUM', TRUE, '["RETAIL_SUPPLIERS"]'),
    ('RETAIL_CUSTOMER_CREDIT', 'RETAIL_OPERATIONS', 'Customer Credit / Due', 'Credit sales and due', 150, 'RETAIL_OPS', 'BASE', 'FREE', 0, 'BASIC', FALSE, '["RETAIL_CUSTOMERS"]'),
    ('RETAIL_RETURNS', 'RETAIL_OPERATIONS', 'Returns & Refunds', 'Sales returns', 160, 'RETAIL_OPS', 'BASE', 'FREE', 0, 'BASIC', FALSE, '["RETAIL_POS"]'),
    ('RETAIL_DISCOUNTS', 'RETAIL_OPERATIONS', 'Discounts & Offers', 'Bill and item discounts', 170, 'RETAIL_OPS', 'BASE', 'FREE', 0, 'BASIC', FALSE, '["RETAIL_POS"]'),
    ('RETAIL_LOYALTY', 'RETAIL_OPERATIONS', 'Loyalty / Rewards', 'Points and rewards', 180, 'RETAIL_OPS', 'ADDON', 'MONTHLY', 39900, 'PREMIUM', TRUE, '["RETAIL_CUSTOMERS"]'),
    ('RETAIL_CASH_REGISTER', 'RETAIL_OPERATIONS', 'Cash Register', 'Till / register', 190, 'RETAIL_OPS', 'BASE', 'FREE', 0, 'BASIC', FALSE, '["RETAIL_POS"]'),
    ('RETAIL_DAY_CLOSING', 'RETAIL_OPERATIONS', 'Day Closing', 'End-of-day register close', 200, 'RETAIL_OPS', 'BASE', 'FREE', 0, 'BASIC', FALSE, '["RETAIL_CASH_REGISTER"]'),
    ('RETAIL_SALES_ANALYTICS', 'RETAIL_OPERATIONS', 'Sales Analytics', 'Sales trends', 210, 'RETAIL_OPS', 'ADDON', 'MONTHLY', 29900, 'PREMIUM', TRUE, '["RETAIL_REPORTS"]'),
    ('RETAIL_PURCHASE_ANALYTICS', 'RETAIL_OPERATIONS', 'Purchase Analytics', 'Purchase trends', 220, 'RETAIL_OPS', 'ADDON', 'MONTHLY', 29900, 'PREMIUM', TRUE, '["RETAIL_REPORTS"]'),
    ('RETAIL_INVENTORY_ANALYTICS', 'RETAIL_OPERATIONS', 'Inventory Analytics', 'Stock movement analytics', 230, 'RETAIL_OPS', 'ADDON', 'MONTHLY', 29900, 'PREMIUM', TRUE, '["RETAIL_REPORTS"]'),
    ('RETAIL_PROFIT_MARGIN', 'RETAIL_OPERATIONS', 'Profit & Margin Reports', 'Margin by SKU / bill', 240, 'RETAIL_OPS', 'ADDON', 'MONTHLY', 39900, 'PREMIUM', TRUE, '["RETAIL_REPORTS"]'),
    ('CASHBOOK', 'PLATFORM_FINANCE', 'Cashbook', 'Daily cash / bank book', 5, 'FINANCE', 'ADDON', 'MONTHLY', 29900, 'BASIC', TRUE, '[]')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    availability = EXCLUDED.availability,
    billing_type = EXCLUDED.billing_type,
    price_minor = EXCLUDED.price_minor,
    tier = EXCLUDED.tier,
    is_addon = EXCLUDED.is_addon,
    dependencies_json = EXCLUDED.dependencies_json;

INSERT INTO feature_definition (code, module_code, name, description, sort_order, category, availability, billing_type, price_minor, tier, is_addon, dependencies_json) VALUES
    ('FINANCE_INCOME', 'PLATFORM_FINANCE', 'Income', 'Record other income', 10, 'FINANCE', 'ADDON', 'MONTHLY', 0, 'BASIC', TRUE, '["CASHBOOK"]'),
    ('FINANCE_EXPENSES', 'PLATFORM_FINANCE', 'Expenses', 'Record expenses', 20, 'FINANCE', 'ADDON', 'MONTHLY', 0, 'BASIC', TRUE, '["CASHBOOK"]'),
    ('FINANCE_EXPENSE_CATEGORIES', 'PLATFORM_FINANCE', 'Expense Categories', 'Expense category master', 30, 'FINANCE', 'ADDON', 'FREE', 0, 'BASIC', TRUE, '["FINANCE_EXPENSES"]'),
    ('FINANCE_CASH_IN_OUT', 'PLATFORM_FINANCE', 'Cash In / Cash Out', 'Cash movement', 40, 'FINANCE', 'ADDON', 'FREE', 0, 'BASIC', TRUE, '["CASHBOOK"]'),
    ('FINANCE_DAILY', 'PLATFORM_FINANCE', 'Daily Income & Expense', 'Day-wise income and expense', 50, 'FINANCE', 'ADDON', 'FREE', 0, 'BASIC', TRUE, '["FINANCE_INCOME","FINANCE_EXPENSES"]'),
    ('FINANCE_PAYMENT_TRACKING', 'PLATFORM_FINANCE', 'Payment Tracking', 'Track incoming / outgoing payments', 60, 'FINANCE', 'ADDON', 'MONTHLY', 19900, 'BASIC', TRUE, '["CASHBOOK"]'),
    ('FINANCE_RECEIVABLES', 'PLATFORM_FINANCE', 'Receivables', 'Customer receivables', 70, 'FINANCE', 'ADDON', 'MONTHLY', 19900, 'BASIC', TRUE, '["CASHBOOK"]'),
    ('FINANCE_PAYABLES', 'PLATFORM_FINANCE', 'Payables', 'Supplier payables', 80, 'FINANCE', 'ADDON', 'MONTHLY', 19900, 'BASIC', TRUE, '["CASHBOOK"]'),
    ('FINANCE_CUSTOMER_OUTSTANDING', 'PLATFORM_FINANCE', 'Customer Outstanding', 'Customer due ledger', 90, 'FINANCE', 'ADDON', 'MONTHLY', 0, 'BASIC', TRUE, '["FINANCE_RECEIVABLES"]'),
    ('FINANCE_SUPPLIER_OUTSTANDING', 'PLATFORM_FINANCE', 'Supplier Outstanding', 'Supplier due ledger', 100, 'FINANCE', 'ADDON', 'MONTHLY', 0, 'BASIC', TRUE, '["FINANCE_PAYABLES"]'),
    ('FINANCE_CASH_BANK', 'PLATFORM_FINANCE', 'Cash / Bank Accounts', 'Cash and bank books', 110, 'FINANCE', 'ADDON', 'FREE', 0, 'BASIC', TRUE, '["CASHBOOK"]'),
    ('FINANCE_PAYMENT_MODES', 'PLATFORM_FINANCE', 'Payment Modes', 'Cash, UPI, card, bank', 120, 'FINANCE', 'ADDON', 'FREE', 0, 'BASIC', TRUE, '["CASHBOOK"]'),
    ('FINANCE_TRANSACTIONS', 'PLATFORM_FINANCE', 'Financial Transactions', 'All finance postings', 130, 'FINANCE', 'ADDON', 'FREE', 0, 'BASIC', TRUE, '["CASHBOOK"]'),
    ('FINANCE_PNL', 'PLATFORM_FINANCE', 'Profit & Loss', 'P&L statement', 140, 'FINANCE', 'ADDON', 'MONTHLY', 39900, 'PREMIUM', TRUE, '["CASHBOOK"]'),
    ('FINANCE_CASH_FLOW', 'PLATFORM_FINANCE', 'Cash Flow', 'Cash flow statement', 150, 'FINANCE', 'ADDON', 'MONTHLY', 39900, 'PREMIUM', TRUE, '["CASHBOOK"]'),
    ('FINANCE_REPORTS', 'PLATFORM_FINANCE', 'Financial Reports', 'Finance report pack', 160, 'FINANCE', 'ADDON', 'MONTHLY', 29900, 'PREMIUM', TRUE, '["CASHBOOK"]'),
    ('FINANCE_DAY_CLOSING', 'PLATFORM_FINANCE', 'Finance Day Closing', 'Finance EOD', 170, 'FINANCE', 'ADDON', 'FREE', 0, 'BASIC', TRUE, '["CASHBOOK"]'),
    ('FINANCE_ACCOUNTING_EXPORT', 'PLATFORM_FINANCE', 'Accounting Export', 'Export to accountant', 180, 'FINANCE', 'ADDON', 'MONTHLY', 29900, 'PREMIUM', TRUE, '["CASHBOOK"]'),
    ('FINANCE_GST_REPORTS', 'PLATFORM_FINANCE', 'GST Reports', 'GST summaries', 190, 'FINANCE', 'ADDON', 'MONTHLY', 29900, 'PREMIUM', TRUE, '["CASHBOOK"]'),
    ('FINANCE_TAX_REPORTS', 'PLATFORM_FINANCE', 'Tax Reports', 'Tax summaries', 200, 'FINANCE', 'ADDON', 'MONTHLY', 29900, 'PREMIUM', TRUE, '["FINANCE_GST_REPORTS"]'),
    ('FINANCE_INVOICE_TAX', 'PLATFORM_FINANCE', 'Invoice Tax Management', 'Tax on invoices', 210, 'FINANCE', 'ADDON', 'MONTHLY', 19900, 'BASIC', TRUE, '["CASHBOOK"]')
ON CONFLICT (code) DO UPDATE SET
    category = EXCLUDED.category, availability = EXCLUDED.availability, is_addon = EXCLUDED.is_addon,
    billing_type = EXCLUDED.billing_type, price_minor = EXCLUDED.price_minor, tier = EXCLUDED.tier,
    dependencies_json = EXCLUDED.dependencies_json, description = EXCLUDED.description, name = EXCLUDED.name;

INSERT INTO feature_definition (code, module_code, name, description, sort_order, category, availability, billing_type, price_minor, tier, is_addon, dependencies_json) VALUES
    ('WHATSAPP_INVOICE', 'PLATFORM_COMMS', 'WhatsApp Invoice', 'Send invoice on WhatsApp', 10, 'COMMUNICATION', 'ADDON', 'MONTHLY', 19900, 'BASIC', TRUE, '["INTEGRATION_WHATSAPP"]'),
    ('WHATSAPP_PAYMENT_RECEIPT', 'PLATFORM_COMMS', 'WhatsApp Payment Receipt', 'Receipt on WhatsApp', 20, 'COMMUNICATION', 'ADDON', 'MONTHLY', 14900, 'BASIC', TRUE, '["INTEGRATION_WHATSAPP"]'),
    ('WHATSAPP_ORDER_CONFIRM', 'PLATFORM_COMMS', 'WhatsApp Order Confirmation', 'Order confirm on WhatsApp', 30, 'COMMUNICATION', 'ADDON', 'MONTHLY', 14900, 'BASIC', TRUE, '["INTEGRATION_WHATSAPP"]'),
    ('WHATSAPP_PURCHASE_ORDER', 'PLATFORM_COMMS', 'WhatsApp Purchase Order', 'PO on WhatsApp', 40, 'COMMUNICATION', 'ADDON', 'MONTHLY', 14900, 'BASIC', TRUE, '["INTEGRATION_WHATSAPP"]'),
    ('WHATSAPP_CUSTOMER_NOTIFY', 'PLATFORM_COMMS', 'WhatsApp Customer Notification', 'General customer WhatsApp', 50, 'COMMUNICATION', 'ADDON', 'MONTHLY', 19900, 'BASIC', TRUE, '["INTEGRATION_WHATSAPP"]'),
    ('WHATSAPP_APPOINTMENT', 'PLATFORM_COMMS', 'WhatsApp Appointment', 'Appointment reminders', 60, 'COMMUNICATION', 'ADDON', 'MONTHLY', 19900, 'BASIC', TRUE, '["INTEGRATION_WHATSAPP"]'),
    ('WHATSAPP_LAB_REPORT', 'PLATFORM_COMMS', 'WhatsApp Lab Report', 'Send lab reports', 70, 'COMMUNICATION', 'ADDON', 'MONTHLY', 29900, 'PREMIUM', TRUE, '["INTEGRATION_WHATSAPP"]'),
    ('WHATSAPP_PRESCRIPTION', 'PLATFORM_COMMS', 'WhatsApp Prescription', 'Send prescriptions', 80, 'COMMUNICATION', 'ADDON', 'MONTHLY', 29900, 'PREMIUM', TRUE, '["INTEGRATION_WHATSAPP"]'),
    ('WHATSAPP_DUE_REMINDER', 'PLATFORM_COMMS', 'WhatsApp Due Reminder', 'Due / credit reminders', 90, 'COMMUNICATION', 'ADDON', 'MONTHLY', 19900, 'BASIC', TRUE, '["INTEGRATION_WHATSAPP"]'),
    ('WHATSAPP_MARKETING', 'PLATFORM_COMMS', 'WhatsApp Marketing', 'Broadcast campaigns', 100, 'COMMUNICATION', 'ADDON', 'MONTHLY', 59900, 'PREMIUM', TRUE, '["INTEGRATION_WHATSAPP"]'),
    ('SMS_NOTIFICATIONS', 'PLATFORM_COMMS', 'SMS Notifications', 'Transactional SMS', 110, 'COMMUNICATION', 'ADDON', 'USAGE', 9900, 'BASIC', TRUE, '["INTEGRATION_SMS"]'),
    ('EMAIL_NOTIFICATIONS', 'PLATFORM_COMMS', 'Email Notifications', 'Transactional email', 120, 'COMMUNICATION', 'ADDON', 'MONTHLY', 9900, 'BASIC', TRUE, '["INTEGRATION_EMAIL"]'),
    ('NOTIFICATION_TEMPLATES', 'PLATFORM_COMMS', 'Notification Templates', 'Reusable message templates', 130, 'COMMUNICATION', 'ADDON', 'FREE', 0, 'BASIC', TRUE, '[]')
ON CONFLICT (code) DO UPDATE SET
    category = EXCLUDED.category, availability = EXCLUDED.availability, is_addon = EXCLUDED.is_addon,
    billing_type = EXCLUDED.billing_type, price_minor = EXCLUDED.price_minor, tier = EXCLUDED.tier,
    dependencies_json = EXCLUDED.dependencies_json, description = EXCLUDED.description, name = EXCLUDED.name;

INSERT INTO feature_definition (code, module_code, name, description, sort_order, category, availability, billing_type, price_minor, tier, is_addon, dependencies_json) VALUES
    ('AI_PRODUCT_SEARCH', 'PLATFORM_AI', 'AI Product Search', 'Natural-language product search', 10, 'AI', 'ENTERPRISE', 'USAGE', 79900, 'ENTERPRISE', TRUE, '[]'),
    ('AI_MEDICINE_SEARCH', 'PLATFORM_AI', 'AI Medicine Search', 'Medicine search assistant', 20, 'AI', 'ENTERPRISE', 'USAGE', 79900, 'ENTERPRISE', TRUE, '[]'),
    ('AI_PRODUCT_RECOMMEND', 'PLATFORM_AI', 'AI Product Recommendation', 'Suggest related SKUs', 30, 'AI', 'ENTERPRISE', 'USAGE', 79900, 'ENTERPRISE', TRUE, '["RETAIL_POS"]'),
    ('AI_INVOICE_ASSISTANT', 'PLATFORM_AI', 'AI Invoice Assistant', 'Help draft / check invoices', 40, 'AI', 'ENTERPRISE', 'USAGE', 79900, 'ENTERPRISE', TRUE, '[]'),
    ('AI_PURCHASE_ASSISTANT', 'PLATFORM_AI', 'AI Purchase Assistant', 'Purchase suggestions', 50, 'AI', 'ENTERPRISE', 'USAGE', 79900, 'ENTERPRISE', TRUE, '["RETAIL_PURCHASE"]'),
    ('AI_STOCK_REORDER', 'PLATFORM_AI', 'AI Stock Reorder Suggestion', 'Reorder suggestions', 60, 'AI', 'ENTERPRISE', 'USAGE', 79900, 'ENTERPRISE', TRUE, '["RETAIL_INVENTORY"]'),
    ('AI_DEMAND_FORECAST', 'PLATFORM_AI', 'AI Demand Forecasting', 'Demand forecast', 70, 'AI', 'ENTERPRISE', 'USAGE', 99900, 'ENTERPRISE', TRUE, '["RETAIL_INVENTORY"]'),
    ('AI_SALES_FORECAST', 'PLATFORM_AI', 'AI Sales Forecast', 'Sales forecast', 80, 'AI', 'ENTERPRISE', 'USAGE', 99900, 'ENTERPRISE', TRUE, '["RETAIL_REPORTS"]'),
    ('AI_EXPENSE_CATEGORIZE', 'PLATFORM_AI', 'AI Expense Categorization', 'Auto-categorize expenses', 90, 'AI', 'ADDON', 'USAGE', 39900, 'PREMIUM', TRUE, '["FINANCE_EXPENSES"]'),
    ('AI_REPORT_SUMMARY', 'PLATFORM_AI', 'AI Report Summary', 'Summarize reports', 100, 'AI', 'ENTERPRISE', 'USAGE', 79900, 'ENTERPRISE', TRUE, '["REPORT_DASHBOARD"]'),
    ('AI_BUSINESS_INSIGHTS', 'PLATFORM_AI', 'AI Business Insights', 'Business insight cards', 110, 'AI', 'ENTERPRISE', 'USAGE', 99900, 'ENTERPRISE', TRUE, '["REPORT_DASHBOARD"]'),
    ('AI_CUSTOMER_INSIGHTS', 'PLATFORM_AI', 'AI Customer Insights', 'Customer insight cards', 120, 'AI', 'ENTERPRISE', 'USAGE', 79900, 'ENTERPRISE', TRUE, '["RETAIL_CUSTOMERS"]'),
    ('AI_SUPPLIER_ANALYSIS', 'PLATFORM_AI', 'AI Supplier Analysis', 'Supplier analysis', 130, 'AI', 'ENTERPRISE', 'USAGE', 79900, 'ENTERPRISE', TRUE, '["RETAIL_SUPPLIERS"]'),
    ('AI_PRICE_COMPARE', 'PLATFORM_AI', 'AI Price Comparison', 'Price compare assistant', 140, 'AI', 'ENTERPRISE', 'USAGE', 79900, 'ENTERPRISE', TRUE, '["RETAIL_SUPPLIER_PRICE_COMPARE"]'),
    ('AI_OCR_DOCUMENT', 'PLATFORM_AI', 'AI OCR Document Import', 'Import documents via OCR', 150, 'AI', 'ADDON', 'USAGE', 79900, 'PREMIUM', TRUE, '[]'),
    ('AI_BILL_OCR', 'PLATFORM_AI', 'AI Bill / Invoice OCR', 'Purchase bill OCR', 160, 'AI', 'ADDON', 'USAGE', 79900, 'PREMIUM', TRUE, '["AI_OCR_DOCUMENT"]'),
    ('AI_VOICE_ENTRY', 'PLATFORM_AI', 'AI Voice Entry', 'Voice data entry', 170, 'AI', 'ENTERPRISE', 'USAGE', 99900, 'ENTERPRISE', TRUE, '[]'),
    ('AI_VOICE_PRESCRIPTION', 'PLATFORM_AI', 'AI Voice Prescription', 'Voice prescription', 180, 'AI', 'ENTERPRISE', 'USAGE', 99900, 'ENTERPRISE', TRUE, '[]'),
    ('AI_SMART_SEARCH', 'PLATFORM_AI', 'AI Smart Search', 'Smart global search', 190, 'AI', 'ADDON', 'MONTHLY', 49900, 'PREMIUM', TRUE, '[]'),
    ('AI_CHAT_ASSISTANT', 'PLATFORM_AI', 'AI Chat Assistant', 'In-app AI chat', 200, 'AI', 'ENTERPRISE', 'USAGE', 99900, 'ENTERPRISE', TRUE, '[]')
ON CONFLICT (code) DO UPDATE SET
    category = EXCLUDED.category, availability = EXCLUDED.availability, is_addon = EXCLUDED.is_addon,
    billing_type = EXCLUDED.billing_type, price_minor = EXCLUDED.price_minor, tier = EXCLUDED.tier,
    dependencies_json = EXCLUDED.dependencies_json, description = EXCLUDED.description, name = EXCLUDED.name;

INSERT INTO feature_definition (code, module_code, name, description, sort_order, category, availability, billing_type, price_minor, tier, is_addon, dependencies_json) VALUES
    ('REPORT_DASHBOARD', 'PLATFORM_REPORTS', 'Dashboard', 'Operational dashboard', 10, 'REPORTS', 'BASE', 'FREE', 0, 'BASIC', FALSE, '[]'),
    ('REPORT_SALES', 'PLATFORM_REPORTS', 'Sales Reports', 'Sales report pack', 20, 'REPORTS', 'BASE', 'FREE', 0, 'BASIC', FALSE, '[]'),
    ('REPORT_PURCHASE', 'PLATFORM_REPORTS', 'Purchase Reports', 'Purchase report pack', 30, 'REPORTS', 'BASE', 'FREE', 0, 'BASIC', FALSE, '[]'),
    ('REPORT_INVENTORY', 'PLATFORM_REPORTS', 'Inventory Reports', 'Stock report pack', 40, 'REPORTS', 'BASE', 'FREE', 0, 'BASIC', FALSE, '[]'),
    ('REPORT_FINANCE', 'PLATFORM_REPORTS', 'Finance Reports', 'Finance report pack', 50, 'REPORTS', 'ADDON', 'MONTHLY', 29900, 'PREMIUM', TRUE, '["CASHBOOK"]'),
    ('REPORT_CUSTOMER', 'PLATFORM_REPORTS', 'Customer Reports', 'Customer report pack', 60, 'REPORTS', 'BASE', 'FREE', 0, 'BASIC', FALSE, '[]'),
    ('REPORT_SUPPLIER', 'PLATFORM_REPORTS', 'Supplier Reports', 'Supplier report pack', 70, 'REPORTS', 'BASE', 'FREE', 0, 'BASIC', FALSE, '[]'),
    ('REPORT_TAX', 'PLATFORM_REPORTS', 'Tax Reports', 'Tax report pack', 80, 'REPORTS', 'ADDON', 'MONTHLY', 29900, 'PREMIUM', TRUE, '[]'),
    ('REPORT_PROFIT', 'PLATFORM_REPORTS', 'Profit Reports', 'Profit report pack', 90, 'REPORTS', 'ADDON', 'MONTHLY', 39900, 'PREMIUM', TRUE, '[]'),
    ('REPORT_BUSINESS_ANALYTICS', 'PLATFORM_REPORTS', 'Business Analytics', 'Business analytics', 100, 'REPORTS', 'ADDON', 'MONTHLY', 49900, 'PREMIUM', TRUE, '["REPORT_DASHBOARD"]'),
    ('REPORT_ADVANCED_ANALYTICS', 'PLATFORM_REPORTS', 'Advanced Analytics', 'Advanced analytics', 110, 'REPORTS', 'ENTERPRISE', 'MONTHLY', 79900, 'ENTERPRISE', TRUE, '["REPORT_BUSINESS_ANALYTICS"]'),
    ('REPORT_CUSTOM', 'PLATFORM_REPORTS', 'Custom Reports', 'Build custom reports', 120, 'REPORTS', 'ENTERPRISE', 'MONTHLY', 59900, 'ENTERPRISE', TRUE, '["REPORT_DASHBOARD"]'),
    ('REPORT_EXPORT_EXCEL', 'PLATFORM_REPORTS', 'Export Excel', 'Excel export', 130, 'REPORTS', 'BASE', 'FREE', 0, 'BASIC', FALSE, '[]'),
    ('REPORT_EXPORT_PDF', 'PLATFORM_REPORTS', 'Export PDF', 'PDF export', 140, 'REPORTS', 'BASE', 'FREE', 0, 'BASIC', FALSE, '[]'),
    ('REPORT_SCHEDULED', 'PLATFORM_REPORTS', 'Scheduled Reports', 'Email scheduled reports', 150, 'REPORTS', 'ADDON', 'MONTHLY', 29900, 'PREMIUM', TRUE, '["REPORT_DASHBOARD"]')
ON CONFLICT (code) DO UPDATE SET
    category = EXCLUDED.category, availability = EXCLUDED.availability, is_addon = EXCLUDED.is_addon,
    billing_type = EXCLUDED.billing_type, price_minor = EXCLUDED.price_minor, tier = EXCLUDED.tier,
    dependencies_json = EXCLUDED.dependencies_json, description = EXCLUDED.description, name = EXCLUDED.name;

INSERT INTO feature_definition (code, module_code, name, description, sort_order, category, availability, billing_type, price_minor, tier, is_addon, dependencies_json) VALUES
    ('CRM_CUSTOMER_MGMT', 'PLATFORM_CRM', 'Customer Management', 'CRM customer records', 10, 'CRM', 'ADDON', 'MONTHLY', 39900, 'BASIC', TRUE, '[]'),
    ('CRM_CUSTOMER_HISTORY', 'PLATFORM_CRM', 'Customer History', 'Purchase / visit history', 20, 'CRM', 'ADDON', 'MONTHLY', 0, 'BASIC', TRUE, '["CRM_CUSTOMER_MGMT"]'),
    ('CRM_SEGMENTATION', 'PLATFORM_CRM', 'Customer Segmentation', 'Segments and lists', 30, 'CRM', 'ADDON', 'MONTHLY', 29900, 'PREMIUM', TRUE, '["CRM_CUSTOMER_MGMT"]'),
    ('CRM_FOLLOWUP', 'PLATFORM_CRM', 'Customer Follow-up', 'Follow-up tasks', 40, 'CRM', 'ADDON', 'MONTHLY', 19900, 'BASIC', TRUE, '["CRM_CUSTOMER_MGMT"]'),
    ('CRM_REMINDERS', 'PLATFORM_CRM', 'Customer Reminders', 'Customer reminders', 50, 'CRM', 'ADDON', 'MONTHLY', 19900, 'BASIC', TRUE, '["CRM_CUSTOMER_MGMT"]'),
    ('CRM_LOYALTY', 'PLATFORM_CRM', 'Loyalty Program', 'CRM loyalty', 60, 'CRM', 'ADDON', 'MONTHLY', 39900, 'PREMIUM', TRUE, '["CRM_CUSTOMER_MGMT"]'),
    ('CRM_OFFERS', 'PLATFORM_CRM', 'Offers & Campaigns', 'Offers', 70, 'CRM', 'ADDON', 'MONTHLY', 39900, 'PREMIUM', TRUE, '["CRM_CUSTOMER_MGMT"]'),
    ('CRM_MARKETING', 'PLATFORM_CRM', 'Marketing Campaigns', 'Campaigns', 80, 'CRM', 'ADDON', 'MONTHLY', 49900, 'PREMIUM', TRUE, '["CRM_OFFERS"]'),
    ('CRM_LEADS', 'PLATFORM_CRM', 'Lead Management', 'Leads pipeline', 90, 'CRM', 'ADDON', 'MONTHLY', 49900, 'PREMIUM', TRUE, '["CRM_CUSTOMER_MGMT"]'),
    ('CRM_ANALYTICS', 'PLATFORM_CRM', 'CRM Analytics', 'CRM analytics', 100, 'CRM', 'ADDON', 'MONTHLY', 39900, 'PREMIUM', TRUE, '["CRM_CUSTOMER_MGMT"]')
ON CONFLICT (code) DO UPDATE SET
    category = EXCLUDED.category, availability = EXCLUDED.availability, is_addon = EXCLUDED.is_addon,
    billing_type = EXCLUDED.billing_type, price_minor = EXCLUDED.price_minor, tier = EXCLUDED.tier,
    dependencies_json = EXCLUDED.dependencies_json, description = EXCLUDED.description, name = EXCLUDED.name;

INSERT INTO feature_definition (code, module_code, name, description, sort_order, category, availability, billing_type, price_minor, tier, is_addon, dependencies_json) VALUES
    ('ENT_MULTI_BRANCH', 'PLATFORM_ENTERPRISE', 'Multi-Branch', 'More than one branch', 10, 'ENTERPRISE', 'ENTERPRISE', 'MONTHLY', 119900, 'ENTERPRISE', TRUE, '[]'),
    ('ENT_MULTI_OUTLET', 'PLATFORM_ENTERPRISE', 'Multi-Outlet', 'Multiple outlets', 20, 'ENTERPRISE', 'ENTERPRISE', 'MONTHLY', 119900, 'ENTERPRISE', TRUE, '["ENT_MULTI_BRANCH"]'),
    ('ENT_BRANCH_STOCK_TRANSFER', 'PLATFORM_ENTERPRISE', 'Branch Stock Transfer', 'Inter-branch stock', 30, 'ENTERPRISE', 'ENTERPRISE', 'MONTHLY', 39900, 'ENTERPRISE', TRUE, '["ENT_MULTI_BRANCH"]'),
    ('ENT_CENTRAL_INVENTORY', 'PLATFORM_ENTERPRISE', 'Centralized Inventory', 'Central stock view', 40, 'ENTERPRISE', 'ENTERPRISE', 'MONTHLY', 59900, 'ENTERPRISE', TRUE, '["ENT_MULTI_BRANCH"]'),
    ('ENT_CENTRAL_PURCHASE', 'PLATFORM_ENTERPRISE', 'Centralized Purchasing', 'Central purchase', 50, 'ENTERPRISE', 'ENTERPRISE', 'MONTHLY', 59900, 'ENTERPRISE', TRUE, '["ENT_MULTI_BRANCH"]'),
    ('ENT_BRANCH_REPORTS', 'PLATFORM_ENTERPRISE', 'Branch-wise Reports', 'Per-branch reports', 60, 'ENTERPRISE', 'ENTERPRISE', 'MONTHLY', 39900, 'ENTERPRISE', TRUE, '["ENT_MULTI_BRANCH"]'),
    ('ENT_BRANCH_FINANCE', 'PLATFORM_ENTERPRISE', 'Branch-wise Finance', 'Per-branch finance', 70, 'ENTERPRISE', 'ENTERPRISE', 'MONTHLY', 39900, 'ENTERPRISE', TRUE, '["ENT_MULTI_BRANCH","CASHBOOK"]'),
    ('ENT_CONSOLIDATED_REPORTS', 'PLATFORM_ENTERPRISE', 'Consolidated Reports', 'Group reports', 80, 'ENTERPRISE', 'ENTERPRISE', 'MONTHLY', 59900, 'ENTERPRISE', TRUE, '["ENT_MULTI_BRANCH"]'),
    ('ENT_RBAC', 'PLATFORM_ENTERPRISE', 'Role-based Access', 'Roles and access', 90, 'ENTERPRISE', 'BASE', 'FREE', 0, 'BASIC', FALSE, '[]'),
    ('ENT_ADVANCED_PERMISSIONS', 'PLATFORM_ENTERPRISE', 'Advanced Permissions', 'Fine-grained permissions', 100, 'ENTERPRISE', 'ENTERPRISE', 'MONTHLY', 39900, 'ENTERPRISE', TRUE, '["ENT_RBAC"]'),
    ('ENT_APPROVAL_WORKFLOW', 'PLATFORM_ENTERPRISE', 'Approval Workflow', 'Maker-checker', 110, 'ENTERPRISE', 'ENTERPRISE', 'MONTHLY', 59900, 'ENTERPRISE', TRUE, '[]'),
    ('ENT_AUDIT_LOGS', 'PLATFORM_ENTERPRISE', 'Audit Logs', 'Change audit', 120, 'ENTERPRISE', 'ENTERPRISE', 'MONTHLY', 39900, 'ENTERPRISE', TRUE, '[]'),
    ('ENT_DATA_EXPORT', 'PLATFORM_ENTERPRISE', 'Data Export', 'Bulk data export', 130, 'ENTERPRISE', 'ADDON', 'MONTHLY', 29900, 'PREMIUM', TRUE, '[]'),
    ('ENT_API_ACCESS', 'PLATFORM_ENTERPRISE', 'API Access', 'Public API', 140, 'ENTERPRISE', 'ENTERPRISE', 'MONTHLY', 99900, 'ENTERPRISE', TRUE, '[]'),
    ('ENT_WEBHOOKS', 'PLATFORM_ENTERPRISE', 'Webhooks', 'Outbound webhooks', 150, 'ENTERPRISE', 'ENTERPRISE', 'MONTHLY', 59900, 'ENTERPRISE', TRUE, '["ENT_API_ACCESS"]'),
    ('ENT_EXTERNAL_ERP', 'PLATFORM_ENTERPRISE', 'Enterprise Integrations', 'External ERP hooks', 160, 'ENTERPRISE', 'ENTERPRISE', 'MONTHLY', 149900, 'ENTERPRISE', TRUE, '["ENT_API_ACCESS"]')
ON CONFLICT (code) DO UPDATE SET
    category = EXCLUDED.category, availability = EXCLUDED.availability, is_addon = EXCLUDED.is_addon,
    billing_type = EXCLUDED.billing_type, price_minor = EXCLUDED.price_minor, tier = EXCLUDED.tier,
    dependencies_json = EXCLUDED.dependencies_json, description = EXCLUDED.description, name = EXCLUDED.name;

INSERT INTO feature_definition (code, module_code, name, description, sort_order, category, availability, billing_type, price_minor, tier, is_addon, dependencies_json) VALUES
    ('SEC_ROLE_MGMT', 'PLATFORM_SECURITY', 'Role Management', 'Manage roles', 10, 'SECURITY', 'BASE', 'FREE', 0, 'BASIC', FALSE, '[]'),
    ('SEC_PERMISSION_MGMT', 'PLATFORM_SECURITY', 'Permission Management', 'Manage permissions', 20, 'SECURITY', 'BASE', 'FREE', 0, 'BASIC', FALSE, '[]'),
    ('SEC_FEATURE_PERMISSION', 'PLATFORM_SECURITY', 'Feature-level Permission', 'Per-feature ACL', 30, 'SECURITY', 'ENTERPRISE', 'MONTHLY', 39900, 'ENTERPRISE', TRUE, '["SEC_PERMISSION_MGMT"]'),
    ('SEC_AUDIT_LOG', 'PLATFORM_SECURITY', 'Audit Log', 'Security audit log', 40, 'SECURITY', 'ADDON', 'MONTHLY', 29900, 'PREMIUM', TRUE, '[]'),
    ('SEC_LOGIN_HISTORY', 'PLATFORM_SECURITY', 'Login History', 'Login attempts', 50, 'SECURITY', 'BASE', 'FREE', 0, 'BASIC', FALSE, '[]'),
    ('SEC_ACTIVITY', 'PLATFORM_SECURITY', 'Activity Tracking', 'User activity', 60, 'SECURITY', 'ADDON', 'MONTHLY', 19900, 'BASIC', TRUE, '[]'),
    ('SEC_APPROVAL', 'PLATFORM_SECURITY', 'Security Approval Workflow', 'Sensitive-action approvals', 70, 'SECURITY', 'ENTERPRISE', 'MONTHLY', 39900, 'ENTERPRISE', TRUE, '[]'),
    ('SEC_DATA_EXPORT', 'PLATFORM_SECURITY', 'Secure Data Export', 'Controlled export', 80, 'SECURITY', 'ADDON', 'MONTHLY', 19900, 'PREMIUM', TRUE, '[]'),
    ('SEC_BACKUP', 'PLATFORM_SECURITY', 'Backup Configuration', 'Backup settings', 90, 'SECURITY', 'ENTERPRISE', 'MONTHLY', 39900, 'ENTERPRISE', TRUE, '[]'),
    ('SEC_SETTINGS', 'PLATFORM_SECURITY', 'Security Settings', 'Password / lockout policy', 100, 'SECURITY', 'BASE', 'FREE', 0, 'BASIC', FALSE, '[]'),
    ('SEC_API_ACCESS', 'PLATFORM_SECURITY', 'Security API Access', 'API keys', 110, 'SECURITY', 'ENTERPRISE', 'MONTHLY', 59900, 'ENTERPRISE', TRUE, '[]'),
    ('SEC_INTEGRATION_MGMT', 'PLATFORM_SECURITY', 'Integration Management', 'Manage integrations', 120, 'SECURITY', 'ADDON', 'MONTHLY', 19900, 'PREMIUM', TRUE, '[]')
ON CONFLICT (code) DO UPDATE SET
    category = EXCLUDED.category, availability = EXCLUDED.availability, is_addon = EXCLUDED.is_addon,
    billing_type = EXCLUDED.billing_type, price_minor = EXCLUDED.price_minor, tier = EXCLUDED.tier,
    dependencies_json = EXCLUDED.dependencies_json, description = EXCLUDED.description, name = EXCLUDED.name;

INSERT INTO feature_definition (code, module_code, name, description, sort_order, category, availability, billing_type, price_minor, tier, is_addon, dependencies_json) VALUES
    ('INTEGRATION_WHATSAPP', 'PLATFORM_INTEGRATIONS', 'WhatsApp', 'WhatsApp Business API', 10, 'INTEGRATIONS', 'ADDON', 'MONTHLY', 29900, 'BASIC', TRUE, '[]'),
    ('INTEGRATION_SMS', 'PLATFORM_INTEGRATIONS', 'SMS Gateway', 'SMS provider', 20, 'INTEGRATIONS', 'ADDON', 'USAGE', 9900, 'BASIC', TRUE, '[]'),
    ('INTEGRATION_EMAIL', 'PLATFORM_INTEGRATIONS', 'Email', 'SMTP / email provider', 30, 'INTEGRATIONS', 'ADDON', 'MONTHLY', 9900, 'BASIC', TRUE, '[]'),
    ('INTEGRATION_PAYMENT', 'PLATFORM_INTEGRATIONS', 'Payment Gateway', 'Razorpay / similar', 40, 'INTEGRATIONS', 'ADDON', 'MONTHLY', 39900, 'PREMIUM', TRUE, '[]'),
    ('INTEGRATION_UPI', 'PLATFORM_INTEGRATIONS', 'UPI', 'UPI collections', 50, 'INTEGRATIONS', 'ADDON', 'MONTHLY', 19900, 'BASIC', TRUE, '["INTEGRATION_PAYMENT"]'),
    ('INTEGRATION_ACCOUNTING', 'PLATFORM_INTEGRATIONS', 'Accounting Integration', 'Tally / accountant export', 60, 'INTEGRATIONS', 'ADDON', 'MONTHLY', 49900, 'PREMIUM', TRUE, '["CASHBOOK"]'),
    ('INTEGRATION_GST', 'PLATFORM_INTEGRATIONS', 'GST Integration', 'GST portal hooks', 70, 'INTEGRATIONS', 'ADDON', 'MONTHLY', 49900, 'PREMIUM', TRUE, '[]'),
    ('INTEGRATION_ECOMMERCE', 'PLATFORM_INTEGRATIONS', 'E-commerce Integration', 'Online store sync', 80, 'INTEGRATIONS', 'ENTERPRISE', 'MONTHLY', 99900, 'ENTERPRISE', TRUE, '[]'),
    ('INTEGRATION_API', 'PLATFORM_INTEGRATIONS', 'API Integration', 'REST API', 90, 'INTEGRATIONS', 'ENTERPRISE', 'MONTHLY', 99900, 'ENTERPRISE', TRUE, '[]'),
    ('INTEGRATION_WEBHOOKS', 'PLATFORM_INTEGRATIONS', 'Webhooks', 'Webhook endpoints', 100, 'INTEGRATIONS', 'ENTERPRISE', 'MONTHLY', 59900, 'ENTERPRISE', TRUE, '["INTEGRATION_API"]'),
    ('INTEGRATION_ERP', 'PLATFORM_INTEGRATIONS', 'External ERP Integration', 'External ERP', 110, 'INTEGRATIONS', 'ENTERPRISE', 'MONTHLY', 149900, 'ENTERPRISE', TRUE, '["INTEGRATION_API"]')
ON CONFLICT (code) DO UPDATE SET
    category = EXCLUDED.category, availability = EXCLUDED.availability, is_addon = EXCLUDED.is_addon,
    billing_type = EXCLUDED.billing_type, price_minor = EXCLUDED.price_minor, tier = EXCLUDED.tier,
    dependencies_json = EXCLUDED.dependencies_json, description = EXCLUDED.description, name = EXCLUDED.name;

-- Compatibility: shop verticals for PLATFORM features
INSERT INTO feature_business_type (feature_code, business_type_code)
SELECT f.code, bt.code
FROM feature_definition f
CROSS JOIN business_type bt
WHERE f.module_code LIKE 'PLATFORM_%'
  AND bt.code IN ('RETAIL', 'PHARMACY', 'MEDICAL_STORE', 'MEDICAL_DISTRIBUTOR', 'HOSPITAL', 'POLYCLINIC', 'PATHLAB')
ON CONFLICT DO NOTHING;

-- Clinic-only comms
DELETE FROM feature_business_type
WHERE feature_code = 'WHATSAPP_LAB_REPORT'
  AND business_type_code IN ('RETAIL', 'PHARMACY', 'MEDICAL_STORE', 'MEDICAL_DISTRIBUTOR');
DELETE FROM feature_business_type
WHERE feature_code = 'WHATSAPP_PRESCRIPTION'
  AND business_type_code IN ('RETAIL', 'PHARMACY', 'MEDICAL_STORE', 'MEDICAL_DISTRIBUTOR', 'PATHLAB');
DELETE FROM feature_business_type
WHERE feature_code = 'WHATSAPP_APPOINTMENT'
  AND business_type_code IN ('RETAIL', 'PHARMACY', 'MEDICAL_STORE', 'MEDICAL_DISTRIBUTOR');
DELETE FROM feature_business_type
WHERE feature_code = 'AI_MEDICINE_SEARCH'
  AND business_type_code IN ('RETAIL', 'PATHLAB');
DELETE FROM feature_business_type
WHERE feature_code = 'AI_VOICE_PRESCRIPTION'
  AND business_type_code IN ('RETAIL', 'PATHLAB', 'MEDICAL_DISTRIBUTOR');

INSERT INTO feature_business_type (feature_code, business_type_code)
SELECT code, 'RETAIL' FROM feature_definition
WHERE module_code IN ('RETAIL_POS', 'RETAIL_INVENTORY', 'RETAIL_PURCHASE', 'RETAIL_CUSTOMERS', 'RETAIL_REPORTS', 'RETAIL_OPERATIONS')
ON CONFLICT DO NOTHING;
