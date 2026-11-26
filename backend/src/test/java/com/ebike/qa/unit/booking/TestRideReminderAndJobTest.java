package com.ebike.qa.unit.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.event.notification.TestRideNoticeEvent;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Showroom;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.VehicleType;
import com.ebike.model.entity.testride.TestRideBooking;
import com.ebike.repository.testride.TestRideBookingRepository;
import com.ebike.service.testride.TestRideJob;
import com.ebike.service.testride.TestRideMessages;
import com.ebike.service.testride.TestRideReminderService;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** The two timer tasks (reminder 24 h before, expiry of unconfirmed requests) and the job that runs them. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestRideReminderAndJobTest {

    static final LocalDateTime NOW = LocalDateTime.parse("2026-11-23T10:00");

    @Mock TestRideBookingRepository bookings;
    @Mock PlatformTransactionManager tx;

    Fx.Events events;
    User user = Fx.user(7L, "an");
    Showroom showroom = Fx.showroom(1L, true);
    Product bike = Fx.product(5L, VehicleType.E_BIKE, true);
    TestRideReminderService service;

    @BeforeEach
    void setUp() {
        events = new Fx.Events();
        when(tx.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        service = new TestRideReminderService(bookings, tx, 24, 100);
        when(bookings.save(any(TestRideBooking.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        events.close();
    }

    TestRideBooking booking(long id, String status, String slot) {
        TestRideBooking b = Fx.booking(id, user, showroom, bike, LocalDateTime.parse(slot), status);
        when(bookings.findLockedById(id)).thenReturn(Optional.of(b));
        return b;
    }

    // ------------------------------------------------------------------ reminders

    @Test
    void reminderWindowIsNowExclusiveToNowPlusReminderHoursInclusive() {
        when(bookings.findReminderDueIds(any(), any(), any(Pageable.class))).thenReturn(List.of());
        service.sendReminders(NOW);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(bookings).findReminderDueIds(eq(NOW), eq(NOW.plusHours(24)), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void aDueReminderIsStampedSavedAndPublished() {
        TestRideBooking b = booking(1, "CONFIRMED", "2026-11-24T09:00");
        when(bookings.findReminderDueIds(any(), any(), any(Pageable.class))).thenReturn(List.of(1L));
        assertThat(service.sendReminders(NOW)).isEqualTo(1);
        assertThat(b.getReminderSentAt()).isEqualTo(NOW);
        assertThat(b.getUpdatedAt()).isEqualTo(NOW);
        verify(bookings).save(b);
        assertThat(events.published).singleElement().isInstanceOfSatisfying(TestRideNoticeEvent.class, e -> {
            assertThat(e.kind()).isEqualTo(TestRideMessages.REMINDER);
            assertThat(e.userId()).isEqualTo(7L);
        });
    }

    @Test
    void aBookingCancelledBetweenTheIdQueryAndTheLockGetsNoReminder() {
        TestRideBooking b = booking(1, "CANCELLED", "2026-11-24T09:00");
        when(bookings.findReminderDueIds(any(), any(), any(Pageable.class))).thenReturn(List.of(1L));
        assertThat(service.sendReminders(NOW)).isZero();
        assertThat(b.getReminderSentAt()).isNull();
        assertThat(events.published).isEmpty();
        verify(bookings, never()).save(any());
    }

    @Test
    void aReminderAlreadySentByAnotherRunIsNotSentTwice() {
        TestRideBooking b = booking(1, "CONFIRMED", "2026-11-24T09:00");
        b.setReminderSentAt(NOW.minusMinutes(30));
        when(bookings.findReminderDueIds(any(), any(), any(Pageable.class))).thenReturn(List.of(1L));
        assertThat(service.sendReminders(NOW)).isZero();
        assertThat(events.published).isEmpty();
    }

    @Test
    void noReminderForAShowroomSwitchedOffOrABikeNoLongerSold() {
        TestRideBooking b = booking(1, "CONFIRMED", "2026-11-24T09:00");
        when(bookings.findReminderDueIds(any(), any(), any(Pageable.class))).thenReturn(List.of(1L));
        showroom.setActive(false);
        assertThat(service.sendReminders(NOW)).isZero();
        showroom.setActive(true);
        bike.setActive(false);
        assertThat(service.sendReminders(NOW)).isZero();
        assertThat(b.getReminderSentAt()).isNull();
    }

    @Test
    void noReminderWhenTheRideIsBeyondTheWindowOrAlreadyStarted() {
        booking(1, "CONFIRMED", "2026-11-24T10:01");
        booking(2, "CONFIRMED", "2026-11-23T10:00");
        when(bookings.findReminderDueIds(any(), any(), any(Pageable.class))).thenReturn(List.of(1L, 2L));
        assertThat(service.sendReminders(NOW)).isZero();
    }

    @Test
    void aBookingThatVanishedIsSkipped() {
        when(bookings.findLockedById(9L)).thenReturn(Optional.empty());
        when(bookings.findReminderDueIds(any(), any(), any(Pageable.class))).thenReturn(List.of(9L));
        assertThat(service.sendReminders(NOW)).isZero();
    }

    @Test
    void oneFailingBookingDoesNotStopTheOthers() {
        booking(1, "CONFIRMED", "2026-11-24T09:00");
        TestRideBooking second = booking(2, "CONFIRMED", "2026-11-24T09:30");
        when(bookings.findReminderDueIds(any(), any(), any(Pageable.class))).thenReturn(List.of(1L, 2L));
        when(bookings.save(argThatId(1L))).thenThrow(new IllegalStateException("db down"));
        assertThat(service.sendReminders(NOW)).isEqualTo(1);
        assertThat(second.getReminderSentAt()).isEqualTo(NOW);
        assertThat(events.published).hasSize(1);
    }

    private static TestRideBooking argThatId(long id) {
        return org.mockito.ArgumentMatchers.argThat(b -> b != null && b.getId() != null && b.getId() == id);
    }

    @Test
    void everyBookingRunsInItsOwnNewTransaction() {
        booking(1, "CONFIRMED", "2026-11-24T09:00");
        booking(2, "CONFIRMED", "2026-11-24T09:30");
        when(bookings.findReminderDueIds(any(), any(), any(Pageable.class))).thenReturn(List.of(1L, 2L));
        service.sendReminders(NOW);
        ArgumentCaptor<TransactionDefinition> def = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(tx, times(2)).getTransaction(def.capture());
        assertThat(def.getAllValues()).allMatch(d -> d.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        verify(tx, times(2)).commit(any(TransactionStatus.class));
    }

    @Test
    void maxPerRunIsClampedToOneAndReminderHoursToOne() {
        TestRideReminderService tiny = new TestRideReminderService(bookings, tx, 0, 0);
        when(bookings.findReminderDueIds(any(), any(), any(Pageable.class))).thenReturn(List.of());
        tiny.sendReminders(NOW);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(bookings).findReminderDueIds(eq(NOW), eq(NOW.plusHours(1)), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ expiry

    @Test
    void expiryCutoffIsOneHourBeforeNow() {
        when(bookings.findExpiredRequestIds(any(), any(Pageable.class))).thenReturn(List.of());
        service.expireUnconfirmed(NOW);
        verify(bookings).findExpiredRequestIds(eq(NOW.minusHours(1)), any(Pageable.class));
    }

    @Test
    void anUnconfirmedRequestWhoseHourIsOverIsClosedAsExpiredAndTheCustomerTold() {
        TestRideBooking b = booking(1, "REQUESTED", "2026-11-23T09:00"); // hour ends exactly at NOW
        when(bookings.findExpiredRequestIds(any(), any(Pageable.class))).thenReturn(List.of(1L));
        assertThat(service.expireUnconfirmed(NOW)).isEqualTo(1);
        assertThat(b.getStatus()).isEqualTo("CANCELLED");
        assertThat(b.getCancelSource()).isEqualTo(TestRideBooking.BY_EXPIRY);
        assertThat(b.getCancelledByCustomerAt()).isNull();
        assertThat(b.getUpdatedAt()).isEqualTo(NOW);
        assertThat(events.published).singleElement().isInstanceOfSatisfying(TestRideNoticeEvent.class, e -> assertThat(e.kind()).isEqualTo(TestRideMessages.EXPIRED));
    }

    @Test
    void aRequestConfirmedByStaffMeanwhileIsNotExpired() {
        TestRideBooking b = booking(1, "CONFIRMED", "2026-11-23T08:00");
        when(bookings.findExpiredRequestIds(any(), any(Pageable.class))).thenReturn(List.of(1L));
        assertThat(service.expireUnconfirmed(NOW)).isZero();
        assertThat(b.getStatus()).isEqualTo("CONFIRMED");
        assertThat(events.published).isEmpty();
    }

    @Test
    void aRequestWhoseHourIsNotYetOverIsNotExpiredEvenIfTheIdQueryReturnedIt() {
        TestRideBooking b = booking(1, "REQUESTED", "2026-11-23T09:01");
        when(bookings.findExpiredRequestIds(any(), any(Pageable.class))).thenReturn(List.of(1L));
        assertThat(service.expireUnconfirmed(NOW)).isZero();
        assertThat(b.getStatus()).isEqualTo("REQUESTED");
    }

    @Test
    void expiringTwiceDoesNotNotifyTwice() {
        booking(1, "REQUESTED", "2026-11-22T09:00");
        when(bookings.findExpiredRequestIds(any(), any(Pageable.class))).thenReturn(List.of(1L));
        assertThat(service.expireUnconfirmed(NOW)).isEqualTo(1);
        assertThat(service.expireUnconfirmed(NOW)).isZero();
        assertThat(events.published).hasSize(1);
    }

    @Test
    void oneFailingExpiryDoesNotStopTheOthers() {
        booking(1, "REQUESTED", "2026-11-22T09:00");
        TestRideBooking second = booking(2, "REQUESTED", "2026-11-22T10:00");
        when(bookings.findExpiredRequestIds(any(), any(Pageable.class))).thenReturn(List.of(1L, 2L));
        when(bookings.save(argThatId(1L))).thenThrow(new IllegalStateException("db down"));
        assertThat(service.expireUnconfirmed(NOW)).isEqualTo(1);
        assertThat(second.getStatus()).isEqualTo("CANCELLED");
    }

    // ------------------------------------------------------------------ the job

    @Test
    void jobRunsExpiryBeforeRemindersWithTheShopsLocalNow() {
        TestRideReminderService mock = mock(TestRideReminderService.class);
        TestRideJob job = new TestRideJob(mock, "Asia/Ho_Chi_Minh");
        LocalDateTime before = LocalDateTime.now(Fx.VN);
        job.run();
        LocalDateTime after = LocalDateTime.now(Fx.VN);
        ArgumentCaptor<LocalDateTime> t1 = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> t2 = ArgumentCaptor.forClass(LocalDateTime.class);
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(mock);
        order.verify(mock).expireUnconfirmed(t1.capture());
        order.verify(mock).sendReminders(t2.capture());
        assertThat(t1.getValue()).isBetween(before, after);
        assertThat(t2.getValue()).isEqualTo(t1.getValue());
    }

    @Test
    void jobUsesTheConfiguredZoneNotTheServerZone() {
        TestRideReminderService mock = mock(TestRideReminderService.class);
        TestRideJob job = new TestRideJob(mock, "Pacific/Kiritimati"); // UTC+14: always a different date/time from Ho Chi Minh
        job.run();
        ArgumentCaptor<LocalDateTime> t = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mock).expireUnconfirmed(t.capture());
        LocalDateTime kiritimati = LocalDateTime.now(java.time.ZoneId.of("Pacific/Kiritimati"));
        assertThat(java.time.Duration.between(t.getValue(), kiritimati).abs()).isLessThan(java.time.Duration.ofMinutes(1));
    }

    @Test
    void aFailureOfTheExpiryDoesNotStopTheReminders() {
        TestRideReminderService mock = mock(TestRideReminderService.class);
        when(mock.expireUnconfirmed(any())).thenThrow(new IllegalStateException("boom"));
        new TestRideJob(mock, "Asia/Ho_Chi_Minh").run();
        verify(mock).sendReminders(any());
    }

    @Test
    void aFailureOfTheRemindersDoesNotEscapeTheJob() {
        TestRideReminderService mock = mock(TestRideReminderService.class);
        when(mock.sendReminders(any())).thenThrow(new IllegalStateException("boom"));
        new TestRideJob(mock, "Asia/Ho_Chi_Minh").run(); // would break the scheduler thread if it escaped
        verify(mock).expireUnconfirmed(any());
    }

    @Test
    void jobIsScheduledEveryHalfHourInTheShopZone() throws Exception {
        Method run = TestRideJob.class.getMethod("run");
        Scheduled scheduled = run.getAnnotation(Scheduled.class);
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).contains("app.test-ride.cron").contains("0 */30 * * * *");
        assertThat(scheduled.zone()).contains("app.time-zone").contains("Asia/Ho_Chi_Minh");
    }

    @Test
    void jobRejectsAnInvalidZoneAtStartup() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TestRideJob(mock(TestRideReminderService.class), "Nowhere/Land"))
            .isInstanceOf(java.time.DateTimeException.class);
    }
}
