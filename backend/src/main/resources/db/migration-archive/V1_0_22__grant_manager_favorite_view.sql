-- =====================================================
-- Grant managers permission to view favorites
-- =====================================================

INSERT INTO ebike_auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM ebike_auth.roles r
JOIN ebike_auth.permissions p ON p.code = 'favorite:view'
WHERE r.name = 'MANAGER'
ON CONFLICT DO NOTHING;
