-- =====================================================
-- Merge legacy STAFF role into MANAGER
-- =====================================================

INSERT INTO ebike_auth.roles (name, description)
VALUES ('MANAGER', 'Store manager account')
ON CONFLICT (name) DO NOTHING;

INSERT INTO ebike_auth.user_roles (user_id, role_id)
SELECT ur.user_id, manager_role.id
FROM ebike_auth.user_roles ur
JOIN ebike_auth.roles staff_role ON staff_role.id = ur.role_id AND staff_role.name = 'STAFF'
JOIN ebike_auth.roles manager_role ON manager_role.name = 'MANAGER'
ON CONFLICT DO NOTHING;

DELETE FROM ebike_auth.user_roles ur
USING ebike_auth.roles r
WHERE ur.role_id = r.id
  AND r.name = 'STAFF';

DELETE FROM ebike_auth.role_permissions rp
USING ebike_auth.roles r
WHERE rp.role_id = r.id
  AND r.name = 'STAFF';

DELETE FROM ebike_auth.roles
WHERE name = 'STAFF';
