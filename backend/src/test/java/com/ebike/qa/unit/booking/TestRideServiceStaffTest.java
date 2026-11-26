package com.ebike.qa.unit.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.testride.TestRideBacklogResponse;
import com.ebike.dto.testride.TestRideResponse;
import com.ebike.dto.testride.TestRideSlotResponse;
import com.ebike.dto.testride.TestRideUpdateRequest;
import com.ebike.event.notification.TestRideNoticeEvent;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.order.Showroom;
import com.ebike.model.entity.product.Product;
import com.ebike.model.entity.product.VehicleType;
import com.ebike.model.entity.testride.TestRideBooking;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.ShowroomRepository;
import com.ebike.repository.product.ProductRepository;
import com.ebike.repository.testride.TestRideBookingRepository;
import com.ebike.service.admin.AuditLogService;
import com.ebike.service.testride.TestRideMessages;
import com.ebike.service.testride.TestRideRules;
import com.ebike.service.testride.TestRideService;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** TestRideService: slots, the customer's list, the staff schedule, the backlog counts and the staff's status changes. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestRideServiceStaffTest {

    static final String NOW = "2026-11-23T10:00"; // Monday

    @Mock TestRideBookingRepository bookings;
    @Mock ShowroomRepository showrooms;
    @Mock ProductRepository products;
    @Mock UserRepository users;
    @Mock AuditLogService audit;

    Fx.Events events;
    User user;
    Showroom showroom;
    Product bike;
    TestRideService service;

    @BeforeEach
    void setUp() {
        events = new Fx.Events();
        user = Fx.user(7L, "an");
        showroom = Fx.showroom(1L, true);
        bike = Fx.product(5L, VehicleType.E_BIKE, true);
        service = Fx.rideService(bookings, showrooms, products, users, audit, 14, 2, 120, 3, 24, Fx.clockAt(NOW));
        when(bookings.save(any(TestRideBooking.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        events.close();
    }

    ResponseStatusException refused(Runnable call) {
        Throwable t = catchThrowable(call::run);
        assertThat(t).isInstanceOf(ResponseStatusException.class);
        return (ResponseStatusException) t;
    }

    TestRideBooking stored(String status, String slot) {
        TestRideBooking b = Fx.booking(40L, user, showroom, bike, LocalDateTime.parse(slot), status);
        when(bookings.findLockedById(40L)).thenReturn(Optional.of(b));
        return b;
    }

    // ------------------------------------------------------------------ slots

    @Test
    void slotsListsThirteenSlotsWithCapacityAndRemainingPlaces() {
        when(showrooms.findById(1L)).thenReturn(Optional.of(showroom));
        when(bookings.countBySlot(eq(1L), any(), any(), any())).thenReturn(List.<Object[]>of(
            new Object[] {LocalDateTime.parse("2026-11-24T09:00"), 1L},
            new Object[] {Timestamp.valueOf("2026-11-24 10:00:00"), 2L},
            new Object[] {LocalDateTime.parse("2026-11-24T11:00"), 5L}));
        List<TestRideSlotResponse> slots = service.slots(1L, "2026-11-24");

        assertThat(slots).hasSize(13);
        assertThat(slots.get(0).start()).isEqualTo(LocalDateTime.parse("2026-11-24T08:00"));
        assertThat(slots.get(0)).extracting(TestRideSlotResponse::capacity, TestRideSlotResponse::remaining, TestRideSlotResponse::bookable).containsExactly(2, 2, true);
        assertThat(slots.get(1)).extracting(TestRideSlotResponse::remaining, TestRideSlotResponse::bookable).containsExactly(1, true);
        assertThat(slots.get(2)).extracting(TestRideSlotResponse::remaining, TestRideSlotResponse::bookable).containsExactly(0, false);
        assertThat(slots.get(3).remaining()).isZero(); // over-booked by 5: never negative
        assertThat(slots.get(3).bookable()).isFalse();
    }

    @Test
    void slotsQueriesOnlyOpenBookingsOfThatShowroomAndDay() {
        when(showrooms.findById(1L)).thenReturn(Optional.of(showroom));
        service.slots(1L, "2026-11-24");
        verify(bookings).countBySlot(1L, LocalDateTime.parse("2026-11-24T00:00"), LocalDateTime.parse("2026-11-25T00:00"), TestRideRules.OPEN);
    }

    @Test
    void slotsOfTodayAreNotBookableWithinTheLeadTime() {
        when(showrooms.findById(1L)).thenReturn(Optional.of(showroom));
        List<TestRideSlotResponse> slots = service.slots(1L, "2026-11-23");
        // now 10:00, lead 2 h: 11:00 is too soon, 12:00 is fine
        assertThat(find(slots, "2026-11-23T08:00").bookable()).isFalse();
        assertThat(find(slots, "2026-11-23T11:00").bookable()).isFalse();
        assertThat(find(slots, "2026-11-23T12:00").bookable()).isTrue();
        assertThat(find(slots, "2026-11-23T11:00").remaining()).isEqualTo(2); // still shows the places
    }

    private static TestRideSlotResponse find(List<TestRideSlotResponse> slots, String start) {
        return slots.stream().filter(s -> s.start().equals(LocalDateTime.parse(start))).findFirst().orElseThrow();
    }

    @Test
    void slotsOfAClosedWeekdayAreEmpty() {
        showroom.setOpeningHours("CN nghỉ");
        when(showrooms.findById(1L)).thenReturn(Optional.of(showroom));
        assertThat(service.slots(1L, "2026-11-29")).isEmpty(); // Sunday
    }

    @Test
    void slotsRejectBadInput() {
        when(showrooms.findById(1L)).thenReturn(Optional.of(showroom));
        assertThat(refused(() -> service.slots(null, "2026-11-24")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused(() -> service.slots(1L, null)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused(() -> service.slots(1L, "  ")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused(() -> service.slots(1L, "24/11/2026")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused(() -> service.slots(1L, "2026-02-30")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused(() -> service.slots(1L, "2026-11-22")).getReason()).contains("đã qua");
    }

    @Test
    void slotsWindowIsToday_Plus14AndNoFurther() {
        when(showrooms.findById(1L)).thenReturn(Optional.of(showroom));
        assertThat(service.slots(1L, "2026-12-07")).isNotEmpty();
        assertThat(refused(() -> service.slots(1L, "2026-12-08")).getReason()).contains("14 ngày");
    }

    @Test
    void slotsOfAnInactiveOrUnknownShowroomAre404() {
        showroom.setActive(false);
        when(showrooms.findById(1L)).thenReturn(Optional.of(showroom));
        assertThat(refused(() -> service.slots(1L, "2026-11-24")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refused(() -> service.slots(2L, "2026-11-24")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void slotsInTheShopZoneUseTheLocalDayWhenTheInstantIsStillYesterdayInUtc() {
        // 20:00 UTC on the 22nd is 03:00 on the 23rd in Ho Chi Minh: the 22nd is already "past"
        TestRideService s = Fx.rideService(bookings, showrooms, products, users, audit, 14, 2, 120, 3, 24,
            java.time.Clock.fixed(java.time.Instant.parse("2026-11-22T20:00:00Z"), Fx.VN));
        when(showrooms.findById(1L)).thenReturn(Optional.of(showroom));
        assertThat(refused(() -> s.slots(1L, "2026-11-22")).getReason()).contains("đã qua");
        assertThat(s.slots(1L, "2026-11-23")).isNotEmpty();
    }

    @Test
    void customCapacityIsReportedInEverySlot() {
        TestRideService s = Fx.rideService(bookings, showrooms, products, users, audit, 14, 4, 120, 3, 24, Fx.clockAt(NOW));
        when(showrooms.findById(1L)).thenReturn(Optional.of(showroom));
        assertThat(s.slots(1L, "2026-11-24")).allMatch(slot -> slot.capacity() == 4 && slot.remaining() == 4);
    }

    // ------------------------------------------------------------------ mine

    @Test
    void mineAsksForTheHundredLatestSlotsAndNeverShowsStaffSteps() {
        TestRideBooking b = Fx.booking(1L, user, showroom, bike, LocalDateTime.parse("2026-11-25T10:00"), "CONFIRMED");
        when(bookings.findByUserUsername(eq("an"), any(Pageable.class))).thenReturn(List.of(b));
        List<TestRideResponse> mine = service.mine("an");
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).nextSteps()).isEmpty();
        assertThat(mine.get(0).canCancel()).isTrue();
        assertThat(mine.get(0).customerName()).isEqualTo("An Nguyen");

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(bookings).findByUserUsername(eq("an"), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(TestRideService.MINE_LIMIT).isEqualTo(100);
        assertThat(page.getValue().getSort().getOrderFor("slotStart").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void mineRequiresASignedInUser() {
        assertThat(refused(() -> service.mine(null)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(refused(() -> service.mine("")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aPastBookingCannotBeCancelledByTheCustomerAccordingToTheList() {
        TestRideBooking b = Fx.booking(1L, user, showroom, bike, LocalDateTime.parse("2026-11-23T09:00"), "CONFIRMED");
        when(bookings.findByUserUsername(eq("an"), any(Pageable.class))).thenReturn(List.of(b));
        assertThat(service.mine("an").get(0).canCancel()).isFalse();
    }

    @Test
    void aBikeNoLongerSoldHasNoSlugInTheCustomersList() {
        bike.setActive(false);
        TestRideBooking b = Fx.booking(1L, user, showroom, bike, LocalDateTime.parse("2026-11-25T10:00"), "REQUESTED");
        when(bookings.findByUserUsername(eq("an"), any(Pageable.class))).thenReturn(List.of(b));
        assertThat(service.mine("an").get(0).productSlug()).isNull();
    }

    @Test
    void anErasedAccountShowsAPlaceholderNameInsteadOfTheScrubbedOne() {
        user.setAccountStatus("DELETED");
        user.setFirstName("x");
        TestRideBooking b = Fx.booking(1L, user, showroom, bike, LocalDateTime.parse("2026-11-25T10:00"), "REQUESTED");
        when(bookings.findSchedule(any(), any(), anyCollection(), anyLong(), any(Pageable.class))).thenReturn(List.of(b));
        assertThat(service.schedule(null, null, null, null).get(0).customerName()).isEqualTo("Tài khoản đã xóa");
    }

    @Test
    void aCustomerWithNoNameShowsTheEmail() {
        user.setFirstName(null);
        user.setLastName(null);
        TestRideBooking b = Fx.booking(1L, user, showroom, bike, LocalDateTime.parse("2026-11-25T10:00"), "REQUESTED");
        when(bookings.findSchedule(any(), any(), anyCollection(), anyLong(), any(Pageable.class))).thenReturn(List.of(b));
        assertThat(service.schedule(null, null, null, null).get(0).customerName()).isEqualTo("an@example.com");
    }

    // ------------------------------------------------------------------ schedule

    @SuppressWarnings("unchecked")
    private Collection<String> statusesAsked() {
        ArgumentCaptor<Collection<String>> captor = (ArgumentCaptor<Collection<String>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Collection.class);
        verify(bookings).findSchedule(any(), any(), captor.capture(), anyLong(), any());
        return captor.getValue();
    }

    @Test
    void scheduleWithoutStatusListsOpenBookingsSoonestFirstAcrossAllTime() {
        service.schedule(null, null, null);
        assertThat(statusesAsked()).containsExactlyInAnyOrder("REQUESTED", "CONFIRMED");
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(bookings).findSchedule(any(), any(), anyCollection(), eq(0L), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(200);
        assertThat(page.getValue().getSort().getOrderFor("slotStart").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @SuppressWarnings("unchecked")
    void scheduleAllListsEveryStatusAndAClosedStatusLatestFirst() {
        service.schedule(null, null, "all");
        assertThat(statusesAsked()).containsExactlyInAnyOrderElementsOf(TestRideRules.ALL);

        TestRideService s2 = Fx.rideService(bookings, showrooms, products, users, audit, 14, 2, 120, 3, 24, Fx.clockAt(NOW));
        org.mockito.Mockito.clearInvocations(bookings);
        s2.schedule(null, null, " done ");
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<Collection<String>> st = (ArgumentCaptor<Collection<String>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Collection.class);
        verify(bookings).findSchedule(any(), any(), st.capture(), anyLong(), page.capture());
        assertThat(st.getValue()).containsExactly("DONE");
        assertThat(page.getValue().getSort().getOrderFor("slotStart").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void scheduleWithADateIsHalfOpenOverThatDayAndSoonestFirstEvenForAClosedStatus() {
        service.schedule("2026-11-24", 3L, "DONE", 50);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(bookings).findSchedule(eq(LocalDateTime.parse("2026-11-24T00:00")), eq(LocalDateTime.parse("2026-11-25T00:00")), anyCollection(), eq(3L), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(50);
        assertThat(page.getValue().getSort().getOrderFor("slotStart").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void scheduleLimitIsClampedBetweenOneAndOneThousand() {
        service.schedule(null, null, null, 5000);
        service.schedule(null, null, null, 0);
        service.schedule(null, null, null, -3);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(bookings, times(3)).findSchedule(any(), any(), anyCollection(), anyLong(), page.capture());
        assertThat(page.getAllValues()).extracting(Pageable::getPageSize).containsExactly(1000, 1, 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"garbage", "PENDING", "REQUESTED,DONE", "1"})
    void scheduleRejectsAnUnknownStatus(String status) {
        assertThat(refused(() -> service.schedule(null, null, status)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(bookings, never()).findSchedule(any(), any(), anyCollection(), anyLong(), any());
    }

    @Test
    void scheduleRejectsAnUnparsableDate() {
        assertThat(refused(() -> service.schedule("tomorrow", null, null)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void scheduleTreatsBlankStatusAndBlankDateAsAbsent() {
        service.schedule("  ", null, "  ");
        assertThat(statusesAsked()).containsExactlyInAnyOrder("REQUESTED", "CONFIRMED");
    }

    @Test
    void scheduleGivesStaffTheNextStepsThatTheClockAllows() {
        TestRideBooking b = Fx.booking(1L, user, showroom, bike, LocalDateTime.parse("2026-11-23T09:00"), "CONFIRMED"); // started an hour ago
        when(bookings.findSchedule(any(), any(), anyCollection(), anyLong(), any(Pageable.class))).thenReturn(List.of(b));
        TestRideResponse r = service.schedule(null, null, null).get(0);
        assertThat(r.nextSteps()).containsExactly("DONE", "NO_SHOW");
        assertThat(r.canCancel()).isFalse();
    }

    @Test
    void scheduleHidesConfirmWhenTheShowroomWasSwitchedOff() {
        showroom.setActive(false);
        TestRideBooking b = Fx.booking(1L, user, showroom, bike, LocalDateTime.parse("2026-11-25T10:00"), "REQUESTED");
        when(bookings.findSchedule(any(), any(), anyCollection(), anyLong(), any(Pageable.class))).thenReturn(List.of(b));
        assertThat(service.schedule(null, null, null).get(0).nextSteps()).containsExactly("CANCELLED");
    }

    @Test
    void cancelledByIsOnlyShownForCancelledBookings() {
        TestRideBooking open = Fx.booking(1L, user, showroom, bike, LocalDateTime.parse("2026-11-25T10:00"), "REQUESTED");
        open.setCancelSource("CUSTOMER"); // stale data
        TestRideBooking cancelled = Fx.booking(2L, user, showroom, bike, LocalDateTime.parse("2026-11-25T11:00"), "CANCELLED");
        cancelled.setCancelSource("STORE");
        when(bookings.findSchedule(any(), any(), anyCollection(), anyLong(), any(Pageable.class))).thenReturn(List.of(open, cancelled));
        List<TestRideResponse> list = service.schedule(null, null, "ALL");
        assertThat(list.get(0).cancelledBy()).isNull();
        assertThat(list.get(1).cancelledBy()).isEqualTo("STORE");
    }

    // ------------------------------------------------------------------ backlog

    @Test
    void backlogUsesNowAndTheRightWindows() {
        when(bookings.countRequestedAvailable(any())).thenReturn(4L);
        when(bookings.countByStatusAndSlotStartLessThanEqual(eq("CONFIRMED"), any())).thenReturn(2L);
        when(bookings.countCancelledByCustomerBetween(any(), any())).thenReturn(1L);
        when(bookings.countOpenUnavailable(anyCollection(), any())).thenReturn(3L);
        TestRideBacklogResponse r = service.backlog();
        assertThat(r).isEqualTo(new TestRideBacklogResponse(4, 2, 1, 3));
        LocalDateTime now = LocalDateTime.parse(NOW);
        verify(bookings).countRequestedAvailable(now);
        verify(bookings).countByStatusAndSlotStartLessThanEqual("CONFIRMED", now.minusHours(1));
        verify(bookings).countCancelledByCustomerBetween(now, now.plusHours(24));
        verify(bookings).countOpenUnavailable(TestRideRules.OPEN, now.minusHours(1));
    }

    // ------------------------------------------------------------------ update (staff)

    private TestRideResponse update(String status, String note) {
        return service.update(40L, new TestRideUpdateRequest(status, note));
    }

    @Test
    void unknownBookingIs404() {
        when(bookings.findLockedById(404L)).thenReturn(Optional.empty());
        assertThat(refused(() -> service.update(404L, new TestRideUpdateRequest("CONFIRMED", null))).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void confirmingARequestStampsConfirmationAndNotifiesTheCustomer() {
        TestRideBooking b = stored("REQUESTED", "2026-11-26T10:00"); // 3 days ahead: outside the 24 h reminder window
        TestRideResponse r = update("CONFIRMED", "Please arrive 10 minutes early");
        assertThat(b.getStatus()).isEqualTo("CONFIRMED");
        assertThat(b.getConfirmedAt()).isEqualTo(LocalDateTime.parse(NOW));
        assertThat(b.getReminderSentAt()).isNull();
        assertThat(b.getStaffNote()).isEqualTo("Please arrive 10 minutes early");
        assertThat(b.getUpdatedAt()).isEqualTo(LocalDateTime.parse(NOW));
        assertThat(r.status()).isEqualTo("CONFIRMED");
        assertThat(events.published).singleElement().isInstanceOfSatisfying(TestRideNoticeEvent.class, e -> {
            assertThat(e.kind()).isEqualTo(TestRideMessages.CONFIRMED);
            assertThat(e.userId()).isEqualTo(7L);
            assertThat(e.slotStart()).isEqualTo(LocalDateTime.parse("2026-11-26T10:00"));
            assertThat(e.note()).isEqualTo("Please arrive 10 minutes early");
        });
        verify(audit).record(any(), any());
    }

    @Test
    void confirmingARideInsideTheReminderWindowMarksTheReminderAsAlreadySent() {
        TestRideBooking b = stored("REQUESTED", "2026-11-24T09:00"); // 23 h ahead
        update("CONFIRMED", null);
        assertThat(b.getReminderSentAt()).isEqualTo(LocalDateTime.parse(NOW));
    }

    @Test
    void confirmingExactly24HoursAheadAlsoSkipsTheReminder() {
        TestRideBooking b = stored("REQUESTED", "2026-11-24T10:00");
        update("CONFIRMED", null);
        assertThat(b.getReminderSentAt()).isNotNull();
    }

    @Test
    void confirmingJustOver24HoursAheadKeepsTheReminderForTheJob() {
        TestRideBooking b = stored("REQUESTED", "2026-11-24T10:01");
        update("CONFIRMED", null);
        assertThat(b.getReminderSentAt()).isNull();
    }

    @Test
    void statusIsCaseInsensitiveAndTrimmed() {
        TestRideBooking b = stored("REQUESTED", "2026-11-26T10:00");
        update("  confirmed ", null);
        assertThat(b.getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void confirmingAfterTheStartIsRefused() {
        stored("REQUESTED", "2026-11-23T10:00");
        ResponseStatusException e = refused(() -> update("CONFIRMED", null));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getReason()).contains("không thể xác nhận");
    }

    @Test
    void confirmingWhenTheShowroomIsOffOrTheBikeNotSoldIsRefused() {
        TestRideBooking b = stored("REQUESTED", "2026-11-26T10:00");
        showroom.setActive(false);
        assertThat(refused(() -> update("CONFIRMED", null)).getReason()).contains("ngừng");
        showroom.setActive(true);
        bike.setActive(false);
        assertThat(refused(() -> update("CONFIRMED", null)).getReason()).contains("ngừng");
        assertThat(b.getStatus()).isEqualTo("REQUESTED");
    }

    @Test
    void cancellationNeedsAReasonInTheSameRequest() {
        TestRideBooking b = stored("CONFIRMED", "2026-11-26T10:00");
        b.setStaffNote("an older note");
        assertThat(refused(() -> update("CANCELLED", null)).getReason()).contains("lý do");
        assertThat(refused(() -> update("CANCELLED", "   ")).getReason()).contains("lý do");
        assertThat(b.getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void cancellationByStaffRecordsTheSourceAndNotifies() {
        TestRideBooking b = stored("CONFIRMED", "2026-11-26T10:00");
        update("CANCELLED", "Bike is in repair");
        assertThat(b.getStatus()).isEqualTo("CANCELLED");
        assertThat(b.getCancelSource()).isEqualTo(TestRideBooking.BY_STORE);
        assertThat(b.getCancelledByCustomerAt()).isNull();
        assertThat(events.published).singleElement().isInstanceOfSatisfying(TestRideNoticeEvent.class, e -> assertThat(e.kind()).isEqualTo(TestRideMessages.CANCELLED));
    }

    @Test
    void cancellingAfterTheHourIsOverIsRefused() {
        stored("CONFIRMED", "2026-11-23T09:00"); // hour over exactly now
        ResponseStatusException e = refused(() -> update("CANCELLED", "late"));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getReason()).contains("không thể hủy");
    }

    @Test
    void cancellingDuringTheHourIsAllowed() {
        stored("CONFIRMED", "2026-11-23T09:01");
        update("CANCELLED", "storm");
    }

    @Test
    void markingDoneBeforeTheEarlyWindowIsRefusedAndInsideItIsAllowedWithoutANotice() {
        TestRideBooking b = stored("CONFIRMED", "2026-11-23T10:31");
        assertThat(refused(() -> update("DONE", null)).getReason()).contains("Chưa đến giờ");
        b.setSlotStart(LocalDateTime.parse("2026-11-23T10:30"));
        update("DONE", null);
        assertThat(b.getStatus()).isEqualTo("DONE");
        assertThat(events.published).isEmpty(); // done tells nobody
    }

    @Test
    void noShowBeforeTheStartIsRefusedAndAtTheStartAllowedWithANotice() {
        TestRideBooking b = stored("CONFIRMED", "2026-11-23T10:01");
        assertThat(refused(() -> update("NO_SHOW", null)).getReason()).contains("khách không đến");
        b.setSlotStart(LocalDateTime.parse(NOW));
        update("NO_SHOW", null);
        assertThat(b.getStatus()).isEqualTo("NO_SHOW");
        assertThat(events.published).singleElement().isInstanceOfSatisfying(TestRideNoticeEvent.class, e -> assertThat(e.kind()).isEqualTo(TestRideMessages.NO_SHOW));
    }

    @Test
    void anUnconfirmedRequestCanNeverBeMarkedNoShow() {
        TestRideBooking b = stored("REQUESTED", "2026-11-22T10:00");
        assertThat(refused(() -> update("NO_SHOW", null)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(b.getStatus()).isEqualTo("REQUESTED");
    }

    @Test
    void aRequestOfACustomerWhoCameEarlyCanBeMarkedDone() {
        TestRideBooking b = stored("REQUESTED", "2026-11-23T10:15");
        update("DONE", null);
        assertThat(b.getStatus()).isEqualTo("DONE");
    }

    @Test
    void closedBookingsRefuseEveryFurtherStatusIncludingTheSameOne() {
        for (String closed : new String[] {"DONE", "CANCELLED", "NO_SHOW"}) {
            TestRideBooking b = stored(closed, "2026-11-22T10:00");
            for (String next : new String[] {"CONFIRMED", "DONE", "NO_SHOW", "CANCELLED", closed}) {
                assertThat(refused(() -> update(next, "reason")).getStatusCode()).as(closed + " -> " + next).isEqualTo(HttpStatus.BAD_REQUEST);
            }
            assertThat(b.getStatus()).isEqualTo(closed);
        }
    }

    @Test
    void confirmingAnAlreadyConfirmedBookingIsRefusedNotSilentlyRepeated() {
        stored("CONFIRMED", "2026-11-26T10:00");
        assertThat(refused(() -> update("CONFIRMED", null)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(events.published).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"BOGUS", "ALL", "pending"})
    void unknownTargetStatusIsRefused(String status) {
        stored("REQUESTED", "2026-11-26T10:00");
        assertThat(refused(() -> update(status, null)).getReason()).contains("Trạng thái không hợp lệ");
    }

    @Test
    void closedBookingNoteCannotBeEdited() {
        stored("DONE", "2026-11-22T10:00");
        ResponseStatusException e = refused(() -> update(null, "late note"));
        assertThat(e.getReason()).contains("đã đóng");
    }

    @Test
    void noteOnlyChangeOnAConfirmedRideTellsTheCustomer() {
        TestRideBooking b = stored("CONFIRMED", "2026-11-26T10:00");
        update(null, "  Come at 9:45  ");
        assertThat(b.getStaffNote()).isEqualTo("Come at 9:45");
        assertThat(b.getStatus()).isEqualTo("CONFIRMED");
        assertThat(events.published).singleElement().isInstanceOfSatisfying(TestRideNoticeEvent.class, e -> assertThat(e.kind()).isEqualTo(TestRideMessages.NOTE_UPDATED));
        verify(audit).record(any(), org.mockito.ArgumentMatchers.contains("cập nhật ghi chú"));
    }

    @Test
    void clearingTheNoteTellsNobody() {
        TestRideBooking b = stored("CONFIRMED", "2026-11-26T10:00");
        b.setStaffNote("old");
        update(null, "   ");
        assertThat(b.getStaffNote()).isNull();
        assertThat(events.published).isEmpty();
    }

    @Test
    void noteOnlyChangeOnARequestedBookingTellsNobody() {
        stored("REQUESTED", "2026-11-26T10:00");
        update(null, "we will call you");
        assertThat(events.published).isEmpty();
    }

    @Test
    void resendingTheSameNoteIsNotAChange() {
        TestRideBooking b = stored("CONFIRMED", "2026-11-26T10:00");
        b.setStaffNote("same");
        LocalDateTime before = b.getUpdatedAt();
        update(null, "same");
        assertThat(b.getUpdatedAt()).isEqualTo(before);
        assertThat(events.published).isEmpty();
        verify(audit, never()).record(any(), any());
    }

    @Test
    void emptyRequestChangesNothing() {
        TestRideBooking b = stored("CONFIRMED", "2026-11-26T10:00");
        LocalDateTime before = b.getUpdatedAt();
        service.update(40L, new TestRideUpdateRequest(null, null));
        service.update(40L, null);
        service.update(40L, new TestRideUpdateRequest("  ", null));
        assertThat(b.getUpdatedAt()).isEqualTo(before);
        assertThat(b.getStatus()).isEqualTo("CONFIRMED");
        verify(audit, never()).record(any(), any());
    }

    @Test
    void staffNoteLongerThan1000CharactersIsRefused() {
        stored("REQUESTED", "2026-11-26T10:00");
        assertThat(refused(() -> update(null, "x".repeat(1001))).getReason()).contains("1000");
        update(null, "x".repeat(1000));
    }

    @Test
    void auditRecordNamesTheTransition() {
        stored("REQUESTED", "2026-11-26T10:00");
        update("CONFIRMED", null);
        verify(audit).record(eq("xử lý lịch lái thử"), eq("#40: REQUESTED → CONFIRMED"));
    }

    @Test
    void noticeCarriesTheShowroomAddressAndBikeName() {
        stored("REQUESTED", "2026-11-26T10:00");
        update("CONFIRMED", null);
        assertThat(events.published).singleElement().isInstanceOfSatisfying(TestRideNoticeEvent.class, e -> {
            assertThat(e.productName()).isEqualTo("Bike 5");
            assertThat(e.showroomName()).isEqualTo("Showroom 1");
            assertThat(e.showroomAddress()).isEqualTo("1 Le Loi");
        });
    }

    @Test
    void customerCancelledConfirmedRideStillGetsNoStaffNotice() {
        // the customer's own cancellation tells nobody (README: no staff notification, only the backlog counter)
        TestRideBooking b = stored("CONFIRMED", "2026-11-26T10:00");
        service.cancelOwn("an", 40L);
        assertThat(events.published).isEmpty();
        assertThat(b.getCancelledByCustomerAt()).isNotNull();
    }

    @Test
    void reminderHoursAreClampedToAtLeastOne() {
        TestRideService s = Fx.rideService(bookings, showrooms, products, users, audit, 14, 2, 120, 3, 0, Fx.clockAt(NOW));
        TestRideBooking b = stored("REQUESTED", "2026-11-23T11:30"); // 1.5 h ahead
        s.update(40L, new TestRideUpdateRequest("CONFIRMED", null));
        assertThat(b.getReminderSentAt()).isNull(); // 1.5 h is beyond the clamped 1 h window
    }

    @Test
    void statusSetIsKnownToTheRules() {
        assertThat(Set.of("REQUESTED", "CONFIRMED", "DONE", "CANCELLED", "NO_SHOW")).isEqualTo(TestRideRules.ALL);
    }
}
