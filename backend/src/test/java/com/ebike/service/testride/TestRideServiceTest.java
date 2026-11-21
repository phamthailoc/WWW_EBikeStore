package com.ebike.service.testride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.testride.TestRideCreateRequest;
import com.ebike.dto.testride.TestRideResponse;
import com.ebike.dto.testride.TestRideSlotResponse;
import com.ebike.dto.testride.TestRideUpdateRequest;
import com.ebike.event.notification.TestRideNoticeEvent;
import com.ebike.listener.notification.NotificationEventBridge;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Showroom;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.ProductSpecification;
import com.ebike.model.entity.product.VehicleType;
import com.ebike.model.entity.testride.TestRideBooking;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.ShowroomRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.repository.testride.TestRideBookingRepository;
import com.ebike.service.admin.AuditLogService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** V23: who may book which slot, and how the staff move a booking along. */
class TestRideServiceTest {

    /** A Saturday morning. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 0);
    private static final LocalDateTime SLOT = LocalDateTime.of(2026, 11, 22, 9, 0);

    private TestRideBookingRepository bookings;
    private ShowroomRepository showrooms;
    private ProductRepository products;
    private UserRepository users;
    private TestRideService service;
    private User an;
    private Showroom showroom;
    private Product bike;
    private final List<Object> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        bookings = mock(TestRideBookingRepository.class);
        showrooms = mock(ShowroomRepository.class);
        products = mock(ProductRepository.class);
        users = mock(UserRepository.class);
        ZoneId zone = ZoneId.systemDefault();
        service = new TestRideService(bookings, showrooms, products, users, mock(AuditLogService.class),
            14, 2, 120, 3, 24, Clock.fixed(NOW.atZone(zone).toInstant(), zone));
        ApplicationEventPublisher publisher = events::add;
        new NotificationEventBridge().setApplicationEventPublisher(publisher);

        an = new User();
        an.setId(1L);
        an.setUsername("an");
        an.setEmail("an@example.com");
        an.setFirstName("An");
        an.setLastName("Nguyen");
        showroom = new Showroom();
        showroom.setId(10L);
        showroom.setName("Quan 1");
        showroom.setAddress("187 Nguyen Thai Hoc");
        showroom.setOpeningHours("08:00 - 21:00");
        showroom.setActive(true);
        bike = new Product();
        bike.setId(5L);
        bike.setName("Kinetic X");
        bike.setSlug("kinetic-x");
        bike.setActive(true);
        ProductSpecification spec = new ProductSpecification();
        spec.setVehicleType(VehicleType.E_SCOOTER);
        bike.setSpecification(spec);

        when(showrooms.findLockedById(10L)).thenReturn(Optional.of(showroom));
        when(showrooms.findById(10L)).thenReturn(Optional.of(showroom));
        when(users.findLockedByUsername("an")).thenReturn(Optional.of(an));
        when(products.findById(5L)).thenReturn(Optional.of(bike));
        when(bookings.saveAndFlush(any(TestRideBooking.class))).thenAnswer(call -> call.getArgument(0));
        when(bookings.save(any(TestRideBooking.class))).thenAnswer(call -> call.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        new NotificationEventBridge().setApplicationEventPublisher(null);
    }

    private static TestRideCreateRequest request(String slot) {
        return new TestRideCreateRequest(5L, 10L, slot, "0901 234 567", "  first time  ");
    }

    private HttpStatus statusOf(Runnable action) {
        ResponseStatusException thrown = assertThrows(ResponseStatusException.class, action::run);
        return HttpStatus.valueOf(thrown.getStatusCode().value());
    }

    private TestRideBooking bookingIn(String status, LocalDateTime slot) {
        TestRideBooking booking = new TestRideBooking();
        ReflectionTestUtils.setField(booking, "id", 77L);
        booking.setUser(an);
        booking.setProduct(bike);
        booking.setShowroom(showroom);
        booking.setSlotStart(slot);
        booking.setStatus(status);
        booking.setContactPhone("0901234567");
        when(bookings.findLockedById(77L)).thenReturn(Optional.of(booking));
        return booking;
    }

    // ---- booking ----

    @Test
    void aCustomerBooksAFreeSlot() {
        TestRideResponse saved = service.book("an", request("2026-11-22T09:00"));
        ArgumentCaptor<TestRideBooking> stored = ArgumentCaptor.forClass(TestRideBooking.class);
        verify(bookings).saveAndFlush(stored.capture());
        assertEquals(TestRideBooking.REQUESTED, stored.getValue().getStatus());
        assertEquals("0901234567", stored.getValue().getContactPhone(), "spaces are taken out of the number");
        assertEquals("first time", stored.getValue().getCustomerNote());
        assertEquals(SLOT, stored.getValue().getSlotStart());
        assertEquals(NOW, stored.getValue().getCreatedAt(), "the stamps come from the showroom clock, not the server zone");
        assertEquals(NOW, stored.getValue().getUpdatedAt());
        assertEquals("REQUESTED", saved.status());
        assertEquals("Quan 1", saved.showroomName());
        assertTrue(saved.canCancel());
        assertTrue(saved.nextSteps().isEmpty(), "the customer is not offered the staff's steps");
        assertTrue(events.isEmpty(), "booking tells nobody");
    }

    @Test
    void oneAccountCannotBookWithoutEndInAFewMinutes() {
        when(bookings.countByUserIdAndCreatedAtGreaterThanEqual(1L, NOW.minusMinutes(10))).thenReturn(10L);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, statusOf(() -> service.book("an", request("2026-11-22T09:00"))));
        verify(bookings, never()).saveAndFlush(any(TestRideBooking.class));
        when(bookings.countByUserIdAndCreatedAtGreaterThanEqual(1L, NOW.minusMinutes(10))).thenReturn(9L);
        service.book("an", request("2026-11-22T09:00"));
        verify(bookings).saveAndFlush(any(TestRideBooking.class));
    }

    @Test
    void anEmptyNoteIsNotStored() {
        service.book("an", new TestRideCreateRequest(5L, 10L, "2026-11-22T09:00", "0901234567", "   "));
        ArgumentCaptor<TestRideBooking> stored = ArgumentCaptor.forClass(TestRideBooking.class);
        verify(bookings).saveAndFlush(stored.capture());
        assertNull(stored.getValue().getCustomerNote());
    }

    @Test
    void theShowroomIsLockedBeforeAnythingIsReadSoTwoCustomersCannotTakeTheLastPlace() {
        service.book("an", request("2026-11-22T09:00"));
        var order = inOrder(showrooms, users, bookings);
        order.verify(showrooms).findLockedById(10L);
        order.verify(users).findLockedByUsername("an");
        order.verify(bookings).countAt(eq(10L), eq(SLOT), anyCollection());
        order.verify(bookings).saveAndFlush(any(TestRideBooking.class));
    }

    @Test
    void aVisitorWithoutAnAccountIsToldToSignIn() {
        assertEquals(HttpStatus.UNAUTHORIZED, statusOf(() -> service.book(null, request("2026-11-22T09:00"))));
        assertEquals(HttpStatus.UNAUTHORIZED, statusOf(() -> service.book("  ", request("2026-11-22T09:00"))));
        assertEquals(HttpStatus.UNAUTHORIZED, statusOf(() -> service.mine(null)));
        assertEquals(HttpStatus.UNAUTHORIZED, statusOf(() -> service.cancelOwn(null, 1L)));
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void aRequestWithAMissingPartIsRefused() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.book("an", null)));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.book("an", new TestRideCreateRequest(null, 10L, "2026-11-22T09:00", "0901234567", null))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.book("an", new TestRideCreateRequest(5L, null, "2026-11-22T09:00", "0901234567", null))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.book("an", new TestRideCreateRequest(5L, 10L, null, "0901234567", null))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.book("an", new TestRideCreateRequest(5L, 10L, " ", "0901234567", null))));
    }

    @Test
    void aBadPhoneNumberOrATimeThatIsNotATimeIsRefused() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.book("an", new TestRideCreateRequest(5L, 10L, "2026-11-22T09:00", "12345", null))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.book("an", new TestRideCreateRequest(5L, 10L, "2026-11-22T09:00", null, null))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.book("an", request("tomorrow morning"))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.book("an", request("22/11/2026 09:00"))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.book("an",
            new TestRideCreateRequest(5L, 10L, "2026-11-22T09:00", "0901234567", "x".repeat(501)))));
        verify(showrooms, never()).findLockedById(anyLong());
    }

    @Test
    void aShowroomThatDoesNotExistOrIsClosedForGoodTakesNoBookings() {
        when(showrooms.findLockedById(99L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> service.book("an", new TestRideCreateRequest(5L, 99L, "2026-11-22T09:00", "0901234567", null))));
        showroom.setActive(false);
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> service.book("an", request("2026-11-22T09:00"))));
    }

    @Test
    void aBikeThatIsMissingWithdrawnOrNotARideableThingTakesNoBookings() {
        when(products.findById(404L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> service.book("an", new TestRideCreateRequest(404L, 10L, "2026-11-22T09:00", "0901234567", null))));
        bike.setActive(false);
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> service.book("an", request("2026-11-22T09:00"))));
        bike.setActive(true);
        bike.getSpecification().setVehicleType(VehicleType.BATTERY);
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.book("an", request("2026-11-22T09:00"))));
        bike.getSpecification().setVehicleType(VehicleType.ACCESSORY);
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.book("an", request("2026-11-22T09:00"))));
        bike.setSpecification(null);
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.book("an", request("2026-11-22T09:00"))));
    }

    @Test
    void aTimeOutsideOpeningHoursOrBetweenSlotsIsRefused() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.book("an", request("2026-11-22T07:00"))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.book("an", request("2026-11-22T21:00"))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.book("an", request("2026-11-22T09:30"))));
        service.book("an", request("2026-11-22T20:00")); // the last slot of the day
    }

    @Test
    void aTimeThatHasPassedSaysSoInsteadOfTalkingAboutLeadTime() {
        ResponseStatusException past = assertThrows(ResponseStatusException.class, () -> service.book("an", request("2026-11-20T09:00")));
        assertEquals(HttpStatus.BAD_REQUEST.value(), past.getStatusCode().value());
        assertTrue(past.getReason().contains("đã qua"), past.getReason());
        ResponseStatusException soon = assertThrows(ResponseStatusException.class, () -> service.book("an", request("2026-11-21T11:00")));
        assertTrue(soon.getReason().contains("trước"), soon.getReason());
    }

    @Test
    void theBacklogCountsWaitingRequestsAndConfirmedRidesPastTheirTime() {
        when(bookings.countRequestedAvailable(NOW)).thenReturn(4L);
        // a ride is overdue once its whole hour is over, not when it has just begun
        when(bookings.countByStatusAndSlotStartLessThanEqual("CONFIRMED", NOW.minusHours(1))).thenReturn(2L);
        var backlog = service.backlog();
        assertEquals(4L, backlog.pending());
        assertEquals(2L, backlog.overdue());
        assertEquals(0L, backlog.customerCancelled());
    }

    @Test
    void theBacklogCountsOpenBookingsOfAShowroomOrBikeThatIsSwitchedOff() {
        when(bookings.countOpenUnavailable(anyCollection(), eq(NOW.minusHours(1)))).thenReturn(5L);
        assertEquals(5L, service.backlog().unavailable());
    }

    @Test
    void aBikeThatIsNoLongerSoldHasNoLinkInTheBookingList() {
        TestRideBooking booking = bookingIn("CONFIRMED", SLOT);
        assertNotNull(service.update(77L, new TestRideUpdateRequest(null, "note")).productSlug());
        booking.getProduct().setActive(false);
        assertNull(service.update(77L, new TestRideUpdateRequest(null, "note two")).productSlug());
    }

    @Test
    void theBacklogCountsConfirmedRidesTheCustomerWithdrewInTheNextDay() {
        when(bookings.countCancelledByCustomerBetween(NOW, NOW.plusHours(24))).thenReturn(3L);
        assertEquals(3L, service.backlog().customerCancelled());
    }

    @Test
    void theOpeningHoursOfTheShowroomDecideTheGrid() {
        showroom.setOpeningHours("08:30 - 17:30");
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.book("an", request("2026-11-22T09:00"))));
        service.book("an", request("2026-11-22T09:30"));
    }

    @Test
    void theWindowIsFourteenDaysAndTheLeadTimeIsTwoHours() {
        service.book("an", request("2026-12-05T20:00")); // the 14th day after today
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.book("an", request("2026-12-06T09:00"))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.book("an", request("2026-11-21T11:00"))), "one hour from now is too soon");
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.book("an", request("2026-11-21T09:00"))), "already past");
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.book("an", request("2026-11-20T09:00"))));
        service.book("an", request("2026-11-21T12:00")); // exactly two hours from now
    }

    @Test
    void aCustomerCannotHoldTwoBookingsAtTheSameTime() {
        when(bookings.existsByUserIdAndStatusInAndSlotStartGreaterThanAndSlotStartLessThan(
            eq(1L), anyCollection(), eq(SLOT.minusHours(1)), eq(SLOT.plusHours(1)))).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.book("an", request("2026-11-22T09:00"))));
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void aCustomerHasAtMostThreeBookingsComingUp() {
        when(bookings.countByUserIdAndStatusInAndSlotStartGreaterThanEqual(eq(1L), anyCollection(), eq(NOW))).thenReturn(3L);
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.book("an", request("2026-11-22T09:00"))));
        when(bookings.countByUserIdAndStatusInAndSlotStartGreaterThanEqual(eq(1L), anyCollection(), eq(NOW))).thenReturn(2L);
        service.book("an", request("2026-11-22T09:00"));
    }

    @Test
    void aFullSlotIsRefusedButTheLastPlaceIsGiven() {
        when(bookings.countAt(eq(10L), eq(SLOT), anyCollection())).thenReturn(2L);
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.book("an", request("2026-11-22T09:00"))));
        when(bookings.countAt(eq(10L), eq(SLOT), anyCollection())).thenReturn(1L);
        service.book("an", request("2026-11-22T09:00"));
        // another slot of the same showroom is not affected
        when(bookings.countAt(eq(10L), eq(SLOT.plusHours(1)), anyCollection())).thenReturn(0L);
        service.book("an", request("2026-11-22T10:00"));
    }

    @Test
    void aSecondRequestThatSlipsPastTheCheckIsStoppedByTheDatabaseKey() {
        when(bookings.saveAndFlush(any(TestRideBooking.class))).thenThrow(new DataIntegrityViolationException("uk_test_ride_active_user_slot"));
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.book("an", request("2026-11-22T09:00"))));
    }

    @Test
    void anotherDataErrorIsNotAnsweredAsADoubleBooking() {
        when(bookings.saveAndFlush(any(TestRideBooking.class))).thenThrow(new DataIntegrityViolationException("Data too long for column 'contact_phone'"));
        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class, () -> service.book("an", request("2026-11-22T09:00")));
    }

    @Test
    void closedBookingsDoNotHoldAPlace() {
        service.book("an", request("2026-11-22T09:00"));
        ArgumentCaptor<Collection<String>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(bookings).countAt(eq(10L), eq(SLOT), statuses.capture());
        assertEquals(Set.of("REQUESTED", "CONFIRMED"), Set.copyOf(statuses.getValue()));
    }

    // ---- slots ----

    @Test
    void theSlotListShowsThePlacesLeftAndWhichOnesCanBeTaken() {
        LocalDateTime today9 = LocalDateTime.of(2026, 11, 21, 9, 0);
        when(bookings.countBySlot(eq(10L), any(), any(), anyCollection()))
            .thenReturn(List.<Object[]>of(new Object[] {LocalDateTime.of(2026, 11, 21, 14, 0), 2L}, new Object[] {LocalDateTime.of(2026, 11, 21, 15, 0), 1L}));
        List<TestRideSlotResponse> slots = service.slots(10L, "2026-11-21");
        assertEquals(13, slots.size());
        TestRideSlotResponse nine = slots.stream().filter(slot -> slot.start().equals(today9)).findFirst().orElseThrow();
        assertFalse(nine.bookable(), "already past");
        TestRideSlotResponse eleven = slots.stream().filter(slot -> slot.start().getHour() == 11).findFirst().orElseThrow();
        assertFalse(eleven.bookable(), "inside the two hour lead time");
        TestRideSlotResponse noon = slots.stream().filter(slot -> slot.start().getHour() == 12).findFirst().orElseThrow();
        assertTrue(noon.bookable());
        assertEquals(2, noon.remaining());
        TestRideSlotResponse two = slots.stream().filter(slot -> slot.start().getHour() == 14).findFirst().orElseThrow();
        assertEquals(0, two.remaining());
        assertFalse(two.bookable(), "full");
        TestRideSlotResponse three = slots.stream().filter(slot -> slot.start().getHour() == 15).findFirst().orElseThrow();
        assertEquals(1, three.remaining());
        assertTrue(three.bookable());
    }

    @Test
    void theSlotListRefusesADayOutsideTheWindowOrABadRequest() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.slots(10L, "2026-11-20")));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.slots(10L, "2026-12-06")));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.slots(10L, "soon")));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.slots(10L, null)));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.slots(null, "2026-11-22")));
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> service.slots(404L, "2026-11-22")));
        showroom.setActive(false);
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> service.slots(10L, "2026-11-22")));
    }

    // ---- the customer cancels ----

    @Test
    void aCustomerCancelsTheirOwnBookingBeforeItBegins() {
        TestRideBooking booking = bookingIn("CONFIRMED", SLOT);
        TestRideResponse response = service.cancelOwn("an", 77L);
        assertEquals(TestRideBooking.CANCELLED, booking.getStatus());
        assertEquals("CANCELLED", response.status());
        assertEquals(TestRideBooking.BY_CUSTOMER, response.cancelledBy());
        assertEquals(NOW, response.cancelledByCustomerAt());
        assertFalse(response.canCancel());
        assertTrue(events.isEmpty(), "the customer needs no message about their own cancellation");
        assertEquals(NOW, booking.getCancelledByCustomerAt(), "a ride the staff had confirmed is marked for the backlog");
    }

    @Test
    void withdrawingARequestNobodyConfirmedLeavesNoMarkForTheStaff() {
        TestRideBooking booking = bookingIn("REQUESTED", SLOT);
        service.cancelOwn("an", 77L);
        assertNull(booking.getCancelledByCustomerAt());
    }

    @Test
    void aBookingOfSomeoneElseLooksLikeItDoesNotExist() {
        bookingIn("REQUESTED", SLOT);
        User other = new User();
        other.setId(2L);
        other.setUsername("binh");
        when(users.findLockedByUsername("binh")).thenReturn(Optional.of(other));
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> service.cancelOwn("binh", 77L)));
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> service.cancelOwn("an", 404L)));
    }

    @Test
    void aStartedOrClosedBookingCannotBeCancelledByTheCustomer() {
        bookingIn("CONFIRMED", NOW);
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.cancelOwn("an", 77L)));
        bookingIn("CONFIRMED", NOW.minusHours(2));
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.cancelOwn("an", 77L)));
        bookingIn("DONE", SLOT);
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.cancelOwn("an", 77L)));
        bookingIn("CANCELLED", SLOT);
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.cancelOwn("an", 77L)));
    }

    // ---- the staff ----

    @Test
    void confirmingTellsTheCustomerAndRemembersWhen() {
        TestRideBooking booking = bookingIn("REQUESTED", NOW.plusDays(3));
        TestRideResponse response = service.update(77L, new TestRideUpdateRequest("confirmed", "See you"));
        assertEquals(TestRideBooking.CONFIRMED, booking.getStatus());
        assertEquals(NOW, booking.getConfirmedAt());
        assertNull(booking.getReminderSentAt(), "the ride is more than a day away, so the reminder is still to come");
        assertEquals("See you", booking.getStaffNote());
        assertEquals(List.of("CANCELLED"), response.nextSteps());
        assertEquals(1, events.size());
        TestRideNoticeEvent event = (TestRideNoticeEvent) events.get(0);
        assertEquals(TestRideMessages.CONFIRMED, event.kind());
        assertEquals(1L, event.userId());
        assertEquals("Kinetic X", event.productName());
        assertEquals("Quan 1", event.showroomName());
        assertEquals("187 Nguyen Thai Hoc", event.showroomAddress());
        assertEquals("See you", event.note());
    }

    @Test
    void aRideConfirmedInsideTheLastDayGetsNoSecondReminder() {
        TestRideBooking booking = bookingIn("REQUESTED", NOW.plusHours(20));
        service.update(77L, new TestRideUpdateRequest("CONFIRMED", null));
        assertEquals(NOW, booking.getReminderSentAt(), "the confirmation already names the day");
    }

    @Test
    void aCancellationNeedsAReasonAndTellsTheCustomer() {
        TestRideBooking booking = bookingIn("CONFIRMED", NOW.plusDays(2));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.update(77L, new TestRideUpdateRequest("CANCELLED", null))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.update(77L, new TestRideUpdateRequest("CANCELLED", "   "))));
        assertEquals(TestRideBooking.CONFIRMED, booking.getStatus());
        assertTrue(events.isEmpty());
        TestRideResponse cancelled = service.update(77L, new TestRideUpdateRequest("CANCELLED", "The bike is in service"));
        assertEquals(TestRideBooking.CANCELLED, booking.getStatus());
        assertEquals(TestRideBooking.BY_STORE, cancelled.cancelledBy());
        assertEquals(NOW, booking.getUpdatedAt());
        assertEquals(TestRideMessages.CANCELLED, ((TestRideNoticeEvent) events.get(0)).kind());
    }

    @Test
    void aRideWhoseHourIsOverCannotBeCancelledNoMatterWhoAsks() {
        TestRideBooking booking = bookingIn("CONFIRMED", NOW.minusHours(2));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.update(77L, new TestRideUpdateRequest("CANCELLED", "Too late"))));
        assertEquals(TestRideBooking.CONFIRMED, booking.getStatus());
        assertTrue(events.isEmpty(), "no 'we cancelled your ride' for a ride that is past");
        assertEquals(List.of("DONE", "NO_SHOW"), service.update(77L, new TestRideUpdateRequest(null, "Waited")).nextSteps());
    }

    @Test
    void aRideAtASwitchedOffShowroomOrOfAWithdrawnBikeCannotBeConfirmed() {
        TestRideBooking booking = bookingIn("REQUESTED", NOW.plusDays(3));
        showroom.setActive(false);
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.update(77L, new TestRideUpdateRequest("CONFIRMED", null))));
        assertEquals(List.of("CANCELLED"), service.update(77L, new TestRideUpdateRequest(null, "note")).nextSteps());
        showroom.setActive(true);
        bike.setActive(false);
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.update(77L, new TestRideUpdateRequest("CONFIRMED", null))));
        assertEquals(TestRideBooking.REQUESTED, booking.getStatus());
        assertTrue(events.isEmpty(), "nobody is told about a confirmation that did not happen");
        // it can still be cancelled, with a reason
        service.update(77L, new TestRideUpdateRequest("CANCELLED", "The bike is no longer sold"));
        assertEquals(TestRideBooking.CANCELLED, booking.getStatus());
    }

    @Test
    void anOlderNoteOnTheBookingIsNotAReasonToCancel() {
        TestRideBooking booking = bookingIn("CONFIRMED", NOW.plusDays(2));
        booking.setStaffNote("Please bring your ID");
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.update(77L, new TestRideUpdateRequest("CANCELLED", null))));
        assertEquals(TestRideBooking.CONFIRMED, booking.getStatus());
        service.update(77L, new TestRideUpdateRequest("CANCELLED", "The bike is in service"));
        assertEquals(TestRideBooking.CANCELLED, booking.getStatus());
        assertEquals("The bike is in service", booking.getStaffNote());
    }

    @Test
    void aRideIsDoneFromHalfAnHourBeforeItsStartAndSaysNothing() {
        TestRideBooking booking = bookingIn("CONFIRMED", NOW.plusHours(3));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.update(77L, new TestRideUpdateRequest("DONE", null))));
        booking.setSlotStart(NOW.plusMinutes(20));
        service.update(77L, new TestRideUpdateRequest("DONE", null));
        assertEquals(TestRideBooking.DONE, booking.getStatus());
        assertTrue(events.isEmpty());
    }

    @Test
    void aCustomerIsAbsentOnlyAfterTheStartTimeAndIsToldSo() {
        TestRideBooking booking = bookingIn("CONFIRMED", NOW.plusMinutes(5));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.update(77L, new TestRideUpdateRequest("NO_SHOW", null))));
        booking.setSlotStart(NOW.minusHours(1));
        service.update(77L, new TestRideUpdateRequest("NO_SHOW", null));
        assertEquals(TestRideBooking.NO_SHOW, booking.getStatus());
        assertEquals(TestRideMessages.NO_SHOW, ((TestRideNoticeEvent) events.get(0)).kind());
    }

    @Test
    void aRequestIsNeverANoShowAndIsOnlyDoneFromHalfAnHourBeforeItsStart() {
        TestRideBooking booking = bookingIn("REQUESTED", NOW.plusMinutes(10));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.update(77L, new TestRideUpdateRequest("NO_SHOW", null))));
        assertEquals(List.of("CONFIRMED", "DONE", "CANCELLED"), service.update(77L, new TestRideUpdateRequest(null, "note")).nextSteps());
        booking.setSlotStart(NOW.minusMinutes(5));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.update(77L, new TestRideUpdateRequest("NO_SHOW", null))));
        assertEquals(List.of("DONE", "CANCELLED"), service.update(77L, new TestRideUpdateRequest(null, "note")).nextSteps());
        assertEquals(TestRideBooking.REQUESTED, booking.getStatus());
        assertTrue(events.isEmpty());
    }

    @Test
    void aRequestWhoseSlotBeganCannotBeConfirmed() {
        bookingIn("REQUESTED", NOW.minusMinutes(1));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.update(77L, new TestRideUpdateRequest("CONFIRMED", null))));
    }

    @Test
    void stepsOutOfOrderAndUnknownStatusesAreRefused() {
        bookingIn("REQUESTED", NOW.plusDays(2));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.update(77L, new TestRideUpdateRequest("DONE", null))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.update(77L, new TestRideUpdateRequest("SHIPPED", null))));
        bookingIn("DONE", NOW.minusDays(1));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.update(77L, new TestRideUpdateRequest("CONFIRMED", null))));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.update(77L, new TestRideUpdateRequest(null, "late note"))), "a closed booking keeps its note");
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> service.update(404L, new TestRideUpdateRequest("CONFIRMED", null))));
        assertTrue(events.isEmpty());
    }

    @Test
    void changingOnlyTheNoteKeepsTheStepAndTellsNobody() {
        TestRideBooking booking = bookingIn("CONFIRMED", NOW.plusDays(2));
        service.update(77L, new TestRideUpdateRequest(null, "  Bring the helmet  "));
        assertEquals("Bring the helmet", booking.getStaffNote());
        assertEquals(TestRideBooking.CONFIRMED, booking.getStatus());
        assertTrue(events.isEmpty());
        service.update(77L, new TestRideUpdateRequest("", ""));
        assertNull(booking.getStaffNote(), "an empty note clears it");
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.update(77L, new TestRideUpdateRequest(null, "x".repeat(1001)))));
    }

    @Test
    void theStaffResponseOffersOnlyTheStepsThatAreAllowedNow() {
        bookingIn("REQUESTED", NOW.plusDays(2));
        TestRideResponse response = service.update(77L, new TestRideUpdateRequest(null, "note"));
        assertEquals(List.of("CONFIRMED", "CANCELLED"), response.nextSteps());
        assertFalse(response.canCancel(), "canCancel is for the customer's own page");
        assertEquals("An Nguyen", response.customerName());
        assertEquals("an@example.com", response.customerEmail());
    }

    // ---- the schedule ----

    @SuppressWarnings("unchecked")
    @Test
    void theScheduleShowsOpenBookingsOfAnyDayByDefault() {
        TestRideBooking open = bookingIn("REQUESTED", SLOT);
        when(bookings.findSchedule(any(), any(), anyCollection(), anyLong(), any(Pageable.class))).thenReturn(List.of(open));
        List<TestRideResponse> rows = service.schedule(null, null, null);
        assertEquals(1, rows.size());
        assertEquals(List.of("CONFIRMED", "CANCELLED"), rows.get(0).nextSteps());
        ArgumentCaptor<Collection<String>> statuses = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(bookings).findSchedule(any(), any(), statuses.capture(), eq(0L), page.capture());
        assertEquals(Set.of("REQUESTED", "CONFIRMED"), Set.copyOf(statuses.getValue()));
        assertEquals(TestRideService.SCHEDULE_LIMIT, page.getValue().getPageSize());
        assertTrue(page.getValue().getSort().getOrderFor("slotStart").isAscending());
    }

    @SuppressWarnings("unchecked")
    @Test
    void aDateNarrowsToThatDayAndAllListsEveryStatus() {
        when(bookings.findSchedule(any(), any(), anyCollection(), anyLong(), any(Pageable.class))).thenReturn(List.of());
        service.schedule("2026-11-22", 10L, "all");
        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Collection<String>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(bookings).findSchedule(from.capture(), to.capture(), statuses.capture(), eq(10L), any(Pageable.class));
        assertEquals(LocalDateTime.of(2026, 11, 22, 0, 0), from.getValue());
        assertEquals(LocalDateTime.of(2026, 11, 23, 0, 0), to.getValue());
        assertEquals(TestRideRules.ALL, Set.copyOf(statuses.getValue()));
    }

    @SuppressWarnings("unchecked")
    @Test
    void aClosedStatusWithNoDateListsTheLatestFirst() {
        when(bookings.findSchedule(any(), any(), anyCollection(), anyLong(), any(Pageable.class))).thenReturn(List.of());
        service.schedule(null, null, "done");
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<Collection<String>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(bookings).findSchedule(any(), any(), statuses.capture(), anyLong(), page.capture());
        assertEquals(Set.of("DONE"), Set.copyOf(statuses.getValue()));
        assertTrue(page.getValue().getSort().getOrderFor("slotStart").isDescending());
    }

    @Test
    void aBadFilterIsRefused() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.schedule("2026-13-40", null, null)));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.schedule(null, null, "SHIPPED")));
    }

    @Test
    void theCustomersOwnListIsTheirBookingsWithTheCancelFlag() {
        TestRideBooking upcoming = bookingIn("CONFIRMED", SLOT);
        TestRideBooking past = bookingIn("DONE", NOW.minusDays(5));
        when(bookings.findByUserUsername(eq("an"), any(Pageable.class))).thenReturn(List.of(upcoming, past));
        List<TestRideResponse> mine = service.mine("an");
        assertEquals(2, mine.size());
        assertTrue(mine.get(0).canCancel());
        assertEquals(List.of(), mine.get(0).nextSteps());
        assertNotNull(mine.get(1).slotStart());
    }
}
