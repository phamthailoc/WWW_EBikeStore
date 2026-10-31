INSERT INTO ebike_auth.permissions (code, name, module_name, action_name, description)
VALUES
    ('cart:view', 'View own cart', 'CART', 'VIEW', 'Allows customers to view their own cart'),
    ('cart:update', 'Update own cart', 'CART', 'UPDATE', 'Allows customers to update their own cart'),
    ('order:create', 'Create own orders', 'ORDER', 'CREATE', 'Allows customers to create orders'),
    ('order:view-own', 'View own orders', 'ORDER', 'VIEW_OWN', 'Allows customers to view their own orders'),
    ('order:cancel-own', 'Cancel own orders', 'ORDER', 'CANCEL_OWN', 'Allows customers to cancel their own orders'),
    ('payment:create', 'Create own payments', 'PAYMENT', 'CREATE', 'Allows customers to create payments for their own orders'),
    ('payment:view-own', 'View own payments', 'PAYMENT', 'VIEW_OWN', 'Allows customers to view their own payments'),
    ('profile:view', 'View own profile', 'PROFILE', 'VIEW', 'Allows customers to view their own profile'),
    ('profile:update', 'Update own profile', 'PROFILE', 'UPDATE', 'Allows customers to update their own profile'),
    ('review:create', 'Create product reviews', 'REVIEW', 'CREATE', 'Allows customers to create product reviews')
ON CONFLICT (code) DO NOTHING;

INSERT INTO ebike_auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ebike_auth.roles r
JOIN ebike_auth.permissions p
    ON p.code IN (
        'PRODUCT_VIEW',
        'ORDER_VIEW',
        'cart:view',
        'cart:update',
        'order:create',
        'order:view-own',
        'order:cancel-own',
        'payment:create',
        'payment:view-own',
        'profile:view',
        'profile:update',
        'review:create'
    )
WHERE r.name = 'CUSTOMER'
ON CONFLICT DO NOTHING;
