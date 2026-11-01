INSERT INTO ebike_auth.permissions (code, name, module_name, action_name, description)
VALUES ('order:cancel-own', 'Cancel own orders', 'ORDER', 'CANCEL_OWN', 'Request cancellation for eligible own orders')
ON CONFLICT (code) DO UPDATE
SET
    name = EXCLUDED.name,
    module_name = EXCLUDED.module_name,
    action_name = EXCLUDED.action_name,
    description = EXCLUDED.description;

INSERT INTO ebike_auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ebike_auth.roles r
JOIN ebike_auth.permissions p ON p.code = 'order:cancel-own'
WHERE r.name IN ('CUSTOMER', 'ADMIN')
ON CONFLICT DO NOTHING;
