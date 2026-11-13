package com.ebike.service.auth;

import com.ebike.model.entity.auth.Permission;
import com.ebike.model.entity.auth.Role;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.auth.UserPermission;
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
}
