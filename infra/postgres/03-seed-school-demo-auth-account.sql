-- authdb: demo school admin (username scoped to org)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO auth_account (
    shop_id,
    tenant_id,
    username,
    email,
    password_hash,
    role
)
VALUES (
    'demo-school',
    100,
    'admin_demo-school',
    'admin@demo-school.local',
    crypt('password', gen_salt('bf', 10)),
    'SHOP_OWNER'
)
ON CONFLICT (username) DO UPDATE SET
    shop_id = EXCLUDED.shop_id,
    tenant_id = EXCLUDED.tenant_id,
    password_hash = EXCLUDED.password_hash,
    role = EXCLUDED.role;

SELECT shop_id, username, role FROM auth_account WHERE shop_id = 'demo-school';
