package com.ebike.qa.unit.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.order.request.OrderStatusUpdateRequest;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.installment.InstallmentApplication;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.model.entity.order.Shipment;
import com.ebike.model.entity.order.ShipmentStatus;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.product.VehicleType;
import com.ebike.service.order.impl.OrderServiceImpl;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class OrderServiceStatusTest extends OrderServiceTestBase {

    Product accessory;
    Product bike;
    User owner;
    User staffUser;
    Authentication staff;

    @BeforeEach
    void data() {
        accessory = OrderFixtures.accessory(2L, "100000", 10);
        bike = OrderFixtures.bike(1L, "Kinetic X1", "10000000", 3, VehicleType.E_BIKE);
        owner = OrderFixtures.user(1L, "alice", "alice@example.com");
        staffUser = OrderFixtures.user(9L, "staffy", "staff@example.com");
        staff = OrderFixtures.staff("staffy");
        when(userRepository.findByUsername("staffy")).thenReturn(Optional.of(staffUser));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(owner));
    }

    private Order order(OrderStatus status, boolean pickup, Product product) {
        Order order = OrderFixtures.order(10L, "ORD-20261126-ABCD1234", status, owner);
        OrderFixtures.line(order, 100L, product, null, 2, product.getPrice().toPlainString());
        // a bike may only be handed over once it is paid (M-20): the orders that can be delivered from here start paid
        boolean handOverNext = status == OrderStatus.READY_FOR_PICKUP || status == OrderStatus.SHIPPED;
        OrderFixtures.payment(order, PaymentMethod.PAY_LATER, handOverNext ? PaymentStatus.PAID : PaymentStatus.PENDING, "200000");
        OrderFixtures.shipment(order, pickup ? OrderFixtures.showroom(1L, "Hà Nội", "Cầu Giấy", true) : null);
        when(orderRepository.findLockedById(10L)).thenReturn(Optional.of(order));
        return order;
    }

    private static void assertBad(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private void change(Order order, OrderStatus to) {
        service.updateOrderStatus(order.getId(), new OrderStatusUpdateRequest(to.name(), "reason"), staff);
    }

    // --- the matrix ----------------------------------------------------------------------------------

    /** Written from the business rule, not from the code: what staff may do from each status. */
    private static Set<OrderStatus> allowed(OrderStatus from, boolean pickup) {
        return switch (from) {
            case PENDING -> EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
            case CONFIRMED -> EnumSet.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED);
            case PROCESSING -> EnumSet.of(pickup ? OrderStatus.READY_FOR_PICKUP : OrderStatus.SHIPPED, OrderStatus.CANCELLED);
            case READY_FOR_PICKUP -> EnumSet.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED);
            case SHIPPED -> EnumSet.of(OrderStatus.DELIVERED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    static Stream<Arguments> matrix() {
        return Arrays.stream(OrderStatus.values()).flatMap(from -> Arrays.stream(OrderStatus.values()).flatMap(to ->
            Stream.of(Arguments.of(from, to, true), Arguments.of(from, to, false))));
    }

    @ParameterizedTest(name = "{0} -> {1} (pickup={2})")
    @MethodSource("matrix")
    void transitionMatrix(OrderStatus from, OrderStatus to, boolean pickup) {
        Order order = order(from, pickup, accessory);
        boolean ok = allowed(from, pickup).contains(to);
        if (ok) {
            service.updateOrderStatus(10L, new OrderStatusUpdateRequest(to.name(), "no longer wanted"), staff);
            assertThat(order.getStatus()).isEqualTo(to);
            verify(orderRepository).save(order);
        } else {
            assertBad(() -> service.updateOrderStatus(10L, new OrderStatusUpdateRequest(to.name(), "no longer wanted"), staff));
            assertThat(order.getStatus()).isEqualTo(from);
            verify(orderRepository, never()).save(any());
            assertThat(accessory.getStockQuantity()).isEqualTo(10);
        }
    }

    // --- M-20: a bike is handed over only once it is paid --------------------------------------------

    @Test
    void deliveringAnUnpaidPayLaterOrderIsRefusedWithAHintToMarkItPaid() {
        Order order = order(OrderStatus.SHIPPED, false, accessory);
        order.getPayment().setPaymentStatus(PaymentStatus.PENDING);
        assertThatThrownBy(() -> change(order, OrderStatus.DELIVERED)).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(e.getReason()).contains("Xác nhận đã thu");
        });
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void deliveringAPickupOrderIsRefusedWhileUnpaidAndAllowedOnceMarkedPaid() {
        Order order = order(OrderStatus.READY_FOR_PICKUP, true, accessory);
        order.getPayment().setPaymentStatus(PaymentStatus.PENDING);
        assertBad(() -> change(order, OrderStatus.DELIVERED));
        order.getPayment().setPaymentStatus(PaymentStatus.PAID);
        change(order, OrderStatus.DELIVERED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void deliveringAnOrderWithNoPaymentRowOrAnUnpaidVnpayIsRefusedWithTheOnlineWording() {
        Order none = order(OrderStatus.SHIPPED, false, accessory);
        none.setPayment(null);
        assertThatThrownBy(() -> change(none, OrderStatus.DELIVERED)).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(e.getReason()).contains("thanh toán trực tuyến");
        });
        Order online = order(OrderStatus.SHIPPED, false, accessory);
        online.getPayment().setPaymentMethod(PaymentMethod.VNPAY);
        online.getPayment().setPaymentStatus(PaymentStatus.PENDING);
        assertThatThrownBy(() -> change(online, OrderStatus.DELIVERED)).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getReason()).contains("thanh toán trực tuyến"));
    }

    // --- D3: a returned shipment cancels the order and puts the bikes back on sale --------------------

    @Test
    void aReturnedOrderIsCancelledAndItsStockRestoredButAPaidPaymentStaysPaidForTheRefund() {
        Order order = order(OrderStatus.SHIPPED, false, accessory);
        order.getPayment().setPaymentStatus(PaymentStatus.PAID);
        service.cancelReturnedOrder(order, "Khách từ chối nhận");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(accessory.getStockQuantity()).isEqualTo(12);
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getCancellationReason()).isEqualTo("Khách từ chối nhận");
        verify(stockLedger).record(org.mockito.ArgumentMatchers.argThat(entries -> entries.size() == 1
            && entries.get(0).reason() == com.ebike.model.entity.stock.StockMovementReason.CANCEL_RESTORE), any(), any(), any(), any());
    }

    // --- request parsing -----------------------------------------------------------------------------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "FLYING", "pending now"})
    void missingOrUnknownStatusIs400(String status) {
        order(OrderStatus.PENDING, true, accessory);
        assertBad(() -> service.updateOrderStatus(10L, new OrderStatusUpdateRequest(status), staff));
    }

    @Test
    void nullRequestIs400AndUnknownOrderIs404() {
        assertBad(() -> service.updateOrderStatus(10L, null, staff));
        when(orderRepository.findLockedById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateOrderStatus(404L, new OrderStatusUpdateRequest("CONFIRMED"), staff))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void statusIsCaseInsensitiveAndTrimmed() {
        Order order = order(OrderStatus.PENDING, true, accessory);
        service.updateOrderStatus(10L, new OrderStatusUpdateRequest("  confirmed "), staff);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void everyChangeIsWrittenToTheAuditLog() {
        order(OrderStatus.PENDING, true, accessory);
        service.updateOrderStatus(10L, new OrderStatusUpdateRequest("CONFIRMED"), staff);
        verify(auditLog).record(any(), org.mockito.ArgumentMatchers.contains("PENDING"));
    }

    // --- READY_FOR_PICKUP and its deadline -----------------------------------------------------------

    @Test
    void readyForPickupStartsTheSevenDayHold() {
        Order order = order(OrderStatus.PROCESSING, true, accessory);
        LocalDateTime before = LocalDateTime.now();
        change(order, OrderStatus.READY_FOR_PICKUP);
        assertThat(order.getPickupDeadlineAt()).isBetween(before.plusDays(7).minusSeconds(2), LocalDateTime.now().plusDays(7).plusSeconds(2));
    }

    @Test
    void theHoldLengthCanBeConfigured() {
        ReflectionTestUtils.setField(service, "pickupHold", Duration.ofDays(3));
        Order order = order(OrderStatus.PROCESSING, true, accessory);
        change(order, OrderStatus.READY_FOR_PICKUP);
        assertThat(Duration.between(LocalDateTime.now(), order.getPickupDeadlineAt())).isBetween(Duration.ofDays(3).minusMinutes(1), Duration.ofDays(3));
    }

    @Test
    void anOrderWaitingForThePickupCannotBeCancelledBeforeTheDeadline() {
        Order order = order(OrderStatus.READY_FOR_PICKUP, true, accessory);
        order.setPickupDeadlineAt(LocalDateTime.now().plusMinutes(1));
        assertBad(() -> change(order, OrderStatus.CANCELLED));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
        assertThat(accessory.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void afterTheDeadlineTheStoreMayGiveTheBikeUpAndStockReturns() {
        Order order = order(OrderStatus.READY_FOR_PICKUP, true, accessory);
        order.setPickupDeadlineAt(LocalDateTime.now().minusSeconds(1));
        change(order, OrderStatus.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(accessory.getStockQuantity()).isEqualTo(12);
    }

    @Test
    void theCustomerCollectingBeforeTheDeadlineIsNeverBlocked() {
        Order order = order(OrderStatus.READY_FOR_PICKUP, true, accessory);
        order.setPickupDeadlineAt(LocalDateTime.now().plusDays(6));
        change(order, OrderStatus.DELIVERED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    // --- delivery bookkeeping ------------------------------------------------------------------------

    @Test
    void handOverRecordsShipmentTimesWithoutOverwritingExistingOnes() {
        Order order = order(OrderStatus.READY_FOR_PICKUP, true, accessory);
        LocalDateTime shipped = LocalDateTime.of(2026, 1, 1, 8, 0);
        order.getShipment().setShippedAt(shipped);
        change(order, OrderStatus.DELIVERED);
        Shipment shipment = order.getShipment();
        assertThat(shipment.getShippedAt()).isEqualTo(shipped);
        assertThat(shipment.getDeliveredAt()).isNotNull();
        assertThat(shipment.getShipmentStatus()).isEqualTo(ShipmentStatus.DELIVERED);
    }

    @Test
    void pickupHandOverFillsBothShipmentTimes() {
        Order order = order(OrderStatus.READY_FOR_PICKUP, true, accessory);
        change(order, OrderStatus.DELIVERED);
        assertThat(order.getShipment().getShippedAt()).isNotNull();
        assertThat(order.getShipment().getDeliveredAt()).isNotNull();
    }

    // --- frame/engine numbers before the bike leaves the store ---------------------------------------

    @Test
    void aBikeWithoutNumbersCannotBeShipped() {
        Order order = order(OrderStatus.PROCESSING, false, bike);
        assertBad(() -> change(order, OrderStatus.SHIPPED));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    void aBikeWithOnlyPartOfItsNumbersCannotBeShipped() {
        Order order = order(OrderStatus.PROCESSING, false, bike);
        OrderItem item = order.getItems().iterator().next(); // quantity 2
        OrderFixtures.unit(item, 1L, "FRAME-0001", "ENGINE-0001");
        assertBad(() -> change(order, OrderStatus.SHIPPED));
    }

    @Test
    void aBikeWithAllNumbersCanBeShipped() {
        Order order = order(OrderStatus.PROCESSING, false, bike);
        OrderItem item = order.getItems().iterator().next();
        OrderFixtures.unit(item, 1L, "FRAME-0001", "ENGINE-0001");
        OrderFixtures.unit(item, 2L, "FRAME-0002", "ENGINE-0002");
        change(order, OrderStatus.SHIPPED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void aPickupCustomerCannotCollectABikeWithoutNumbers() {
        Order order = order(OrderStatus.READY_FOR_PICKUP, true, bike);
        assertBad(() -> change(order, OrderStatus.DELIVERED));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
    }

    @Test
    void anAlreadyShippedBikeCanBeDeliveredEvenWithoutNumbers() {
        Order order = order(OrderStatus.SHIPPED, false, bike); // shipped before numbers existed
        change(order, OrderStatus.DELIVERED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void markingReadyForPickupDoesNotNeedNumbersYet() {
        Order order = order(OrderStatus.PROCESSING, true, bike);
        change(order, OrderStatus.READY_FOR_PICKUP);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
    }

    @Test
    void accessoriesNeverNeedNumbers() {
        Order order = order(OrderStatus.PROCESSING, false, accessory);
        change(order, OrderStatus.SHIPPED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    // --- cancellation by staff -----------------------------------------------------------------------

    @Test
    void staffCancellationRestocksClosesThePendingPaymentAndKeepsTheReason() {
        Order order = order(OrderStatus.CONFIRMED, true, accessory);
        order.getItems().iterator().next().setQuantity(3);
        accessory.setStockQuantity(7);
        service.updateOrderStatus(10L, new OrderStatusUpdateRequest("CANCELLED", "  customer changed mind  "), staff);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo("customer changed mind");
        assertThat(order.getCancellationRequestedFromStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getCancellationRequestedBy()).isSameAs(staffUser);
        assertThat(accessory.getStockQuantity()).isEqualTo(10);
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void staffCancellationNeedsAReasonOfAtMost1000Characters() {
        Order order = order(OrderStatus.PENDING, true, accessory);
        assertBad(() -> service.updateOrderStatus(10L, new OrderStatusUpdateRequest("CANCELLED", null), staff));
        assertBad(() -> service.updateOrderStatus(10L, new OrderStatusUpdateRequest("CANCELLED", "   "), staff));
        assertBad(() -> service.updateOrderStatus(10L, new OrderStatusUpdateRequest("CANCELLED", "r".repeat(1001)), staff));
        assertThat(accessory.getStockQuantity()).isEqualTo(10);
        service.updateOrderStatus(10L, new OrderStatusUpdateRequest("CANCELLED", "r".repeat(1000)), staff);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancellingTwiceNeverRestocksTwice() {
        Order order = order(OrderStatus.PENDING, true, accessory);
        accessory.setStockQuantity(8);
        change(order, OrderStatus.CANCELLED);
        assertBad(() -> change(order, OrderStatus.CANCELLED));
        assertThat(accessory.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void cancellingRestoresTheColourStockToo() {
        ProductVariant red = OrderFixtures.colour(11L, accessory, "Red", 0, null);
        Order order = order(OrderStatus.PENDING, true, accessory);
        order.getItems().iterator().next().setProductVariant(red);
        change(order, OrderStatus.CANCELLED);
        assertThat(red.getStockQuantity()).isEqualTo(2);
        verify(stockAlerts).notifyRestocked(red); // a sold-out colour is back
    }

    @Test
    void cancellingFreesThePromotionUseAndClosesTheInstalmentApplication() {
        Order order = order(OrderStatus.PENDING, true, accessory);
        com.ebike.model.entity.admin.Promotion promotion = new com.ebike.model.entity.admin.Promotion();
        ReflectionTestUtils.setField(promotion, "id", 5L);
        order.setPromotion(promotion);
        change(order, OrderStatus.CANCELLED);
        verify(promotionRepository).releaseUsage(5L);
        verify(installments).cancelOpenOfOrder(eq(10L), any(LocalDateTime.class));
    }

    @Test
    void cancellingKeepsAPaidPaymentPaidSoTheManagerCanRefund() {
        Order order = order(OrderStatus.CONFIRMED, true, accessory);
        order.getPayment().setPaymentStatus(PaymentStatus.PAID);
        change(order, OrderStatus.CANCELLED);
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void cancellingClosesAFailedPaymentToo() {
        Order order = order(OrderStatus.PENDING, true, accessory);
        order.getPayment().setPaymentStatus(PaymentStatus.FAILED);
        change(order, OrderStatus.CANCELLED);
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void cancellingClearsRecordedFrameNumbersSoTheyCanBeUsedAgain() {
        Order order = order(OrderStatus.PROCESSING, true, bike);
        OrderFixtures.unit(order.getItems().iterator().next(), 1L, "FRAME-0001", "ENGINE-0001");
        change(order, OrderStatus.CANCELLED);
        assertThat(order.getItems().iterator().next().getUnits()).isEmpty();
    }

    @Test
    void aGuestOrderCancelledByStaffRecordsTheStaffMemberAsActor() {
        Order order = order(OrderStatus.PENDING, true, accessory);
        order.setUser(null);
        change(order, OrderStatus.CANCELLED);
        verify(stockLedger).record(any(), eq(10L), any(), org.mockito.ArgumentMatchers.isNull(), eq(staffUser));
    }

    // --- instalments ---------------------------------------------------------------------------------

    @Test
    void anInstalmentOrderIsNotConfirmedByHandUntilApprovedAndTheDownPaymentIsPaid() {
        Order order = order(OrderStatus.PENDING, true, accessory);
        order.getPayment().setPaymentMethod(PaymentMethod.INSTALLMENT);
        when(installments.findByOrderId(10L)).thenReturn(Optional.empty());
        assertBad(() -> change(order, OrderStatus.CONFIRMED));

        InstallmentApplication application = new InstallmentApplication();
        application.setStatus(InstallmentApplication.APPROVED);
        when(installments.findByOrderId(10L)).thenReturn(Optional.of(application));
        assertBad(() -> change(order, OrderStatus.CONFIRMED)); // down payment still pending

        order.getPayment().setPaymentStatus(PaymentStatus.PAID);
        change(order, OrderStatus.CONFIRMED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void aRejectedApplicationNeverAllowsConfirmation() {
        Order order = order(OrderStatus.PENDING, true, accessory);
        order.getPayment().setPaymentMethod(PaymentMethod.INSTALLMENT);
        order.getPayment().setPaymentStatus(PaymentStatus.PAID);
        InstallmentApplication application = new InstallmentApplication();
        application.setStatus("REJECTED");
        when(installments.findByOrderId(10L)).thenReturn(Optional.of(application));
        assertBad(() -> change(order, OrderStatus.CONFIRMED));
    }

    @Test
    void theLockedRowIsReadFirstForEveryStatusChange() {
        order(OrderStatus.PENDING, true, accessory);
        service.updateOrderStatus(10L, new OrderStatusUpdateRequest("CONFIRMED"), staff);
        verify(orderRepository).findLockedById(10L);
        verify(orderRepository, never()).findById(anyLong());
    }

    @Test
    void allStatusNamesAreCoveredByTheMatrix() {
        assertThat(OrderStatus.values()).extracting(Enum::name).containsExactly(
            "PENDING", "CONFIRMED", "PROCESSING", "READY_FOR_PICKUP", "SHIPPED", "DELIVERED", "CANCELLED");
        assertThat(OrderServiceImpl.class).isNotNull();
        Payment unused = null;
        assertThat(unused).isNull();
    }
}
