-- authdb: admins for concurrent multi-school load tenants (password = password)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO auth_account (shop_id, tenant_id, username, email, password_hash, role)
VALUES
    ('load-school-01', 201, 'admin_load-school-01', 'admin@load-school-01.local', crypt('password', gen_salt('bf', 10)), 'SHOP_OWNER'),
    ('load-school-02', 202, 'admin_load-school-02', 'admin@load-school-02.local', crypt('password', gen_salt('bf', 10)), 'SHOP_OWNER'),
    ('load-school-03', 203, 'admin_load-school-03', 'admin@load-school-03.local', crypt('password', gen_salt('bf', 10)), 'SHOP_OWNER'),
    ('load-school-04', 204, 'admin_load-school-04', 'admin@load-school-04.local', crypt('password', gen_salt('bf', 10)), 'SHOP_OWNER'),
    ('load-school-05', 205, 'admin_load-school-05', 'admin@load-school-05.local', crypt('password', gen_salt('bf', 10)), 'SHOP_OWNER')
ON CONFLICT (username) DO UPDATE SET
    shop_id = EXCLUDED.shop_id,
    tenant_id = EXCLUDED.tenant_id,
    password_hash = EXCLUDED.password_hash,
    role = EXCLUDED.role;

SELECT shop_id, username, role
FROM auth_account
WHERE shop_id LIKE 'load-school-%'
ORDER BY shop_id;
