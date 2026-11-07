package com.ebike.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.admin.request.AdminAccountCreateRequest;
import com.ebike.dto.admin.request.AdminAccountRoleUpdateRequest;
import com.ebike.dto.admin.request.AdminAccountStatusUpdateRequest;
import com.ebike.model.entity.auth.Role;
import com.ebike.model.entity.auth.User;
import com.ebike.repository.admin.AdminAuditLogRepository;
import com.ebike.repository.admin.PricingRuleRepository;
import com.ebike.repository.admin.PromotionRepository;
import com.ebike.repository.auth.RoleRepository;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.service.admin.impl.AdminServiceImpl;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** V6: an admin cannot lock themselves or the last admin out, and accounts with history are kept, not deleted. */
@ExtendWith(MockitoExtension.class)
class AdminAccountGuardTest {
    @Mock PricingRuleRepository pricingRules;
    @Mock PromotionRepository promotions;
    @Mock AdminAuditLogRepository audits;
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock PasswordEncoder encoder;
    @Mock OrderRepository orders;
    AdminServiceImpl service;
    User me;
    User otherAdmin;
    User customer;

    @BeforeEach void setup() {
        service = new AdminServiceImpl(pricingRules, promotions, audits, users, roles, encoder, orders);
        me = account(1L, "admin.user", "ADMIN");
        otherAdmin = account(2L, "second.admin", "ADMIN");
        customer = account(3L, "khach", "CUSTOMER");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin.user", "x"));
        lenient().when(users.findById(1L)).thenReturn(Optional.of(me));
        lenient().when(users.findById(2L)).thenReturn(Optional.of(otherAdmin));
        lenient().when(users.findById(3L)).thenReturn(Optional.of(customer));
        lenient().when(users.findAll()).thenReturn(List.of(me, otherAdmin, customer));
        lenient().when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    private User account(long id, String username, String roleName) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setActive(true);
        user.setAccountStatus("ACTIVE");
        Role role = new Role();
        role.setName(roleName);
        user.setRoles(new HashSet<>(Set.of(role)));
        return user;
    }

    private void onlyAdmin() { lenient().when(users.findAll()).thenReturn(List.of(me, customer)); }

    @Test void anAdminCannotDeleteOrDeactivateOrDemoteThemselves() {
        assertThrows(ResponseStatusException.class, () -> service.deleteAccount(1L));
        assertThrows(ResponseStatusException.class, () -> service.updateAccountStatus(1L, new AdminAccountStatusUpdateRequest("INACTIVE")));
        assertThrows(ResponseStatusException.class, () -> service.updateAccountRole(1L, new AdminAccountRoleUpdateRequest("CUSTOMER")));
        verify(users, never()).delete(any());
        verify(users, never()).save(any());
    }

    @Test void theLastActiveAdminCannotBeRemovedByAnotherAdmin() {
        // "me" is the only admin; a second admin session (not "me") tries to remove them
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("someone.else", "x"));
        onlyAdmin();
        assertThrows(ResponseStatusException.class, () -> service.deleteAccount(1L));
        assertThrows(ResponseStatusException.class, () -> service.updateAccountStatus(1L, new AdminAccountStatusUpdateRequest("LOCKED")));
        assertThrows(ResponseStatusException.class, () -> service.updateAccountRole(1L, new AdminAccountRoleUpdateRequest("CUSTOMER")));
        verify(users, never()).delete(any());
    }

    @Test void anotherAdminCanBeDeactivatedWhileOneStaysActive() {
        service.updateAccountStatus(2L, new AdminAccountStatusUpdateRequest("INACTIVE"));
        assertEquals("INACTIVE", otherAdmin.getAccountStatus());
    }

    @Test void anAccountWithOrdersIsKeptNotDeleted() {
        when(orders.existsByUserId(3L)).thenReturn(true);
        var ex = assertThrows(ResponseStatusException.class, () -> service.deleteAccount(3L));
        assertEquals(400, ex.getStatusCode().value());
        verify(users, never()).delete(any());
    }

    @Test void anAccountWithoutOrdersCanBeDeleted() {
        when(orders.existsByUserId(3L)).thenReturn(false);
        service.deleteAccount(3L);
        verify(users).delete(customer);
    }

    @Test void managersCanBeCreatedAndNeedAPassword() {
        Role manager = new Role();
        manager.setName("MANAGER");
        when(roles.findByName("MANAGER")).thenReturn(Optional.of(manager));
        when(encoder.encode("MatKhau@123")).thenReturn("hash");
        var created = service.createAccount(new AdminAccountCreateRequest("Quản Lý", "ql", "ql@example.com", "MatKhau@123", "MANAGER", "ACTIVE", true));
        assertEquals("MANAGER", created.role());

        assertThrows(ResponseStatusException.class,
            () -> service.createAccount(new AdminAccountCreateRequest("Không Mật Khẩu", "km", "km@example.com", null, "MANAGER", "ACTIVE", true)));
        assertThrows(ResponseStatusException.class,
            () -> service.createAccount(new AdminAccountCreateRequest("Mật Khẩu Ngắn", "mk", "mk@example.com", "123", "MANAGER", "ACTIVE", true)));
    }
}
