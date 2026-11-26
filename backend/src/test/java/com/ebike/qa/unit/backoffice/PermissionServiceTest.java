package com.ebike.qa.unit.backoffice;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebike.model.entity.auth.Permission;
import com.ebike.model.entity.auth.Role;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.auth.UserPermission;
import com.ebike.service.auth.PermissionService;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PermissionServiceTest {

    PermissionService service = new PermissionService();

    private static Permission perm(String code) {
        Permission p = new Permission();
        p.setCode(code);
        return p;
    }

    private static UserPermission override(User user, String code, Boolean granted) {
        UserPermission up = new UserPermission();
        up.setUser(user);
        up.setPermission(perm(code));
        up.setGranted(granted);
        user.getUserPermissions().add(up);
        return up;
    }

    @Test
    void noRolesNoOverrides_isEmpty() {
        User u = Fx.user(1L, "a", "a@b.com");
        assertThat(service.resolvePermissionCodes(u)).isEmpty();
    }

    @Test
    void permissionsOfAllRoles_areMerged() {
        User u = Fx.user(1L, "a", "a@b.com");
        u.setRoles(Set.of(Fx.role("CUSTOMER", "a", "b"), Fx.role("MANAGER", "b", "c")));
        assertThat(service.resolvePermissionCodes(u)).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void directGrant_addsToRolePermissions() {
        User u = Fx.user(1L, "a", "a@b.com");
        u.setRoles(Set.of(Fx.role("CUSTOMER", "a")));
        override(u, "extra", true);
        assertThat(service.resolvePermissionCodes(u)).containsExactlyInAnyOrder("a", "extra");
    }

    @Test
    void directDenial_removesRolePermission() {
        User u = Fx.user(1L, "a", "a@b.com");
        u.setRoles(Set.of(Fx.role("MANAGER", "a", "b")));
        override(u, "b", false);
        assertThat(service.resolvePermissionCodes(u)).containsExactly("a");
    }

    @Test
    void denialWins_whenSamePermissionGrantedByRoleAndDeniedDirectly() {
        User u = Fx.user(1L, "a", "a@b.com");
        u.setRoles(Set.of(Fx.role("ADMIN", "role:manage")));
        override(u, "role:manage", false);
        assertThat(service.resolvePermissionCodes(u)).isEmpty();
    }

    @Test
    void denialOfPermissionTheUserNeverHad_isHarmless() {
        User u = Fx.user(1L, "a", "a@b.com");
        u.setRoles(Set.of(Fx.role("CUSTOMER", "a")));
        override(u, "zzz", false);
        assertThat(service.resolvePermissionCodes(u)).containsExactly("a");
    }

    @Test
    void resultIsACopy_mutatingItDoesNotChangeTheRole() {
        Role role = Fx.role("CUSTOMER", "a");
        User u = Fx.user(1L, "a", "a@b.com");
        u.setRoles(Set.of(role));
        service.resolvePermissionCodes(u).add("hacked");
        assertThat(role.getPermissions()).extracting(Permission::getCode).containsExactly("a");
    }
}
