-- =====================================================
-- Auth seed data for login and role-based testing
-- =====================================================

INSERT INTO ebike_auth.roles (name, description)
VALUES
    ('CUSTOMER', 'Customer account'),
    ('STAFF', 'Store staff account'),
    ('MANAGER', 'Store manager account'),
    ('ADMIN', 'System administrator account')
ON CONFLICT (name) DO NOTHING;

INSERT INTO ebike_auth.permissions (code, name, module_name, action_name, description)
VALUES
    ('PRODUCT_VIEW', 'View products', 'PRODUCT', 'VIEW', 'Allows viewing product catalog'),
    ('ORDER_VIEW', 'View orders', 'ORDER', 'VIEW', 'Allows viewing orders'),
    ('ORDER_UPDATE', 'Update orders', 'ORDER', 'UPDATE', 'Allows updating order status'),
    ('USER_MANAGE', 'Manage users', 'USER', 'MANAGE', 'Allows managing user accounts'),
    ('SYSTEM_MANAGE', 'Manage system', 'SYSTEM', 'MANAGE', 'Allows system-wide administration')
ON CONFLICT (code) DO NOTHING;

INSERT INTO ebike_auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ebike_auth.roles r
JOIN ebike_auth.permissions p
    ON (r.name = 'CUSTOMER' AND p.code IN ('PRODUCT_VIEW', 'ORDER_VIEW'))
    OR (r.name = 'STAFF' AND p.code IN ('PRODUCT_VIEW', 'ORDER_VIEW', 'ORDER_UPDATE'))
    OR (r.name = 'MANAGER' AND p.code IN ('PRODUCT_VIEW', 'ORDER_VIEW', 'ORDER_UPDATE', 'USER_MANAGE'))
    OR (r.name = 'ADMIN' AND p.code IN ('PRODUCT_VIEW', 'ORDER_VIEW', 'ORDER_UPDATE', 'USER_MANAGE', 'SYSTEM_MANAGE'))
ON CONFLICT DO NOTHING;

INSERT INTO ebike_auth.users (
    username,
    email,
    password_hash,
    first_name,
    last_name,
    is_active,
    is_verified,
    email_verified_at
)
SELECT
    'staff.user',
    'staff.user@example.com',
    '$2a$10$ARf83f/Yua3BCpRaBm8U6u83qmK9mQvlCmYhV60v8oz00L5xUjExa',
    'Store',
    'Staff',
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM ebike_auth.users WHERE username = 'staff.user' OR email = 'staff.user@example.com'
);

INSERT INTO ebike_auth.users (
    username,
    email,
    password_hash,
    first_name,
    last_name,
    is_active,
    is_verified,
    email_verified_at
)
SELECT
    'manager.user',
    'manager.user@example.com',
    '$2a$10$tRiY3ac6Bnx5EHBkjP5ek.ofn3urim7dE36HV4uY3MpPWPficRNoy',
    'Store',
    'Manager',
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM ebike_auth.users WHERE username = 'manager.user' OR email = 'manager.user@example.com'
);

INSERT INTO ebike_auth.users (
    username,
    email,
    password_hash,
    first_name,
    last_name,
    is_active,
    is_verified,
    email_verified_at
)
SELECT
    'admin.user',
    'admin.user@example.com',
    '$2a$10$jLEp29fpNpvnAQDGZppNJeZpILS2AQXQiJunqbwJhjI3cBpDaF5Ba',
    'System',
    'Admin',
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM ebike_auth.users WHERE username = 'admin.user' OR email = 'admin.user@example.com'
);

INSERT INTO ebike_auth.user_roles (user_id, role_id)
SELECT u.id, r.id
FROM ebike_auth.users u
JOIN ebike_auth.roles r ON r.name = 'CUSTOMER'
WHERE u.username = 'johndoe'
ON CONFLICT DO NOTHING;

INSERT INTO ebike_auth.user_roles (user_id, role_id)
SELECT u.id, r.id
FROM ebike_auth.users u
JOIN ebike_auth.roles r ON r.name = 'STAFF'
WHERE u.username = 'staff.user'
ON CONFLICT DO NOTHING;

INSERT INTO ebike_auth.user_roles (user_id, role_id)
SELECT u.id, r.id
FROM ebike_auth.users u
JOIN ebike_auth.roles r ON r.name = 'MANAGER'
WHERE u.username = 'manager.user'
ON CONFLICT DO NOTHING;

INSERT INTO ebike_auth.user_roles (user_id, role_id)
SELECT u.id, r.id
FROM ebike_auth.users u
JOIN ebike_auth.roles r ON r.name = 'ADMIN'
WHERE u.username = 'admin.user'
ON CONFLICT DO NOTHING;
