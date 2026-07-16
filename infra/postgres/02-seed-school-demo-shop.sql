-- shopdb: ACTIVE demo-school org for auth ShopLoginGuard
INSERT INTO shops (
    shop_id,
    tenant_id,
    shop_name,
    owner_name,
    email,
    business_type,
    status,
    gst_enabled,
    subscription_type,
    subscription_status,
    registration_date,
    subscription_expiry,
    created_at,
    updated_at
)
VALUES (
    'demo-school',
    100,
    'Demo School',
    'School Admin',
    'admin@demo-school.local',
    'SCHOOL',
    'ACTIVE',
    false,
    'MONTHLY',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP + INTERVAL '1 year',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (shop_id) DO UPDATE SET
    tenant_id = EXCLUDED.tenant_id,
    shop_name = EXCLUDED.shop_name,
    status = 'ACTIVE',
    subscription_status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP;

SELECT shop_id, shop_name, status FROM shops WHERE shop_id = 'demo-school';
