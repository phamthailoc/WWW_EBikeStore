package com.ebike.service.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.order.request.GuestOrderLinkRequest;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Order;
import com.ebike.repository.admin.PromotionRepository;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.order.ShowroomRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.service.order.impl.OrderServiceImpl;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class GuestOrderLinkTest {
    @Mock OrderRepository orders;
    @Mock UserRepository users;
    @Mock ProductRepository products;
    @Mock ShowroomRepository showrooms;
    @Mock PromotionRepository promotions;
    @Mock OrderEmailVerificationService verification;
    @Mock Authentication authentication;
    OrderServiceImpl service;
    User customer;
    Order guestOrder;
    final UUID session = UUID.randomUUID();

    @BeforeEach void setup() {
        service = new OrderServiceImpl(orders, users, products, showrooms, promotions, verification);
        customer = new User();
        customer.setId(3L);
        customer.setUsername("an");
        customer.setEmail("an@example.com");
        when(authentication.getName()).thenReturn("an");
        when(users.findByUsername("an")).thenReturn(Optional.of(customer));
        guestOrder = new Order();
        guestOrder.setCustomerEmail("an@example.com");
    }

    @Test void verifiedEmailAttachesTheGuestOrders() {
        when(orders.findByUserIsNullAndCustomerEmail("an@example.com")).thenReturn(List.of(guestOrder));

        var response = service.linkGuestOrders(new GuestOrderLinkRequest(session), authentication);

        assertEquals(1, response.linkedOrders());
        assertSame(customer, guestOrder.getUser());
        verify(verification).assertVerifiedForOrder(session, "an@example.com");
    }

    @Test void wrongOrMissingOtpLinksNothing() {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email chưa được xác thực OTP."))
            .when(verification).assertVerifiedForOrder(any(), anyString());

        assertThrows(ResponseStatusException.class, () -> service.linkGuestOrders(new GuestOrderLinkRequest(session), authentication));
        assertThrows(ResponseStatusException.class, () -> service.linkGuestOrders(null, authentication));

        assertNull(guestOrder.getUser());
        verify(orders, never()).findByUserIsNullAndCustomerEmail(anyString());
        verify(orders, never()).saveAll(any());
    }

    @Test void otpIsCheckedAgainstTheAccountEmailNotAnyEmail() {
        customer.setEmail("An@Example.com");
        when(orders.findByUserIsNullAndCustomerEmail("an@example.com")).thenReturn(List.of());

        service.linkGuestOrders(new GuestOrderLinkRequest(session), authentication);

        // The session must have verified the signed-in customer's own address.
        verify(verification).assertVerifiedForOrder(session, "An@Example.com");
    }
}
