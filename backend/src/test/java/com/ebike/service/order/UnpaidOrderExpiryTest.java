package com.ebike.service.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.model.entity.product.Product;
import com.ebike.repository.admin.PromotionRepository;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.order.ShowroomRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.service.order.impl.OrderServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** B2: unpaid orders hold stock for 30 minutes (VNPay) or 72 hours (pay later, not yet confirmed). */
@ExtendWith(MockitoExtension.class)
class UnpaidOrderExpiryTest {
    @Mock OrderRepository orders;
    @Mock UserRepository users;
    @Mock ProductRepository products;
    @Mock ShowroomRepository showrooms;
    @Mock PromotionRepository promotions;
    @Mock OrderEmailVerificationService verification;
    OrderServiceImpl service;
    Product product;
    final LocalDateTime now = LocalDateTime.of(2026, 11, 6, 12, 0);

    @BeforeEach void setup() {
        service = new OrderServiceImpl(orders, users, products, showrooms, promotions, verification);
        product = new Product();
        product.setId(1L);
        product.setStockQuantity(5);
    }

    private Order order(LocalDateTime createdAt, PaymentMethod method, PaymentStatus paymentStatus) {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(1);
        Order order = new Order();
        order.setStatus(OrderStatus.PENDING);
        org.springframework.test.util.ReflectionTestUtils.setField(order, "createdAt", createdAt);
        order.getItems().add(item);
        if (method != null) {
            Payment payment = new Payment();
            payment.setOrder(order);
            payment.setPaymentMethod(method);
            payment.setPaymentStatus(paymentStatus);
            payment.setAmount(new BigDecimal("20000000"));
            order.setPayment(payment);
        }
        return order;
    }

    private void stale(Order... found) {
        when(orders.findByStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), any())).thenReturn(List.of(found));
    }

    @Test void vnpayOrderLeftUnpaidForOver30MinutesIsCancelledAndStockReturned() {
        Order neverStarted = order(now.minusMinutes(40), null, null);
        Order abandoned = order(now.minusMinutes(31), PaymentMethod.VNPAY, PaymentStatus.PENDING);
        stale(neverStarted, abandoned);

        assertEquals(2, service.expireUnpaidOrders(now));

        assertEquals(OrderStatus.CANCELLED, neverStarted.getStatus());
        assertEquals(OrderStatus.CANCELLED, abandoned.getStatus());
        assertEquals(PaymentStatus.CANCELLED, abandoned.getPayment().getPaymentStatus());
        assertEquals(7, product.getStockQuantity());
        assertEquals("Tự động hủy: quá hạn thanh toán online.", abandoned.getCancellationReason());
    }

    @Test void payLaterOrderIsHeldFor72Hours() {
        Order recent = order(now.minusHours(10), PaymentMethod.PAY_LATER, PaymentStatus.PENDING);
        Order old = order(now.minusHours(73), PaymentMethod.PAY_LATER, PaymentStatus.PENDING);
        stale(recent, old);

        assertEquals(1, service.expireUnpaidOrders(now));

        assertEquals(OrderStatus.PENDING, recent.getStatus());
        assertEquals(OrderStatus.CANCELLED, old.getStatus());
        assertEquals(PaymentStatus.CANCELLED, old.getPayment().getPaymentStatus());
        assertEquals(6, product.getStockQuantity());
    }

    @Test void paidOrdersAreNeverExpired() {
        Order paid = order(now.minusDays(5), PaymentMethod.VNPAY, PaymentStatus.PAID);
        stale(paid);

        assertEquals(0, service.expireUnpaidOrders(now));
        assertEquals(OrderStatus.PENDING, paid.getStatus());
        assertEquals(5, product.getStockQuantity());
    }
}
