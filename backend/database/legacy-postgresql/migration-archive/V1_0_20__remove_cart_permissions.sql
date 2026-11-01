DELETE FROM ebike_auth.role_permissions rp
USING ebike_auth.permissions p
WHERE rp.permission_id = p.id
  AND p.code IN ('cart:view', 'cart:update');

DELETE FROM ebike_auth.user_permissions up
USING ebike_auth.permissions p
WHERE up.permission_id = p.id
  AND p.code IN ('cart:view', 'cart:update');

DELETE FROM ebike_auth.permissions
WHERE code IN ('cart:view', 'cart:update');
