package com.ebike.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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
import com.ebike.repository.testride.TestRideBookingRepository;
import com.ebike.repository.warranty.WarrantyClaimRepository;
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

/** V6: an admin cannot lock themselves or the last admin out, and a deleted account is anonymised, never removed. */
@ExtendWith(MockitoExtension.class)
class AdminAccountGuardTest {
    @Mock PricingRuleRepository pricingRules;
    @Mock PromotionRepository promotions;
    @Mock AdminAuditLogRepository audits;
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock PasswordEncoder encoder;
    @Mock OrderRepository orders;
    @Mock com.ebike.repository.user.UserAddressRepository addresses;
    @Mock com.ebike.repository.user.UserFavoriteRepository favorites;
    @Mock com.ebike.repository.user.UserPreferenceRepository preferences;
    @Mock com.ebike.repository.notification.NotificationRepository notifications;
    @Mock com.ebike.repository.product.StockAlertRepository stockAlerts;
    @Mock com.ebike.service.product.ProductReviewService reviews;
    @Mock com.ebike.service.admin.AuditLogService auditLog;
    @Mock com.ebike.repository.auth.AuthenticationLogRepository authLogs;
    @Mock com.ebike.repository.testride.TestRideBookingRepository testRidesRepo;
    AdminServiceImpl service;
    User me;
    User otherAdmin;
    User customer;

    @BeforeEach void setup() {
        service = new AdminServiceImpl(pricingRules, promotions, audits, users, roles, encoder, orders,
            authLogs, testRidesRepo, reviews,
            auditLog);
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
        ReflectionTestUtils.setField(user, "createdAt", java.time.LocalDateTime.now().minusDays(id));
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
        assertThrows(ResponseStatusException.class, () -> service.deleteAccount(1L, "admin.user@example.com"));
        assertThrows(ResponseStatusException.class, () -> service.updateAccountStatus(1L, new AdminAccountStatusUpdateRequest("INACTIVE")));
        assertThrows(ResponseStatusException.class, () -> service.updateAccountRole(1L, new AdminAccountRoleUpdateRequest("CUSTOMER")));
        verify(users, never()).delete(any());
        verify(users, never()).save(any());
    }

    @Test void theLastActiveAdminCannotBeRemovedByAnotherAdmin() {
        // "me" is the only admin; a second admin session (not "me") tries to remove them
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("someone.else", "x"));
        onlyAdmin();
        assertThrows(ResponseStatusException.class, () -> service.deleteAccount(1L, "admin.user@example.com"));
        assertThrows(ResponseStatusException.class, () -> service.updateAccountStatus(1L, new AdminAccountStatusUpdateRequest("LOCKED")));
        assertThrows(ResponseStatusException.class, () -> service.updateAccountRole(1L, new AdminAccountRoleUpdateRequest("CUSTOMER")));
        verify(users, never()).delete(any());
    }

    @Test void anotherAdminCanBeDeactivatedWhileOneStaysActive() {
        service.updateAccountStatus(2L, new AdminAccountStatusUpdateRequest("INACTIVE"));
        assertEquals("INACTIVE", otherAdmin.getAccountStatus());
    }

    @Test void anAccountWithOrdersIsNotDeleted() {
        customer.setEmail("khach@example.com");
        when(orders.existsByUserId(3L)).thenReturn(true);
        var ex = assertThrows(ResponseStatusException.class, () -> service.deleteAccount(3L, "KHACH@example.com"));
        assertEquals(409, ex.getStatusCode().value());
        assertEquals("ACTIVE", customer.getAccountStatus());
        verify(users, never()).delete(any());
        verify(authLogs, never()).deleteByUserId(any());
    }

    @Test void anAccountWithoutOrdersIsReallyDeletedWithItsSignInHistory() {
        customer.setEmail("khach@example.com");
        service.deleteAccount(3L, "KHACH@example.com");
        verify(reviews).removeReviewsOf(3L);
        verify(authLogs).deleteByUserId(3L);
        verify(users).delete(customer);
        verify(users).flush();
        verify(auditLog).record("xóa tài khoản", "tài khoản #3");
    }

    @Test void anAccountWithAnOpenTestRideIsNotDeleted() {
        customer.setEmail("khach@example.com");
        when(testRidesRepo.existsByUserIdAndStatusIn(any(), any())).thenReturn(true);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.deleteAccount(3L, "khach@example.com")).getStatusCode().value());
        verify(users, never()).delete(any());
    }

    @Test void searchFindsAnAccountByNameUsernameOrEmail() {
        customer.setEmail("khach@example.com");
        assertEquals(1, service.getAccounts(null, "KHACH").size());
        assertEquals(1, service.getAccounts(null, "example.com").stream().filter(a -> a.username().equals("khach")).count());
        assertEquals(0, service.getAccounts(null, "khong-co-ai").size());
    }

    @Test void updatingAnAccountChecksEmailFormatAndUniqueness() {
        customer.setEmail("khach@example.com");
        assertEquals(400, assertThrows(ResponseStatusException.class,
            () -> service.updateAccount(3L, new com.ebike.dto.admin.request.AdminAccountUpdateRequest("Khách", "khong-hop-le"))).getStatusCode().value());
        when(users.existsByEmail("trung@example.com")).thenReturn(true);
        assertEquals(409, assertThrows(ResponseStatusException.class,
            () -> service.updateAccount(3L, new com.ebike.dto.admin.request.AdminAccountUpdateRequest("Khách", "trung@example.com"))).getStatusCode().value());
        when(users.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        var detail = service.updateAccount(3L, new com.ebike.dto.admin.request.AdminAccountUpdateRequest("Lê Văn Khách", "moi@example.com"));
        assertEquals("moi@example.com", detail.email());
        assertEquals("Lê", detail.firstName());
        assertEquals("Văn Khách", detail.lastName());
    }

    @Test void aStaffAccountWithAuditEntriesCannotBeDeletedOnlyLocked() {
        when(audits.existsByActorIn(any())).thenReturn(true);
        var ex = assertThrows(ResponseStatusException.class, () -> service.deleteAccount(2L, "second.admin@example.com"));
        assertEquals(400, ex.getStatusCode().value());
        assertEquals("ACTIVE", otherAdmin.getAccountStatus());
        verify(users, never()).save(any());
    }

    @Test void deletingNeedsTheExactEmailTyped() {
        for (String typed : new String[] {null, "", "khach", "someone.else@example.com"}) {
            var ex = assertThrows(ResponseStatusException.class, () -> service.deleteAccount(3L, typed));
            assertEquals(400, ex.getStatusCode().value());
        }
        assertEquals("ACTIVE", customer.getAccountStatus());
        verify(users, never()).save(any());
        verify(reviews, never()).removeReviewsOf(any());
    }

    @Test void aDeletedAccountCannotBeChangedAgainAndIsHiddenFromTheList() {
        customer.setAccountStatus("DELETED");
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.deleteAccount(3L, "khach@example.com")).getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> service.updateAccountStatus(3L, new AdminAccountStatusUpdateRequest("ACTIVE")));
        assertThrows(ResponseStatusException.class, () -> service.updateAccountRole(3L, new AdminAccountRoleUpdateRequest("ADMIN")));
        assertEquals(2, service.getAccounts(null).size());
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
