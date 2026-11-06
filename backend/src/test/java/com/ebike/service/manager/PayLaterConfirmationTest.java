package com.ebike.service.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.manager.request.ManagerPaymentConfirmationRequest;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.order.PaymentRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.service.manager.impl.ManagerServiceImpl;
import com.ebike.service.product.ProductService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PayLaterConfirmationTest {
    @Mock OrderRepository orders;
    @Mock PaymentRepository payments;
    @Mock UserRepository users;
    @Mock ProductRepository products;
    @Mock ProductService productService;
    ManagerServiceImpl service;
    Order order;
    Payment payment;

    @BeforeEach void setup() {
        service = new ManagerServiceImpl(orders, payments, users, products, productService);
        order = new Order();
        order.setId(5L);
        order.setOrderNumber("ORD-TEST");
        order.setStatus(OrderStatus.PENDING);
        payment = new Payment();
        payment.setId(7L);
        payment.setOrder(order);
        payment.setPaymentMethod(PaymentMethod.PAY_LATER);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("20000000"));
        when(payments.findById(7L)).thenReturn(Optional.of(payment));
    }

    private ManagerPaymentConfirmationRequest note() { return new ManagerPaymentConfirmationRequest(null, "Thu tiền mặt tại cửa hàng"); }

    @Test void pendingPayLaterPaymentIsConfirmedAndOrderMovesOn() {
        when(payments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var response = service.confirmPayLaterPayment(7L, note());
        assertEquals("PAID", response.paymentStatus());
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
    }

    @Test void cancelledPaymentCannotBeMarkedPaid() {
        payment.setPaymentStatus(PaymentStatus.CANCELLED);
        order.setStatus(OrderStatus.CANCELLED);
        assertThrows(ResponseStatusException.class, () -> service.confirmPayLaterPayment(7L, note()));
        assertEquals(PaymentStatus.CANCELLED, payment.getPaymentStatus());
        verify(payments, never()).save(any());
    }

    @Test void paymentOfACancelledOrderCannotBeMarkedPaid() {
        order.setStatus(OrderStatus.CANCELLED);
        assertThrows(ResponseStatusException.class, () -> service.confirmPayLaterPayment(7L, note()));
        assertEquals(PaymentStatus.PENDING, payment.getPaymentStatus());
        verify(payments, never()).save(any());
    }

    @Test void failedPaymentCannotBeMarkedPaid() {
        payment.setPaymentStatus(PaymentStatus.FAILED);
        assertThrows(ResponseStatusException.class, () -> service.confirmPayLaterPayment(7L, note()));
        verify(payments, never()).save(any());
    }
}
