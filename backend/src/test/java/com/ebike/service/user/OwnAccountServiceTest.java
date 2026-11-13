package com.ebike.service.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.auth.request.DeleteOwnAccountRequest;
import com.ebike.model.entity.auth.Role;
import com.ebike.model.entity.auth.User;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.service.admin.AccountErasure;
import com.ebike.service.admin.AuditLogService;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** V6: a customer erases their own account only with the password, the typed email and no order still open. */
@ExtendWith(MockitoExtension.class)
class OwnAccountServiceTest {
    @Mock UserRepository users;
    @Mock OrderRepository orders;
    @Mock PasswordEncoder encoder;
    @Mock AccountErasure erasure;
    @Mock AuditLogService auditLog;
    OwnAccountService service;
    User customer;
    TestingAuthenticationToken auth = new TestingAuthenticationToken("khach", "x");

    @BeforeEach void setup() {
        service = new OwnAccountService(users, orders, encoder, erasure, auditLog);
        customer = new User();
        ReflectionTestUtils.setField(customer, "id", 7L);
        customer.setUsername("khach");
        customer.setEmail("khach@example.com");
        customer.setPasswordHash("hash");
        customer.setAccountStatus("ACTIVE");
        Role role = new Role();
        role.setName("CUSTOMER");
        customer.setRoles(new HashSet<>(Set.of(role)));
        org.mockito.Mockito.lenient().when(users.findByUsername("khach")).thenReturn(Optional.of(customer));
        org.mockito.Mockito.lenient().when(encoder.matches("dung", "hash")).thenReturn(true);
    }

    private void refused(DeleteOwnAccountRequest request, int status) {
        var ex = assertThrows(ResponseStatusException.class, () -> service.deleteOwnAccount(auth, request));
        assertEquals(status, ex.getStatusCode().value());
        verify(erasure, never()).erase(any());
        verify(users, never()).save(any());
    }

    @Test void aWrongPasswordIsRefused() {
        refused(new DeleteOwnAccountRequest("sai", "khach@example.com"), 400);
    }

    @Test void theEmailMustBeTypedExactly() {
        refused(new DeleteOwnAccountRequest("dung", "nguoi.khac@example.com"), 400);
        refused(new DeleteOwnAccountRequest("dung", null), 400);
    }

    @Test void anOrderStillOpenBlocksTheDeletion() {
        when(orders.existsByUserIdAndStatusIn(org.mockito.ArgumentMatchers.eq(7L), any())).thenReturn(true);
        refused(new DeleteOwnAccountRequest("dung", "khach@example.com"), 400);
    }

    @Test void staffAccountsCannotUseIt() {
        Role manager = new Role();
        manager.setName("MANAGER");
        customer.setRoles(new HashSet<>(Set.of(manager)));
        refused(new DeleteOwnAccountRequest("dung", "khach@example.com"), 403);
    }

    @Test void withEverythingRightTheAccountIsErasedAndLoggedByNumber() {
        service.deleteOwnAccount(auth, new DeleteOwnAccountRequest("dung", " KHACH@example.com "));
        verify(erasure).erase(customer);
        verify(users).save(customer);
        verify(auditLog).recordBy("tài khoản #7", "khách tự xóa tài khoản", "tài khoản #7");
    }
}
