package com.ebike.qa.unit.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.order.response.OrderResponse;
import com.ebike.model.entity.admin.Promotion;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.installment.InstallmentApplication;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderItem;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductVariant;
import com.ebike.model.entity.product.VehicleType;
import com.ebike.model.entity.stock.StockMovementReason;
import com.ebike.repository.order.OrderRepository;
import com.ebike.service.stock.StockLedger;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class OrderServiceExpiryAndQuantityTest extends OrderServiceTestBase {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 26, 12, 0, 0);

    Product accessory;
    User alice;
    Authentication staff;

    @BeforeEach
    void data() {
        accessory = OrderFixtures.accessory(2L, "100000", 10);
        alice = OrderFixtures.user(1L, "alice", "alice@example.com");
        staff = OrderFixtures.staff("staffy");
        when(userRepository.findByUsername("staffy")).thenReturn(Optional.of(OrderFixtures.user(9L, "staffy", "s@x.com")));
    }

    private static OrderRepository.ExpiryCandidate candidate(long id, LocalDateTime createdAt, PaymentMethod method, PaymentStatus status) {
        OrderRepository.ExpiryCandidate candidate = mock(OrderRepository.ExpiryCandidate.class);
        when(candidate.getId()).thenReturn(id);
        when(candidate.getCreatedAt()).thenReturn(createdAt);
        when(candidate.getPaymentMethod()).thenReturn(method);
        when(candidate.getPaymentStatus()).thenReturn(status);
        return candidate;
    }

    private Order pending(long id, LocalDateTime createdAt, PaymentMethod method, PaymentStatus payStatus) {
        Order order = OrderFixtures.order(id, "ORD-" + id, OrderStatus.PENDING, alice);
        ReflectionTestUtils.setField(order, "createdAt", createdAt);
        OrderFixtures.line(order, id * 10, accessory, null, 2, "100000");
        if (method != null) {
            OrderFixtures.payment(order, method, payStatus, "200000");
        }
        when(orderRepository.findLockedById(id)).thenReturn(Optional.of(order));
        return order;
    }

    private void candidates(OrderRepository.ExpiryCandidate... list) {
        when(orderRepository.findExpiryCandidates(eq(OrderStatus.PENDING), any(LocalDateTime.class))).thenReturn(List.of(list));
    }

    // --- expiry --------------------------------------------------------------------------------------

    @Test
    void aPayLaterOrderIsKeptFor72HoursThenCancelledAndItsBikesReturn() {
        LocalDateTime created = NOW.minusHours(73);
        Order order = pending(1, created, PaymentMethod.PAY_LATER, PaymentStatus.PENDING);
        accessory.setStockQuantity(8);
        candidates(candidate(1, created, PaymentMethod.PAY_LATER, PaymentStatus.PENDING));

        int cancelled = service.expireUnpaidOrders(NOW);

        assertThat(cancelled).isEqualTo(1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isNotBlank();
        assertThat(order.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(accessory.getStockQuantity()).isEqualTo(10);
        verify(orderRepository).saveAll(List.of(order));
    }

    @Test
    void payLaterBoundaryExactly72HoursIsNotYetDueOneSecondMoreIs() {
        LocalDateTime exactly = NOW.minusHours(72);
        pending(1, exactly, PaymentMethod.PAY_LATER, PaymentStatus.PENDING);
        candidates(candidate(1, exactly, PaymentMethod.PAY_LATER, PaymentStatus.PENDING));
        assertThat(service.expireUnpaidOrders(NOW)).isZero();

        LocalDateTime late = NOW.minusHours(72).minusSeconds(1);
        pending(2, late, PaymentMethod.PAY_LATER, PaymentStatus.PENDING);
        candidates(candidate(2, late, PaymentMethod.PAY_LATER, PaymentStatus.PENDING));
        assertThat(service.expireUnpaidOrders(NOW)).isEqualTo(1);
    }

    @Test
    void anOnlineOrderIsKeptFor30MinutesOnly() {
        LocalDateTime young = NOW.minusMinutes(30);
        Order notDue = pending(1, young, PaymentMethod.VNPAY, PaymentStatus.PENDING);
        candidates(candidate(1, young, PaymentMethod.VNPAY, PaymentStatus.PENDING));
        assertThat(service.expireUnpaidOrders(NOW)).isZero();
        assertThat(notDue.getStatus()).isEqualTo(OrderStatus.PENDING);

        LocalDateTime old = NOW.minusMinutes(31);
        Order due = pending(2, old, PaymentMethod.VNPAY, PaymentStatus.PENDING);
        candidates(candidate(2, old, PaymentMethod.VNPAY, PaymentStatus.PENDING));
        assertThat(service.expireUnpaidOrders(NOW)).isEqualTo(1);
        assertThat(due.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void anOnlineOrderWithoutAPaymentRowYetCountsAsOnline() {
        LocalDateTime old = NOW.minusMinutes(45);
        Order due = pending(1, old, null, null);
        candidates(candidate(1, old, null, null));
        assertThat(service.expireUnpaidOrders(NOW)).isEqualTo(1);
        assertThat(due.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void aPayLaterOrderOlderThan30MinutesIsNotCancelledByTheOnlineRule() {
        LocalDateTime created = NOW.minusHours(2);
        Order order = pending(1, created, PaymentMethod.PAY_LATER, PaymentStatus.PENDING);
        candidates(candidate(1, created, PaymentMethod.PAY_LATER, PaymentStatus.PENDING));
        assertThat(service.expireUnpaidOrders(NOW)).isZero();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(accessory.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void aPaidOrderIsNeverExpiredEvenWhenThePaymentWasWrittenAfterTheCandidateRead() {
        LocalDateTime old = NOW.minusHours(5);
        Order order = pending(1, old, PaymentMethod.VNPAY, PaymentStatus.PAID);
        candidates(candidate(1, old, PaymentMethod.VNPAY, PaymentStatus.PENDING)); // stale unlocked read
        assertThat(service.expireUnpaidOrders(NOW)).isZero();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void anOrderThatMovedOnWhileWaitingForTheLockIsLeftAlone() {
        LocalDateTime old = NOW.minusHours(100);
        Order order = pending(1, old, PaymentMethod.PAY_LATER, PaymentStatus.PENDING);
        order.setStatus(OrderStatus.CONFIRMED);
        candidates(candidate(1, old, PaymentMethod.PAY_LATER, PaymentStatus.PENDING));
        assertThat(service.expireUnpaidOrders(NOW)).isZero();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(accessory.getStockQuantity()).isEqualTo(10);
    }

    @Test
    void aVanishedOrderIsSkipped() {
        candidates(candidate(5, NOW.minusDays(9), PaymentMethod.PAY_LATER, PaymentStatus.PENDING));
        when(orderRepository.findLockedById(5L)).thenReturn(Optional.empty());
        assertThat(service.expireUnpaidOrders(NOW)).isZero();
    }

    @Test
    void paidCandidatesAreFilteredBeforeAnyLockIsTaken() {
        candidates(candidate(1, NOW.minusDays(9), PaymentMethod.VNPAY, PaymentStatus.PAID));
        service.expireUnpaidOrders(NOW);
        verify(orderRepository, never()).findLockedById(1L);
    }

    @Test
    void severalDueOrdersAreLockedInIdOrderAndTheirPromotionsReleased() {
        Order second = pending(2, NOW.minusDays(5), PaymentMethod.PAY_LATER, PaymentStatus.PENDING);
        Order first = pending(1, NOW.minusDays(5), PaymentMethod.PAY_LATER, PaymentStatus.PENDING);
        Promotion promotion = new Promotion();
        ReflectionTestUtils.setField(promotion, "id", 5L);
        first.setPromotion(promotion);
        candidates(candidate(2, NOW.minusDays(5), PaymentMethod.PAY_LATER, PaymentStatus.PENDING),
            candidate(1, NOW.minusDays(5), PaymentMethod.PAY_LATER, PaymentStatus.PENDING));

        assertThat(service.expireUnpaidOrders(NOW)).isEqualTo(2);

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(orderRepository);
        inOrder.verify(orderRepository).findLockedById(1L);
        inOrder.verify(orderRepository).findLockedById(2L);
        verify(promotionRepository).releaseUsage(5L);
        assertThat(second.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(accessory.getStockQuantity()).isEqualTo(14);
    }

    @Test
    void nothingDueDoesNotTouchStockOrSave() {
        candidates();
        assertThat(service.expireUnpaidOrders(NOW)).isZero();
        verify(stockLedger).lock(anyList(), anyList());
    }

    @Test
    void theHoldsCanBeConfigured() {
        ReflectionTestUtils.setField(service, "vnpayExpiry", Duration.ofMinutes(5));
        LocalDateTime created = NOW.minusMinutes(6);
        Order order = pending(1, created, PaymentMethod.VNPAY, PaymentStatus.PENDING);
        candidates(candidate(1, created, PaymentMethod.VNPAY, PaymentStatus.PENDING));
        assertThat(service.expireUnpaidOrders(NOW)).isEqualTo(1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void anUnreviewedInstalmentApplicationHoldsStockFor72HoursFromTheOrder() {
        LocalDateTime created = NOW.minusHours(73);
        Order order = pending(1, created, PaymentMethod.INSTALLMENT, PaymentStatus.PENDING);
        when(installments.findByOrderId(1L)).thenReturn(Optional.empty());
        candidates(candidate(1, created, PaymentMethod.INSTALLMENT, PaymentStatus.PENDING));
        assertThat(service.expireUnpaidOrders(NOW)).isEqualTo(1);
        assertThat(order.getCancellationReason()).contains("chưa duyệt");
        verify(installments).cancelOpenOfOrder(eq(1L), any(LocalDateTime.class));
    }

    @Test
    void anApprovedInstalmentWaitsForTheDownPaymentFromTheApprovalNotFromTheOrder() {
        LocalDateTime created = NOW.minusDays(10);
        Order order = pending(1, created, PaymentMethod.INSTALLMENT, PaymentStatus.PENDING);
        InstallmentApplication application = new InstallmentApplication();
        application.setStatus(InstallmentApplication.APPROVED);
        application.setReviewedAt(NOW.minusHours(10));
        when(installments.findByOrderId(1L)).thenReturn(Optional.of(application));
        candidates(candidate(1, created, PaymentMethod.INSTALLMENT, PaymentStatus.PENDING));
        assertThat(service.expireUnpaidOrders(NOW)).isZero();

        application.setReviewedAt(NOW.minusHours(73));
        assertThat(service.expireUnpaidOrders(NOW)).isEqualTo(1);
        assertThat(order.getCancellationReason()).contains("khoản trả trước");
    }

    // --- staff edits the quantity of a line ----------------------------------------------------------

    /** Two E-motorbikes at 10,000,000, same-city delivery 200,000 each, registration 500,000 each, 10% promotion. */
    private Order editable(PaymentMethod method) {
        Product moto = OrderFixtures.motorbike(1L, "10000000", 6);
        Order order = OrderFixtures.order(10L, "ORD-10", OrderStatus.PENDING, alice);
        OrderFixtures.line(order, 100L, moto, null, 2, "10000000");
        order.setSubtotal(new BigDecimal("20000000"));
        order.setShippingFee(new BigDecimal("400000"));
        order.setRegistrationFee(new BigDecimal("1000000"));
        order.setIncludeRegistrationService(true);
        order.setDiscountAmount(new BigDecimal("2000000"));
        order.setTotalAmount(new BigDecimal("19400000"));
        OrderFixtures.payment(order, method, PaymentStatus.PENDING, "19400000");
        OrderFixtures.shipment(order, null);
        Promotion promotion = new Promotion();
        promotion.setDiscountType("PERCENTAGE");
        promotion.setDiscountValue(new BigDecimal("10"));
        order.setPromotion(promotion);
        when(orderRepository.findLockedById(10L)).thenReturn(Optional.of(order));
        return order;
    }

    private static void assertBad(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void raisingTheQuantityRecalculatesEveryMoneyFieldAndTheStock() {
        Order order = editable(PaymentMethod.PAY_LATER);
        Product moto = order.getItems().iterator().next().getProduct();

        OrderResponse response = service.updateItemQuantity(10L, 100L, 3, staff);

        assertThat(order.getSubtotal()).isEqualByComparingTo("30000000");
        assertThat(order.getShippingFee()).isEqualByComparingTo("600000");
        assertThat(order.getRegistrationFee()).isEqualByComparingTo("1500000");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("3000000");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("29100000");
        assertThat(order.getPayment().getAmount()).isEqualByComparingTo("29100000");
        assertThat(response.totalAmount()).isEqualByComparingTo("29100000");
        assertThat(moto.getStockQuantity()).isEqualTo(5);
        assertThat(order.getItems().iterator().next().getUnitPrice()).isEqualByComparingTo("10000000");
        verify(auditLog).record(any(), any());
    }

    /** M-11: the limit is per product over all colours: another line of the same bike with 8 leaves room for only 2. */
    @Test
    void raisingTheQuantityPastTenBikesOfOneProductAcrossLinesIs400() {
        Order order = editable(PaymentMethod.PAY_LATER);
        Product moto = order.getItems().iterator().next().getProduct();
        OrderFixtures.line(order, 101L, moto, null, 8, "10000000");

        assertBad(() -> service.updateItemQuantity(10L, 100L, 3, staff));
        assertThat(order.getItems().iterator().next().getQuantity()).isEqualTo(2);
        service.updateItemQuantity(10L, 100L, 2, staff);
    }

    @Test
    void loweringTheQuantityReturnsStockAndTheLedgerRecordsARestore() {
        Order order = editable(PaymentMethod.PAY_LATER);
        Product moto = order.getItems().iterator().next().getProduct();

        service.updateItemQuantity(10L, 100L, 1, staff);

        assertThat(moto.getStockQuantity()).isEqualTo(7);
        assertThat(order.getSubtotal()).isEqualByComparingTo("10000000");
        assertThat(order.getShippingFee()).isEqualByComparingTo("200000");
        assertThat(order.getRegistrationFee()).isEqualByComparingTo("500000");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("1000000");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("9700000");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StockLedger.Entry>> entries = ArgumentCaptor.forClass(List.class);
        verify(stockLedger).record(entries.capture(), eq(10L), any(), org.mockito.ArgumentMatchers.isNull(), any());
        assertThat(entries.getValue().get(0).change()).isEqualTo(1);
        assertThat(entries.getValue().get(0).reason()).isEqualTo(StockMovementReason.CANCEL_RESTORE);
    }

    @Test
    void goingUpAndBackDownReturnsToTheExactStartingFigures() {
        Order order = editable(PaymentMethod.PAY_LATER);
        service.updateItemQuantity(10L, 100L, 7, staff);
        service.updateItemQuantity(10L, 100L, 2, staff);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("19400000");
        assertThat(order.getShippingFee()).isEqualByComparingTo("400000");
        assertThat(order.getRegistrationFee()).isEqualByComparingTo("1000000");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("2000000");
        assertThat(order.getItems().iterator().next().getProduct().getStockQuantity()).isEqualTo(6);
    }

    @Test
    void aPercentageDiscountStaysUnderThePromotionCap() {
        Order order = editable(PaymentMethod.PAY_LATER);
        order.getPromotion().setMaxDiscountAmount(new BigDecimal("2500000"));
        service.updateItemQuantity(10L, 100L, 4, staff); // 10% of 40,000,000 would be 4,000,000
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("2500000");
    }

    @Test
    void aFixedDiscountStaysButNeverExceedsTheSubtotal() {
        Order order = editable(PaymentMethod.PAY_LATER);
        order.getPromotion().setDiscountType("FIXED_AMOUNT");
        order.setDiscountAmount(new BigDecimal("15000000"));
        service.updateItemQuantity(10L, 100L, 1, staff);
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("10000000");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("700000"); // 0 + 200,000 + 500,000
    }

    @Test
    void sameQuantityChangesNothing() {
        Order order = editable(PaymentMethod.PAY_LATER);
        service.updateItemQuantity(10L, 100L, 2, staff);
        verify(orderRepository, never()).save(any());
        verify(stockLedger, never()).record(any(), any(), any(), any(), any());
        assertThat(order.getTotalAmount()).isEqualByComparingTo("19400000");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1, 11, 100})
    void quantityOutsideOneToTenIs400(Integer quantity) {
        editable(PaymentMethod.PAY_LATER);
        assertBad(() -> service.updateItemQuantity(10L, 100L, quantity, staff));
    }

    @Test
    void tenIsTheLargestAllowedQuantity() {
        Order order = editable(PaymentMethod.PAY_LATER);
        order.getItems().iterator().next().getProduct().setStockQuantity(20);
        service.updateItemQuantity(10L, 100L, 10, staff);
        assertThat(order.getItems().iterator().next().getQuantity()).isEqualTo(10);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    void onlyAPendingOrderCanBeEdited(OrderStatus status) {
        Order order = editable(PaymentMethod.PAY_LATER);
        order.setStatus(status);
        assertBad(() -> service.updateItemQuantity(10L, 100L, 3, staff));
        assertThat(order.getItems().iterator().next().getQuantity()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentMethod.class, names = "PAY_LATER", mode = EnumSource.Mode.EXCLUDE)
    void onlinePaymentAndInstalmentOrdersCannotBeEdited(PaymentMethod method) {
        editable(method);
        assertBad(() -> service.updateItemQuantity(10L, 100L, 3, staff));
    }

    @Test
    void anOrderWithoutAPaymentRowOrWithAPaidPaymentCannotBeEdited() {
        Order order = editable(PaymentMethod.PAY_LATER);
        order.getPayment().setPaymentStatus(PaymentStatus.PAID);
        assertBad(() -> service.updateItemQuantity(10L, 100L, 3, staff));
        order.setPayment(null);
        assertBad(() -> service.updateItemQuantity(10L, 100L, 3, staff));
    }

    @Test
    void anUnknownLineIs404AndAnUnknownOrderIs404() {
        editable(PaymentMethod.PAY_LATER);
        assertThatThrownBy(() -> service.updateItemQuantity(10L, 999L, 3, staff)).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        when(orderRepository.findLockedById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateItemQuantity(404L, 100L, 3, staff)).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void aLineOfAnotherOrderCannotBeEditedThroughThisOrder() {
        Order order = editable(PaymentMethod.PAY_LATER);
        Order other = OrderFixtures.order(11L, "ORD-11", OrderStatus.PENDING, alice);
        OrderFixtures.line(other, 555L, order.getItems().iterator().next().getProduct(), null, 1, "10000000");
        assertThatThrownBy(() -> service.updateItemQuantity(10L, 555L, 3, staff)).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void aLineThatAlreadyHasFrameNumbersCannotBeEdited() {
        Order order = editable(PaymentMethod.PAY_LATER);
        OrderFixtures.unit(order.getItems().iterator().next(), 1L, "FRAME-0001", "ENGINE-0001");
        assertBad(() -> service.updateItemQuantity(10L, 100L, 3, staff));
    }

    @Test
    void anOrderWithAnOpenCancellationRequestCannotBeEdited() {
        Order order = editable(PaymentMethod.PAY_LATER);
        order.setCancellationRequestedAt(LocalDateTime.now());
        assertBad(() -> service.updateItemQuantity(10L, 100L, 3, staff));
    }

    @Test
    void raisingBeyondStockIs400AndChangesNothing() {
        Order order = editable(PaymentMethod.PAY_LATER);
        Product moto = order.getItems().iterator().next().getProduct();
        moto.setStockQuantity(0);
        assertBad(() -> service.updateItemQuantity(10L, 100L, 3, staff));
        assertThat(moto.getStockQuantity()).isZero();
        assertThat(order.getSubtotal()).isEqualByComparingTo("20000000");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void raisingByExactlyTheRemainingStockWorksAndLeavesZeroNotNegative() {
        Order order = editable(PaymentMethod.PAY_LATER);
        Product moto = order.getItems().iterator().next().getProduct();
        moto.setStockQuantity(1);
        service.updateItemQuantity(10L, 100L, 3, staff);
        assertThat(moto.getStockQuantity()).isZero();
    }

    @Test
    void raisingBeyondTheColourStockIs400() {
        Order order = editable(PaymentMethod.PAY_LATER);
        OrderItem line = order.getItems().iterator().next();
        ProductVariant red = OrderFixtures.colour(11L, line.getProduct(), "Red", 0, null);
        line.setProductVariant(red);
        assertBad(() -> service.updateItemQuantity(10L, 100L, 3, staff));
        assertThat(red.getStockQuantity()).isZero();
    }

    @Test
    void raisingAProductThatIsNoLongerSoldIs400ButLoweringStillWorks() {
        Order order = editable(PaymentMethod.PAY_LATER);
        order.getItems().iterator().next().getProduct().setActive(false);
        assertBad(() -> service.updateItemQuantity(10L, 100L, 3, staff));
        service.updateItemQuantity(10L, 100L, 1, staff);
        assertThat(order.getItems().iterator().next().getQuantity()).isEqualTo(1);
    }

    @Test
    void loweringBringsASoldOutColourBackAndTellsWaitingCustomers() {
        Order order = editable(PaymentMethod.PAY_LATER);
        OrderItem line = order.getItems().iterator().next();
        ProductVariant red = OrderFixtures.colour(11L, line.getProduct(), "Red", 0, null);
        line.setProductVariant(red);
        service.updateItemQuantity(10L, 100L, 1, staff);
        assertThat(red.getStockQuantity()).isEqualTo(1);
        verify(stockAlerts).notifyRestocked(red);
    }

    @Test
    void aGuestOrderCanBeEditedToo() {
        Order order = editable(PaymentMethod.PAY_LATER);
        order.setUser(null);
        service.updateItemQuantity(10L, 100L, 3, staff);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("29100000");
    }

    @Test
    void anAccessoryOrderGetsNoRegistrationFeeFromAnEdit() {
        Order order = OrderFixtures.order(10L, "ORD-10", OrderStatus.PENDING, alice);
        OrderFixtures.line(order, 100L, accessory, null, 1, "100000");
        order.setSubtotal(new BigDecimal("100000"));
        order.setTotalAmount(new BigDecimal("100000"));
        OrderFixtures.payment(order, PaymentMethod.PAY_LATER, PaymentStatus.PENDING, "100000");
        when(orderRepository.findLockedById(10L)).thenReturn(Optional.of(order));
        service.updateItemQuantity(10L, 100L, 4, staff);
        assertThat(order.getRegistrationFee()).isEqualByComparingTo("0");
        assertThat(order.getShippingFee()).isEqualByComparingTo("0");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("400000");
    }
}
