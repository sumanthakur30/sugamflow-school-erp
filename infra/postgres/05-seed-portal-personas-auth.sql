-- authdb: TEACHER + PARENT personas for multi-school portal load (password = password)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO auth_account (shop_id, tenant_id, username, email, password_hash, role)
VALUES
    ('demo-school', 100, 'teacher_demo-school', 'teacher@demo-school.local', crypt('password', gen_salt('bf', 10)), 'TEACHER'),
    ('demo-school', 100, 'parent_demo-school', 'parent@demo-school.local', crypt('password', gen_salt('bf', 10)), 'PARENT'),
    ('load-school-01', 201, 'teacher_load-school-01', 'teacher@load-school-01.local', crypt('password', gen_salt('bf', 10)), 'TEACHER'),
    ('load-school-01', 201, 'parent_load-school-01', 'parent@load-school-01.local', crypt('password', gen_salt('bf', 10)), 'PARENT'),
    ('load-school-02', 202, 'teacher_load-school-02', 'teacher@load-school-02.local', crypt('password', gen_salt('bf', 10)), 'TEACHER'),
    ('load-school-02', 202, 'parent_load-school-02', 'parent@load-school-02.local', crypt('password', gen_salt('bf', 10)), 'PARENT'),
    ('load-school-03', 203, 'teacher_load-school-03', 'teacher@load-school-03.local', crypt('password', gen_salt('bf', 10)), 'TEACHER'),
    ('load-school-03', 203, 'parent_load-school-03', 'parent@load-school-03.local', crypt('password', gen_salt('bf', 10)), 'PARENT'),
    ('load-school-04', 204, 'teacher_load-school-04', 'teacher@load-school-04.local', crypt('password', gen_salt('bf', 10)), 'TEACHER'),
    ('load-school-04', 204, 'parent_load-school-04', 'parent@load-school-04.local', crypt('password', gen_salt('bf', 10)), 'PARENT'),
    ('load-school-05', 205, 'teacher_load-school-05', 'teacher@load-school-05.local', crypt('password', gen_salt('bf', 10)), 'TEACHER'),
    ('load-school-05', 205, 'parent_load-school-05', 'parent@load-school-05.local', crypt('password', gen_salt('bf', 10)), 'PARENT')
ON CONFLICT (username) DO UPDATE SET
    shop_id = EXCLUDED.shop_id,
    tenant_id = EXCLUDED.tenant_id,
    password_hash = EXCLUDED.password_hash,
    role = EXCLUDED.role,
    email = EXCLUDED.email;

SELECT shop_id, username, role
FROM auth_account
WHERE username LIKE 'teacher_%' OR username LIKE 'parent_%'
ORDER BY shop_id, role, username;
