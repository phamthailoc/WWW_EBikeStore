package com.ebike.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.auth.request.ChangePasswordRequest;
import com.ebike.model.entity.auth.User;
import com.ebike.repository.auth.AuthenticationLogRepository;
import com.ebike.repository.auth.RoleRepository;
import com.ebike.repository.auth.UserRepository;
import com.ebike.service.auth.impl.AuthenticationServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

/** V4: changing the password needs the current one, a confirmed new one, and a new one that is really new. */
@ExtendWith(MockitoExtension.class)
class ChangePasswordTest {
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock AuthenticationLogRepository logs;
    @Mock PermissionService permissions;
    @Mock PasswordEncoder encoder;
    @Mock JwtTokenProvider tokens;
    AuthenticationServiceImpl service;
    User user;

    @BeforeEach void setup() {
        service = new AuthenticationServiceImpl(users, roles, logs, permissions, encoder, tokens, org.mockito.Mockito.mock(com.ebike.service.admin.AuditLogService.class));
        user = new User();
        user.setUsername("an");
        user.setPasswordHash("hash");
        org.mockito.Mockito.lenient().when(users.findByEmail("an")).thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(users.findByUsername("an")).thenReturn(Optional.of(user));
    }

    @Test void aWrongCurrentPasswordIsRefused() {
        when(encoder.matches("sai-roi", "hash")).thenReturn(false);
        assertThrows(ResponseStatusException.class,
            () -> service.changePassword("an", new ChangePasswordRequest("sai-roi", "matkhau-moi", "matkhau-moi")));
        verify(users, never()).save(any());
    }

    @Test void theNewPasswordMustDifferFromTheCurrentOne() {
        var ex = assertThrows(ResponseStatusException.class,
            () -> service.changePassword("an", new ChangePasswordRequest("matkhau-cu", "matkhau-cu", "matkhau-cu")));
        assertEquals(400, ex.getStatusCode().value());
        verify(users, never()).save(any());
    }

    @Test void aValidChangeStoresTheNewHash() {
        when(encoder.matches("matkhau-cu", "hash")).thenReturn(true);
        when(encoder.encode("matkhau-moi")).thenReturn("hash-moi");
        service.changePassword("an", new ChangePasswordRequest("matkhau-cu", "matkhau-moi", "matkhau-moi"));
        assertEquals("hash-moi", user.getPasswordHash());
        verify(users).save(user);
    }
}
