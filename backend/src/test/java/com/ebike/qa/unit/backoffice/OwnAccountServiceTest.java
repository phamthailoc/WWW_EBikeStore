package com.ebike.qa.unit.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.auth.request.DeleteOwnAccountRequest;
import com.ebike.model.entity.auth.User;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.service.admin.AccountErasure;
import com.ebike.service.admin.AuditLogService;
import com.ebike.service.user.OwnAccountService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class OwnAccountServiceTest {

    UserRepository users = mock(UserRepository.class);
    OrderRepository orders = mock(OrderRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    AccountErasure erasure = mock(AccountErasure.class);
    AuditLogService audit = mock(AuditLogService.class);
    OwnAccountService service;
    User me;

    @BeforeEach
    void setUp() {
        service = new OwnAccountService(users, orders, encoder, erasure, audit);
        me = Fx.user(5L, "tien", "tien@example.com", "CUSTOMER");
        when(users.findByUsername("tien")).thenReturn(Optional.of(me));
        when(encoder.matches("secret1", me.getPasswordHash())).thenReturn(true);
    }

    private static void assertStatus(Throwable t, HttpStatus status) {
        assertThat(t).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(status.value());
    }

    private static DeleteOwnAccountRequest body(String pw, String email) {
        return new DeleteOwnAccountRequest(pw, email);
    }

    @Test
    void happyPath_erasesAsOwner_savesUser_auditsByNumberOnly() {
        service.deleteOwnAccount(Fx.auth("tien"), body("secret1", "  TIEN@example.com "));
        verify(erasure).erase(me, true);
        verify(users).save(me);
        ArgumentCaptor<String> actor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> target = ArgumentCaptor.forClass(String.class);
        verify(audit).recordBy(actor.capture(), anyString(), target.capture());
        assertThat(actor.getValue()).isEqualTo("tài khoản #5").doesNotContain("tien");
        assertThat(target.getValue()).isEqualTo("tài khoản #5");
    }

    @Test
    void emailIdentifiedPrincipal_isAccepted() {
        when(users.findByUsername("tien@example.com")).thenReturn(Optional.empty());
        when(users.findByEmail("tien@example.com")).thenReturn(Optional.of(me));
        service.deleteOwnAccount(Fx.auth("tien@example.com"), body("secret1", "tien@example.com"));
        verify(erasure).erase(me, true);
    }

    @Test
    void notSignedIn_is401() {
        assertThatThrownBy(() -> service.deleteOwnAccount(null, body("secret1", "tien@example.com"))).satisfies(t -> assertStatus(t, HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> service.deleteOwnAccount(Fx.auth("ghost"), body("x", "y"))).satisfies(t -> assertStatus(t, HttpStatus.UNAUTHORIZED));
        verify(erasure, never()).erase(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void staffAccounts_cannotUseSelfService_403() {
        for (String role : List.of("ADMIN", "MANAGER", "SUPPORT", "OPERATOR")) {
            me.getRoles().add(Fx.role(role));
            assertThatThrownBy(() -> service.deleteOwnAccount(Fx.auth("tien"), body("secret1", "tien@example.com")))
                .as(role).satisfies(t -> assertStatus(t, HttpStatus.FORBIDDEN));
            me.getRoles().removeIf(r -> r.getName().equals(role));
        }
        verify(erasure, never()).erase(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void alreadyErased_is400() {
        me.setAccountStatus("DELETED");
        assertThatThrownBy(() -> service.deleteOwnAccount(Fx.auth("tien"), body("secret1", "tien@example.com"))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void wrongOrMissingPassword_is400_not401_andNothingErased() {
        assertThatThrownBy(() -> service.deleteOwnAccount(Fx.auth("tien"), body("wrong", "tien@example.com"))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.deleteOwnAccount(Fx.auth("tien"), body(null, "tien@example.com"))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.deleteOwnAccount(Fx.auth("tien"), null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(erasure, never()).erase(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void wrongOrMissingConfirmEmail_is400() {
        assertThatThrownBy(() -> service.deleteOwnAccount(Fx.auth("tien"), body("secret1", "other@example.com"))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.deleteOwnAccount(Fx.auth("tien"), body("secret1", null))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.deleteOwnAccount(Fx.auth("tien"), body("secret1", ""))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(erasure, never()).erase(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void openOrders_blockErasure() {
        when(orders.existsByUserIdAndStatusIn(anyLong(), anyList())).thenReturn(true);
        assertThatThrownBy(() -> service.deleteOwnAccount(Fx.auth("tien"), body("secret1", "tien@example.com"))).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(erasure, never()).erase(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @SuppressWarnings("unchecked")
    void openOrderStatuses_areEveryNonFinalOne_deliveredAndCancelledAreFinal() {
        service.deleteOwnAccount(Fx.auth("tien"), body("secret1", "tien@example.com"));
        ArgumentCaptor<List<com.ebike.model.entity.order.OrderStatus>> statuses = ArgumentCaptor.forClass(List.class);
        verify(orders).existsByUserIdAndStatusIn(anyLong(), statuses.capture());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(
            com.ebike.model.entity.order.OrderStatus.PENDING, com.ebike.model.entity.order.OrderStatus.CONFIRMED,
            com.ebike.model.entity.order.OrderStatus.PROCESSING, com.ebike.model.entity.order.OrderStatus.READY_FOR_PICKUP,
            com.ebike.model.entity.order.OrderStatus.SHIPPED);
    }

    @Test
    void erasureRefusal_propagates_andNoAuditLineIsWritten() {
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "open instalment")).when(erasure).erase(me, true);
        assertThatThrownBy(() -> service.deleteOwnAccount(Fx.auth("tien"), body("secret1", "tien@example.com"))).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        verify(audit, never()).recordBy(any(), any(), any());
        verify(users, never()).save(any());
    }
}
