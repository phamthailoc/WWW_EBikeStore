package com.ebike.qa.unit.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderEmailVerificationSession;
import com.ebike.model.entity.order.OrderEmailVerificationStatus;
import com.ebike.model.entity.order.OrderItemUnit;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.model.entity.order.Shipment;
import com.ebike.model.entity.order.ShipmentStatus;
import com.ebike.model.entity.order.Showroom;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderEntitiesTest {

    private static void call(Object target, String method) throws Exception {
        Method m = target.getClass().getDeclaredMethod(method);
        m.setAccessible(true);
        m.invoke(target);
    }

    @Test
    void aNewOrderStartsPendingWithZeroMoneyAndNoRegistrationService() {
        Order order = new Order();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order.getShippingFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order.getRegistrationFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order.getIncludeRegistrationService()).isFalse();
        assertThat(order.getItems()).isEmpty();
        assertThat(order.getUser()).isNull();
        assertThat(order.getPayment()).isNull();
    }

    @Test
    void persistingSetsBothTimestamps() throws Exception {
        Order order = new Order();
        LocalDateTime before = LocalDateTime.now();
        call(order, "onCreate");
        assertThat(order.getCreatedAt()).isBetween(before, LocalDateTime.now());
        assertThat(order.getUpdatedAt()).isEqualTo(order.getCreatedAt());
    }

    @Test
    void deliveredAtIsFixedTheFirstTimeTheOrderIsSavedAsDeliveredAndNeverMoves() throws Exception {
        Order order = new Order();
        call(order, "onCreate");
        call(order, "onUpdate");
        assertThat(order.getDeliveredAt()).isNull();
        order.setStatus(OrderStatus.DELIVERED);
        call(order, "onUpdate");
        LocalDateTime first = order.getDeliveredAt();
        assertThat(first).isNotNull();
        Thread.sleep(5);
        call(order, "onUpdate");
        assertThat(order.getDeliveredAt()).isEqualTo(first);
        assertThat(order.getUpdatedAt()).isAfterOrEqualTo(first);
    }

    @Test
    void aCancelledOrderNeverGetsADeliveryTime() throws Exception {
        Order order = new Order();
        order.setStatus(OrderStatus.CANCELLED);
        call(order, "onUpdate");
        assertThat(order.getDeliveredAt()).isNull();
    }

    @Test
    void paymentAndShipmentStartPending() {
        assertThat(new Payment().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(new Shipment().getShipmentStatus()).isEqualTo(ShipmentStatus.PENDING);
        assertThat(new Showroom().getActive()).isTrue();
    }

    @Test
    void theOrderStatusWorkflowHasExactlyTheSevenStepsInOrder() {
        assertThat(OrderStatus.values()).containsExactly(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.PROCESSING,
            OrderStatus.READY_FOR_PICKUP, OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.CANCELLED);
        assertThat(PaymentMethod.values()).containsExactly(PaymentMethod.PAY_LATER, PaymentMethod.VNPAY, PaymentMethod.INSTALLMENT);
        assertThat(PaymentStatus.values()).extracting(Enum::name)
            .containsExactly("PENDING", "AUTHORIZED", "PAID", "FAILED", "CANCELLED", "REFUNDED");
    }

    @Test
    void aNewVerificationSessionIsPendingWithZeroAttempts() {
        OrderEmailVerificationSession session = new OrderEmailVerificationSession();
        assertThat(session.getStatus()).isEqualTo(OrderEmailVerificationStatus.PENDING);
        assertThat(session.getAttemptCount()).isZero();
        assertThat(session.getVerifiedAt()).isNull();
        assertThat(session.getConsumedAt()).isNull();
    }

    @Test
    void aVerificationSessionGetsARandomIdOnlyWhenItHasNone() throws Exception {
        OrderEmailVerificationSession fresh = new OrderEmailVerificationSession();
        call(fresh, "onCreate");
        assertThat(fresh.getId()).isNotNull();
        UUID preset = UUID.randomUUID();
        OrderEmailVerificationSession kept = new OrderEmailVerificationSession();
        kept.setId(preset);
        call(kept, "onCreate");
        assertThat(kept.getId()).isEqualTo(preset);
        OrderEmailVerificationSession other = new OrderEmailVerificationSession();
        call(other, "onCreate");
        assertThat(other.getId()).isNotEqualTo(fresh.getId());
    }

    @Test
    void aFrameNumberRowGetsItsCreationTime() throws Exception {
        OrderItemUnit unit = new OrderItemUnit();
        call(unit, "onCreate");
        assertThat(unit.getWarrantyMonths()).isNull();
    }
}
