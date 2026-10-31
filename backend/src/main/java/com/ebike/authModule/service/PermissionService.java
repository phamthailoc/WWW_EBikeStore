package com.ebike.authModule.service;

import com.ebike.authModule.entity.Permission;
import com.ebike.authModule.entity.Role;
import com.ebike.authModule.entity.User;
import com.ebike.authModule.entity.UserPermission;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PermissionService {

    public Set<String> resolvePermissionCodes(User user) {
        Set<String> rolePermissions = user.getRoles()
            .stream()
            .map(Role::getPermissions)
            .flatMap(Set::stream)
            .map(Permission::getCode)
            .collect(Collectors.toSet());

        Set<String> directPermissions = user.getUserPermissions()
            .stream()
            .filter(UserPermission::getGranted)
            .map(UserPermission::getPermission)
            .map(Permission::getCode)
            .collect(Collectors.toSet());

        Set<String> deniedPermissions = user.getUserPermissions()
            .stream()
            .filter(userPermission -> !Boolean.TRUE.equals(userPermission.getGranted()))
            .map(UserPermission::getPermission)
            .map(Permission::getCode)
            .collect(Collectors.toSet());

        rolePermissions.addAll(directPermissions);
        rolePermissions.removeAll(deniedPermissions);
        return rolePermissions;
    }

    public boolean hasPermission(User user, String permissionCode) {
        return resolvePermissionCodes(user).contains(permissionCode);
    }
}
