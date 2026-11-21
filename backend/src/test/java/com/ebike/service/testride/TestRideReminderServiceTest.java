package com.ebike.service.testride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.event.notification.TestRideNoticeEvent;
import com.ebike.listener.notification.NotificationEventBridge;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Showroom;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.testride.TestRideBooking;
import com.ebike.repository.testride.TestRideBookingRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

/** V23: the timer job reminds once and closes requests nobody confirmed; one bad booking does not stop the others. */
class TestRideReminderServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 0);

    private TestRideBookingRepository bookings;
    private TestRideReminderService service;
    private final List<Object> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        bookings = mock(TestRideBookingRepository.class);
        service = new TestRideReminderService(bookings, mock(PlatformTransactionManager.class), 24, 100);
        ApplicationEventPublisher publisher = events::add;
        new NotificationEventBridge().setApplicationEventPublisher(publisher);
        when(bookings.findReminderDueIds(any(), any(), any(Pageable.class))).thenReturn(List.of());
        when(bookings.findExpiredRequestIds(any(), any(Pageable.class))).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        new NotificationEventBridge().setApplicationEventPublisher(null);
    }

    private TestRideBooking booking(long id, String status, LocalDateTime slot) {
        User user = new User();
        user.setId(1L);
        Showroom showroom = new Showroom();
        showroom.setName("Quan 1");
        showroom.setAddress("187 Nguyen Thai Hoc");
        Product product = new Product();
        product.setName("Kinetic X");
        TestRideBooking booking = new TestRideBooking();
        ReflectionTestUtils.setField(booking, "id", id);
        booking.setUser(user);
        booking.setShowroom(showroom);
        booking.setProduct(product);
        booking.setStatus(status);
        booking.setSlotStart(slot);
        when(bookings.findLockedById(id)).thenReturn(Optional.of(booking));
        return booking;
    }

    @Test
    void aConfirmedRideInsideTheDayIsRemindedOnceAndMarked() {
        TestRideBooking booking = booking(1L, "CONFIRMED", NOW.plusHours(20));
        when(bookings.findReminderDueIds(eq(NOW), eq(NOW.plusHours(24)), any(Pageable.class))).thenReturn(List.of(1L));

        assertEquals(1, service.sendReminders(NOW));

        assertEquals(NOW, booking.getReminderSentAt());
        verify(bookings).save(booking);
        assertEquals(1, events.size());
        TestRideNoticeEvent event = (TestRideNoticeEvent) events.get(0);
        assertEquals(TestRideMessages.REMINDER, event.kind());
        assertEquals("Kinetic X", event.productName());
    }

    @Test
    void noReminderForARideThatCannotTakePlace() {
        TestRideBooking booking = booking(1L, "CONFIRMED", NOW.plusHours(20));
        booking.getShowroom().setActive(false);
        when(bookings.findReminderDueIds(any(), any(), any(Pageable.class))).thenReturn(List.of(1L));

        assertEquals(0, service.sendReminders(NOW));
        assertNull(booking.getReminderSentAt());
        assertTrue(events.isEmpty());
        booking.getShowroom().setActive(true);
        booking.getProduct().setActive(false);
        assertEquals(0, service.sendReminders(NOW));
        assertTrue(events.isEmpty());
    }

    @Test
    void aRideCancelledOrRemindedSinceTheIdsWereReadIsSkipped() {
        TestRideBooking cancelled = booking(1L, "CANCELLED", NOW.plusHours(20));
        TestRideBooking already = booking(2L, "CONFIRMED", NOW.plusHours(20));
        already.setReminderSentAt(NOW.minusMinutes(30));
        when(bookings.findReminderDueIds(any(), any(), any(Pageable.class))).thenReturn(List.of(1L, 2L, 3L));
        when(bookings.findLockedById(3L)).thenReturn(Optional.empty());

        assertEquals(0, service.sendReminders(NOW));

        verify(bookings, never()).save(any(TestRideBooking.class));
        assertNull(cancelled.getReminderSentAt());
        assertEquals(NOW.minusMinutes(30), already.getReminderSentAt());
        assertEquals(0, events.size());
    }

    @Test
    void oneBookingThatFailsDoesNotStopTheNext() {
        booking(1L, "CONFIRMED", NOW.plusHours(5));
        TestRideBooking second = booking(2L, "CONFIRMED", NOW.plusHours(6));
        when(bookings.findReminderDueIds(any(), any(), any(Pageable.class))).thenReturn(List.of(1L, 2L));
        when(bookings.save(any(TestRideBooking.class))).thenAnswer(call -> {
            TestRideBooking given = call.getArgument(0);
            if (given.getId() == 1L) {
                throw new IllegalStateException("database is busy");
            }
            return given;
        });

        assertEquals(1, service.sendReminders(NOW));

        assertNotNull(second.getReminderSentAt());
        assertEquals(1, events.size());
    }

    @Test
    void anUnconfirmedRequestWhoseTimeCameIsClosedAndTheCustomerIsTold() {
        TestRideBooking late = booking(1L, "REQUESTED", NOW.minusMinutes(5));
        when(bookings.findExpiredRequestIds(eq(NOW), any(Pageable.class))).thenReturn(List.of(1L));

        assertEquals(1, service.expireUnconfirmed(NOW));

        assertEquals(TestRideBooking.CANCELLED, late.getStatus());
        assertEquals(TestRideBooking.BY_EXPIRY, late.getCancelSource());
        verify(bookings).save(late);
        assertEquals(TestRideMessages.EXPIRED, ((TestRideNoticeEvent) events.get(0)).kind());
    }

    @Test
    void aRequestConfirmedMeanwhileOrWhoseSlotHasNotStartedIsLeftAlone() {
        TestRideBooking confirmed = booking(1L, "CONFIRMED", NOW.minusMinutes(65));
        TestRideBooking future = booking(2L, "REQUESTED", NOW.plusMinutes(30));
        when(bookings.findExpiredRequestIds(any(), any(Pageable.class))).thenReturn(List.of(1L, 2L));

        assertEquals(0, service.expireUnconfirmed(NOW));

        assertEquals(TestRideBooking.CONFIRMED, confirmed.getStatus());
        assertEquals(TestRideBooking.REQUESTED, future.getStatus());
        verify(bookings, never()).save(any(TestRideBooking.class));
        assertEquals(0, events.size());
    }
}
