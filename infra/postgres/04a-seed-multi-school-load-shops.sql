-- shopdb: concurrent multi-school load tenants
INSERT INTO shops (
    shop_id, tenant_id, shop_name, owner_name, email, business_type, status,
    gst_enabled, subscription_type, subscription_status,
    registration_date, subscription_expiry, created_at, updated_at
)
VALUES
    ('load-school-01', 201, 'Load School 01', 'Load Admin 01', 'admin@load-school-01.local', 'SCHOOL', 'ACTIVE', false, 'MONTHLY', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 year', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('load-school-02', 202, 'Load School 02', 'Load Admin 02', 'admin@load-school-02.local', 'SCHOOL', 'ACTIVE', false, 'MONTHLY', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 year', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('load-school-03', 203, 'Load School 03', 'Load Admin 03', 'admin@load-school-03.local', 'SCHOOL', 'ACTIVE', false, 'MONTHLY', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 year', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('load-school-04', 204, 'Load School 04', 'Load Admin 04', 'admin@load-school-04.local', 'SCHOOL', 'ACTIVE', false, 'MONTHLY', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 year', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('load-school-05', 205, 'Load School 05', 'Load Admin 05', 'admin@load-school-05.local', 'SCHOOL', 'ACTIVE', false, 'MONTHLY', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 year', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (shop_id) DO UPDATE SET
    tenant_id = EXCLUDED.tenant_id,
    shop_name = EXCLUDED.shop_name,
    status = 'ACTIVE',
    subscription_status = 'ACTIVE',
    business_type = 'SCHOOL',
    updated_at = CURRENT_TIMESTAMP;

SELECT shop_id, shop_name, status, business_type
FROM shops
WHERE shop_id LIKE 'load-school-%'
ORDER BY shop_id;
