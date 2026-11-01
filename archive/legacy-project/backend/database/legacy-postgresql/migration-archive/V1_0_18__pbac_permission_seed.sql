-- =====================================================
-- PBAC permission seed aligned with backend PermissionConstants
-- =====================================================

INSERT INTO ebike_auth.permissions (code, name, module_name, action_name, description)
VALUES
    ('product:view', 'View products', 'PRODUCT', 'VIEW', 'View product catalog and details'),
    ('product:search', 'Search products', 'PRODUCT', 'SEARCH', 'Search and filter product catalog'),
    ('category:view', 'View categories', 'CATEGORY', 'VIEW', 'View product categories'),
    ('review:view', 'View reviews', 'REVIEW', 'VIEW', 'View product reviews'),

    ('cart:view', 'View cart', 'CART', 'VIEW', 'View own cart'),
    ('cart:update', 'Update cart', 'CART', 'UPDATE', 'Add, update, and remove own cart items'),
    ('order:create', 'Create orders', 'ORDER', 'CREATE', 'Create own orders'),
    ('order:view-own', 'View own orders', 'ORDER', 'VIEW_OWN', 'View orders owned by the current user'),
    ('order:cancel-own', 'Cancel own orders', 'ORDER', 'CANCEL_OWN', 'Cancel own pending orders'),
    ('payment:create', 'Create payments', 'PAYMENT', 'CREATE', 'Create payment requests for own orders'),
    ('payment:view-own', 'View own payments', 'PAYMENT', 'VIEW_OWN', 'View own payment information'),
    ('favorite:view', 'View favorites', 'FAVORITE', 'VIEW', 'View own favorite products'),
    ('favorite:update', 'Update favorites', 'FAVORITE', 'UPDATE', 'Add and remove own favorite products'),
    ('profile:view', 'View profile', 'PROFILE', 'VIEW', 'View own profile and preferences'),
    ('profile:update', 'Update profile', 'PROFILE', 'UPDATE', 'Update own profile, addresses, and preferences'),
    ('review:create', 'Create reviews', 'REVIEW', 'CREATE', 'Create product reviews'),

    ('product:create', 'Create products', 'PRODUCT', 'CREATE', 'Create products and product images'),
    ('product:update', 'Update products', 'PRODUCT', 'UPDATE', 'Update products, stock, and product images'),
    ('product:delete', 'Delete products', 'PRODUCT', 'DELETE', 'Delete products and product images'),
    ('product:manage-stock', 'Manage stock', 'PRODUCT', 'MANAGE_STOCK', 'Manage product stock quantities'),
    ('category:manage', 'Manage categories', 'CATEGORY', 'MANAGE', 'Create, update, and delete product categories'),
    ('review:moderate', 'Moderate reviews', 'REVIEW', 'MODERATE', 'Moderate product reviews'),

    ('order:view-all', 'View all orders', 'ORDER', 'VIEW_ALL', 'View all customer orders'),
    ('order:update-status', 'Update order status', 'ORDER', 'UPDATE_STATUS', 'Update order fulfillment status'),
    ('order:assign-shipment', 'Assign shipment', 'ORDER', 'ASSIGN_SHIPMENT', 'Assign and update order shipment information'),
    ('payment:verify', 'Verify payments', 'PAYMENT', 'VERIFY', 'Verify payment results and callbacks'),
    ('payment:refund', 'Refund payments', 'PAYMENT', 'REFUND', 'Refund customer payments'),
    ('shipment:manage', 'Manage shipments', 'SHIPMENT', 'MANAGE', 'Manage shipment lifecycle'),

    ('user:view', 'View users', 'USER', 'VIEW', 'View customer account data'),
    ('user:manage', 'Manage users', 'USER', 'MANAGE', 'Manage user accounts'),
    ('role:manage', 'Manage roles', 'ROLE', 'MANAGE', 'Manage role assignments'),
    ('permission:manage', 'Manage permissions', 'PERMISSION', 'MANAGE', 'Manage permissions and direct user grants'),

    ('chatbot:use', 'Use chatbot', 'CHATBOT', 'USE', 'Ask chatbot questions'),
    ('chatbot:view-history', 'View chatbot history', 'CHATBOT', 'VIEW_HISTORY', 'View chatbot conversation history'),
    ('chatbot:manage-faq', 'Manage chatbot FAQ', 'CHATBOT', 'MANAGE_FAQ', 'Manage FAQ knowledge base'),
    ('chatbot:manage-knowledge', 'Manage chatbot knowledge', 'CHATBOT', 'MANAGE_KNOWLEDGE', 'Manage chatbot product knowledge'),
    ('chatbot:configure', 'Configure chatbot', 'CHATBOT', 'CONFIGURE', 'Configure chatbot providers and behavior'),

    ('dashboard:view', 'View dashboard', 'DASHBOARD', 'VIEW', 'View management dashboards'),
    ('report:view', 'View reports', 'REPORT', 'VIEW', 'View business reports'),
    ('system:config', 'Configure system', 'SYSTEM', 'CONFIG', 'Configure system settings'),
    ('audit-log:view', 'View audit logs', 'AUDIT_LOG', 'VIEW', 'View audit and authentication logs')
ON CONFLICT (code) DO UPDATE
SET
    name = EXCLUDED.name,
    module_name = EXCLUDED.module_name,
    action_name = EXCLUDED.action_name,
    description = EXCLUDED.description;

INSERT INTO ebike_auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ebike_auth.roles r
JOIN ebike_auth.permissions p ON (
    (r.name = 'CUSTOMER' AND p.code IN (
        'product:view',
        'product:search',
        'category:view',
        'review:view',
        'cart:view',
        'cart:update',
        'order:create',
        'order:view-own',
        'order:cancel-own',
        'payment:create',
        'payment:view-own',
        'favorite:view',
        'favorite:update',
        'profile:view',
        'profile:update',
        'review:create',
        'chatbot:use',
        'chatbot:view-history'
    ))
    OR (r.name = 'STAFF' AND p.code IN (
        'product:view',
        'product:search',
        'category:view',
        'review:view',
        'product:update',
        'product:manage-stock',
        'review:moderate',
        'order:view-all',
        'order:update-status',
        'order:assign-shipment',
        'payment:verify',
        'shipment:manage',
        'user:view',
        'chatbot:use',
        'chatbot:view-history',
        'dashboard:view'
    ))
    OR (r.name = 'MANAGER' AND p.code IN (
        'product:view',
        'product:search',
        'category:view',
        'review:view',
        'product:create',
        'product:update',
        'product:delete',
        'product:manage-stock',
        'category:manage',
        'review:moderate',
        'order:view-all',
        'order:update-status',
        'order:assign-shipment',
        'payment:verify',
        'payment:refund',
        'shipment:manage',
        'user:view',
        'user:manage',
        'chatbot:use',
        'chatbot:view-history',
        'chatbot:manage-faq',
        'chatbot:manage-knowledge',
        'dashboard:view',
        'report:view'
    ))
    OR (r.name = 'ADMIN' AND p.code IN (
        'product:view',
        'product:search',
        'category:view',
        'review:view',
        'cart:view',
        'cart:update',
        'order:create',
        'order:view-own',
        'order:cancel-own',
        'payment:create',
        'payment:view-own',
        'favorite:view',
        'favorite:update',
        'profile:view',
        'profile:update',
        'review:create',
        'product:create',
        'product:update',
        'product:delete',
        'product:manage-stock',
        'category:manage',
        'review:moderate',
        'order:view-all',
        'order:update-status',
        'order:assign-shipment',
        'payment:verify',
        'payment:refund',
        'shipment:manage',
        'user:view',
        'user:manage',
        'role:manage',
        'permission:manage',
        'chatbot:use',
        'chatbot:view-history',
        'chatbot:manage-faq',
        'chatbot:manage-knowledge',
        'chatbot:configure',
        'dashboard:view',
        'report:view',
        'system:config',
        'audit-log:view'
    ))
)
ON CONFLICT DO NOTHING;
