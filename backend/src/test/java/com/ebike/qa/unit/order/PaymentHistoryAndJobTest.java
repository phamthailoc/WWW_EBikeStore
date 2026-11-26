package com.ebike.qa.unit.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.order.response.PaymentHistoryResponse;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Order;
import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.Payment;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.PaymentRepository;
import com.ebike.service.order.OrderService;
import com.ebike.service.order.impl.PaymentHistoryServiceImpl;
import com.ebike.service.order.impl.UnpaidOrderExpiryJob;
import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentHistoryAndJobTest {

    @Mock PaymentRepository payments;
    @Mock UserRepository users;
    @Mock OrderService orderService;

    PaymentHistoryServiceImpl history;
    User alice;

    @BeforeEach
    void setUp() {
        history = new PaymentHistoryServiceImpl(payments, users);
        alice = OrderFixtures.user(1L, "alice", "alice@example.com");
        when(users.findByUsername("alice")).thenReturn(Optional.of(alice));
    }

    private static void assertStatus(HttpStatus status, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(status));
    }

    // --- payment history -----------------------------------------------------------------------------

    @Test
    void anonymousOrNamelessCallerIs401() {
        assertStatus(HttpStatus.UNAUTHORIZED, () -> history.getMyPaymentHistory(null, null));
        assertStatus(HttpStatus.UNAUTHORIZED, () -> history.getMyPaymentHistory(OrderFixtures.auth(" "), null));
        verify(payments, never()).findHistoryByUserId(any(), any());
    }

    @Test
    void unknownAccountIs401() {
        assertStatus(HttpStatus.UNAUTHORIZED, () -> history.getMyPaymentHistory(OrderFixtures.customer("ghost"), null));
    }

    @Test
    void anAccountCanBeFoundByEmailToo() {
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        history.getMyPaymentHistory(OrderFixtures.customer("alice@example.com"), null);
        verify(payments).findHistoryByUserId(1L, null);
    }

    @Test
    void theHistoryIsQueriedForTheCallerOnly() {
        history.getMyPaymentHistory(OrderFixtures.customer("alice"), null);
        verify(payments).findHistoryByUserId(1L, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"paid", " PAID ", "Paid"})
    void statusFilterIsCaseInsensitiveAndTrimmed(String raw) {
        history.getMyPaymentHistory(OrderFixtures.customer("alice"), raw);
        verify(payments).findHistoryByUserId(1L, PaymentStatus.PAID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blankFilterMeansAll(String raw) {
        history.getMyPaymentHistory(OrderFixtures.customer("alice"), raw);
        verify(payments).findHistoryByUserId(1L, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DONE", "PAID;DROP", "0", "pa id"})
    void unknownFilterIs400(String raw) {
        assertStatus(HttpStatus.BAD_REQUEST, () -> history.getMyPaymentHistory(OrderFixtures.customer("alice"), raw));
        verify(payments, never()).findHistoryByUserId(any(), any());
    }

    @Test
    void rowsAreMappedFromThePaymentAndItsOrder() {
        Order order = OrderFixtures.order(10L, "ORD-10", OrderStatus.CONFIRMED, alice);
        Payment payment = OrderFixtures.payment(order, PaymentMethod.VNPAY, PaymentStatus.PAID, "1500000");
        payment.setTransactionReference("EBIKE1");
        payment.setProviderTxnId("T9");
        payment.setPaidAt(LocalDateTime.of(2026, 11, 1, 9, 0));
        when(payments.findHistoryByUserId(1L, null)).thenReturn(List.of(payment));

        List<PaymentHistoryResponse> rows = history.getMyPaymentHistory(OrderFixtures.customer("alice"), null);

        assertThat(rows).hasSize(1);
        PaymentHistoryResponse row = rows.get(0);
        assertThat(row.orderId()).isEqualTo(10L);
        assertThat(row.orderNumber()).isEqualTo("ORD-10");
        assertThat(row.amount()).isEqualByComparingTo("1500000");
        assertThat(row.paymentMethod()).isEqualTo("VNPAY");
        assertThat(row.paymentStatus()).isEqualTo("PAID");
        assertThat(row.currency()).isEqualTo("VND");
        assertThat(row.paidAt()).isEqualTo(LocalDateTime.of(2026, 11, 1, 9, 0));
    }

    // --- expiry job ----------------------------------------------------------------------------------

    private UnpaidOrderExpiryJob job(Clock clock) throws Exception {
        Constructor<UnpaidOrderExpiryJob> constructor = UnpaidOrderExpiryJob.class.getDeclaredConstructor(OrderService.class, Clock.class);
        constructor.setAccessible(true);
        return constructor.newInstance(orderService, clock);
    }

    @Test
    void theJobAsksTheServiceToExpireOrdersAsOfNowOnItsClock() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-11-26T05:00:00Z"), ZoneOffset.UTC);
        when(orderService.expireUnpaidOrders(LocalDateTime.of(2026, 11, 26, 5, 0))).thenReturn(3);
        job(clock).expireUnpaidOrders();
        verify(orderService).expireUnpaidOrders(LocalDateTime.of(2026, 11, 26, 5, 0));
    }

    @Test
    void theJobUsesTheLocalTimeOfItsClockZone() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-11-26T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        job(clock).expireUnpaidOrders();
        verify(orderService).expireUnpaidOrders(LocalDateTime.of(2026, 11, 26, 12, 0));
    }

    @Test
    void theJobWorksWhenNothingExpired() throws Exception {
        when(orderService.expireUnpaidOrders(any())).thenReturn(0);
        job(Clock.systemDefaultZone()).expireUnpaidOrders();
        verify(orderService).expireUnpaidOrders(any(LocalDateTime.class));
    }

    @Test
    void theJobIsScheduledWithAConfigurableIntervalAndAStartupDelay() throws Exception {
        Scheduled scheduled = UnpaidOrderExpiryJob.class.getMethod("expireUnpaidOrders").getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString()).isEqualTo("${app.order.expiry-check-interval:PT5M}");
        assertThat(scheduled.initialDelayString()).isEqualTo("PT1M");
    }
}
