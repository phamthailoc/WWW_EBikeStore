package com.ebike.qa.unit.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.testride.TestRideCreateRequest;
import com.ebike.dto.testride.TestRideResponse;
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
import com.ebike.service.testride.TestRideRules;
import com.ebike.service.testride.TestRideService;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** TestRideService.book / cancelOwn: every rule of the README (lead time, 14 days, capacity, open limit, rate limit, ownership). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestRideServiceBookTest {

    // Monday 23 Nov 2026, 10:00 in the shop's zone
    static final String NOW = "2026-11-23T10:00";

    @Mock TestRideBookingRepository bookings;
    @Mock ShowroomRepository showrooms;
    @Mock ProductRepository products;
    @Mock UserRepository users;
    @Mock AuditLogService audit;

    User user;
    Showroom showroom;
    Product bike;
    TestRideService service;

    @BeforeEach
    void setUp() {
        user = Fx.user(7L, "an");
        showroom = Fx.showroom(1L, true);
        bike = Fx.product(5L, VehicleType.E_BIKE, true);
        service = newService(14, 2, 120, 3, 24, NOW);
        when(showrooms.findLockedById(1L)).thenReturn(Optional.of(showroom));
        when(users.findLockedByUsername("an")).thenReturn(Optional.of(user));
        when(products.findById(5L)).thenReturn(Optional.of(bike));
        when(bookings.saveAndFlush(any(TestRideBooking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookings.save(any(TestRideBooking.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    TestRideService newService(int days, int capacity, int lead, int maxOpen, int reminderHours, String now) {
        return Fx.rideService(bookings, showrooms, products, users, audit, days, capacity, lead, maxOpen, reminderHours, Fx.clockAt(now));
    }

    static TestRideCreateRequest req(String slot) {
        return new TestRideCreateRequest(5L, 1L, slot, "0901234567", null);
    }

    ResponseStatusException thrownBy(Runnable call) {
        Throwable t = org.assertj.core.api.Assertions.catchThrowable(call::run);
        assertThat(t).isInstanceOf(ResponseStatusException.class);
        return (ResponseStatusException) t;
    }

    void assertRefused(String slot, HttpStatus status, String messagePart) {
        org.mockito.Mockito.clearInvocations(bookings);
        ResponseStatusException e = thrownBy(() -> service.book("an", req(slot)));
        assertThat(e.getStatusCode()).isEqualTo(status);
        assertThat(e.getReason()).contains(messagePart);
        verify(bookings, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------ happy path

    @Test
    void bookingAValidSlotCreatesARequestedBookingStampedWithTheShopClock() {
        TestRideResponse response = service.book("an", req("2026-11-23T12:00"));

        ArgumentCaptor<TestRideBooking> saved = ArgumentCaptor.forClass(TestRideBooking.class);
        verify(bookings).saveAndFlush(saved.capture());
        TestRideBooking b = saved.getValue();
        assertThat(b.getStatus()).isEqualTo(TestRideBooking.REQUESTED);
        assertThat(b.getUser()).isSameAs(user);
        assertThat(b.getProduct()).isSameAs(bike);
        assertThat(b.getShowroom()).isSameAs(showroom);
        assertThat(b.getSlotStart()).isEqualTo(LocalDateTime.parse("2026-11-23T12:00"));
        assertThat(b.getContactPhone()).isEqualTo("0901234567");
        assertThat(b.getCreatedAt()).isEqualTo(LocalDateTime.parse(NOW));
        assertThat(b.getUpdatedAt()).isEqualTo(LocalDateTime.parse(NOW));
        assertThat(b.getCustomerNote()).isNull();
        assertThat(response.status()).isEqualTo("REQUESTED");
        assertThat(response.canCancel()).isTrue();
        assertThat(response.nextSteps()).isEmpty(); // a customer is never shown staff steps
    }

    @Test
    void phoneIsStoredInLocalFormWhenGivenInternationally() {
        service.book("an", new TestRideCreateRequest(5L, 1L, "2026-11-23T12:00", "+84 90 123 4567", null));
        ArgumentCaptor<TestRideBooking> saved = ArgumentCaptor.forClass(TestRideBooking.class);
        verify(bookings).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getContactPhone()).isEqualTo("0901234567");
    }

    @Test
    void noteIsTrimmedAndBlankNoteBecomesNull() {
        service.book("an", new TestRideCreateRequest(5L, 1L, "2026-11-23T12:00", "0901234567", "  bring helmet  "));
        service.book("an", new TestRideCreateRequest(5L, 1L, "2026-11-23T13:00", "0901234567", "   "));
        ArgumentCaptor<TestRideBooking> saved = ArgumentCaptor.forClass(TestRideBooking.class);
        verify(bookings, org.mockito.Mockito.times(2)).saveAndFlush(saved.capture());
        assertThat(saved.getAllValues().get(0).getCustomerNote()).isEqualTo("bring helmet");
        assertThat(saved.getAllValues().get(1).getCustomerNote()).isNull();
    }

    @Test
    void noteOf500CharactersIsAcceptedAndOf501IsRefused() {
        service.book("an", new TestRideCreateRequest(5L, 1L, "2026-11-23T12:00", "0901234567", "x".repeat(500)));
        verify(bookings).saveAndFlush(any());
        assertThatThrownBy(() -> service.book("an", new TestRideCreateRequest(5L, 1L, "2026-11-23T13:00", "0901234567", "x".repeat(501))))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void noteLengthIsMeasuredAfterTrimming() {
        service.book("an", new TestRideCreateRequest(5L, 1L, "2026-11-23T12:00", "0901234567", "  " + "x".repeat(500) + "  "));
        verify(bookings).saveAndFlush(any());
    }

    // ------------------------------------------------------------------ request validation

    @Test
    void nullRequestIsABadRequest() {
        assertThat(thrownBy(() -> service.book("an", null)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingFieldsAreABadRequest() {
        for (TestRideCreateRequest r : new TestRideCreateRequest[] {
            new TestRideCreateRequest(null, 1L, "2026-11-23T12:00", "0901234567", null),
            new TestRideCreateRequest(5L, null, "2026-11-23T12:00", "0901234567", null),
            new TestRideCreateRequest(5L, 1L, null, "0901234567", null),
            new TestRideCreateRequest(5L, 1L, "   ", "0901234567", null)}) {
            assertThat(thrownBy(() -> service.book("an", r)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        verify(bookings, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "12345", "abc", "123456789", "+1 202 555 0100"})
    void invalidPhoneIsABadRequest(String phone) {
        ResponseStatusException e = thrownBy(() -> service.book("an", new TestRideCreateRequest(5L, 1L, "2026-11-23T12:00", phone, null)));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getReason()).contains("Số điện thoại");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-23 12:00", "garbage", "23/11/2026 12:00", "2026-13-01T10:00", "2026-11-23", "12:00"})
    void unparsableSlotIsABadRequest(String slot) {
        assertRefused(slot, HttpStatus.BAD_REQUEST, "Khung giờ không hợp lệ");
    }

    @Test
    void slotWithSurroundingSpacesIsAccepted() {
        service.book("an", req("  2026-11-23T12:00  "));
        verify(bookings).saveAndFlush(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void blankUsernameIsUnauthorised(String username) {
        ResponseStatusException e = thrownBy(() -> service.book(username, req("2026-11-23T12:00")));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unknownUserIsUnauthorised() {
        when(users.findLockedByUsername("ghost")).thenReturn(Optional.empty());
        assertThat(thrownBy(() -> service.book("ghost", req("2026-11-23T12:00"))).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------ showroom and bike

    @Test
    void unknownShowroomIs404() {
        when(showrooms.findLockedById(99L)).thenReturn(Optional.empty());
        ResponseStatusException e = thrownBy(() -> service.book("an", new TestRideCreateRequest(5L, 99L, "2026-11-23T12:00", "0901234567", null)));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void inactiveShowroomIs404() {
        showroom.setActive(false);
        assertThat(thrownBy(() -> service.book("an", req("2026-11-23T12:00"))).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void showroomWithNullActiveFlagIsTreatedAsInactive() {
        showroom.setActive(null);
        assertThat(thrownBy(() -> service.book("an", req("2026-11-23T12:00"))).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unknownOrInactiveBikeIs404() {
        bike.setActive(false);
        assertThat(thrownBy(() -> service.book("an", req("2026-11-23T12:00"))).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        when(products.findById(5L)).thenReturn(Optional.empty());
        assertThat(thrownBy(() -> service.book("an", req("2026-11-23T12:00"))).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @ParameterizedTest
    @CsvSource({"BATTERY", "ACCESSORY"})
    void batteriesAndAccessoriesCannotBeTestRidden(VehicleType type) {
        when(products.findById(6L)).thenReturn(Optional.of(Fx.product(6L, type, true)));
        ResponseStatusException e = thrownBy(() -> service.book("an", new TestRideCreateRequest(6L, 1L, "2026-11-23T12:00", "0901234567", null)));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getReason()).contains("không phải xe");
    }

    @ParameterizedTest
    @CsvSource({"E_BIKE", "E_SCOOTER", "E_MOTORBIKE"})
    void allRideableTypesAreAccepted(VehicleType type) {
        when(products.findById(6L)).thenReturn(Optional.of(Fx.product(6L, type, true)));
        service.book("an", new TestRideCreateRequest(6L, 1L, "2026-11-23T12:00", "0901234567", null));
        verify(bookings).saveAndFlush(any());
    }

    @Test
    void productWithoutSpecificationIsRefusedNotNpe() {
        when(products.findById(6L)).thenReturn(Optional.of(Fx.product(6L, null, true)));
        ResponseStatusException e = thrownBy(() -> service.book("an", new TestRideCreateRequest(6L, 1L, "2026-11-23T12:00", "0901234567", null)));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------------------------ opening hours

    @Test
    void slotAtClosingTimeIsOutsideOpeningHours() {
        assertRefused("2026-11-23T21:00", HttpStatus.BAD_REQUEST, "ngoài giờ mở cửa");
    }

    @Test
    void lastSlotBeforeClosingIsAccepted() {
        service.book("an", req("2026-11-23T20:00"));
        verify(bookings).saveAndFlush(any());
    }

    @Test
    void slotBeforeOpeningAndOffTheHourAreRefused() {
        assertRefused("2026-11-24T07:00", HttpStatus.BAD_REQUEST, "ngoài giờ mở cửa");
        assertRefused("2026-11-23T12:30", HttpStatus.BAD_REQUEST, "ngoài giờ mở cửa");
        assertRefused("2026-11-23T12:00:30", HttpStatus.BAD_REQUEST, "ngoài giờ mở cửa");
    }

    @Test
    void slotOnAClosedWeekdayIsRefused() {
        showroom.setOpeningHours("CN đóng cửa");
        // Sunday 29 Nov 2026
        assertRefused("2026-11-29T10:00", HttpStatus.BAD_REQUEST, "ngoài giờ mở cửa");
        service.book("an", req("2026-11-28T10:00")); // Saturday is fine
        verify(bookings).saveAndFlush(any());
    }

    @Test
    void fullyClosedShowroomTakesNoBooking() {
        showroom.setOpeningHours("Tạm nghỉ đến 01/12");
        assertRefused("2026-11-24T10:00", HttpStatus.BAD_REQUEST, "ngoài giờ mở cửa");
    }

    @Test
    void timeColumnsOfTheShowroomDecideTheGrid() {
        showroom.setOpeningHours("08:00 - 21:00");
        showroom.setOpenTime(LocalTime.of(10, 0));
        showroom.setCloseTime(LocalTime.of(12, 0));
        assertRefused("2026-11-24T09:00", HttpStatus.BAD_REQUEST, "ngoài giờ mở cửa");
        assertRefused("2026-11-24T12:00", HttpStatus.BAD_REQUEST, "ngoài giờ mở cửa");
        service.book("an", req("2026-11-24T11:00"));
        verify(bookings).saveAndFlush(any());
    }

    @Test
    void showroomOpenUntilMidnightTakes2300() {
        showroom.setOpeningHours("08:00 - 24:00");
        service.book("an", req("2026-11-23T23:00"));
        verify(bookings).saveAndFlush(any());
    }

    // ------------------------------------------------------------------ lead time

    @Test
    void exactlyTwoHoursAheadIsAccepted() {
        service.book("an", req("2026-11-23T12:00"));
        verify(bookings).saveAndFlush(any());
    }

    @Test
    void oneHourAheadIsTooSoonAndTheMessageSaysTwoHours() {
        assertRefused("2026-11-23T11:00", HttpStatus.BAD_REQUEST, "ít nhất 2 giờ");
    }

    @Test
    void oneMinuteInsideTheLeadTimeIsRefused() {
        service = newService(14, 2, 120, 3, 24, "2026-11-23T10:01");
        assertRefused("2026-11-23T12:00", HttpStatus.BAD_REQUEST, "ít nhất 2 giờ");
    }

    @Test
    void aSlotInThePastIsRefusedAsPast() {
        assertRefused("2026-11-23T09:00", HttpStatus.BAD_REQUEST, "đã qua");
        assertRefused("2026-11-22T10:00", HttpStatus.BAD_REQUEST, "đã qua");
    }

    @Test
    void theCurrentSlotIsTooSoon() {
        assertRefused("2026-11-23T10:00", HttpStatus.BAD_REQUEST, "ít nhất");
    }

    @Test
    void leadTimeOf90MinutesIsWordedInMinutesAndOf60InOneHour() {
        service = newService(14, 2, 90, 3, 24, NOW);
        assertRefused("2026-11-23T11:00", HttpStatus.BAD_REQUEST, "90 phút");
        service = newService(14, 2, 60, 3, 24, "2026-11-23T10:30");
        assertRefused("2026-11-23T11:00", HttpStatus.BAD_REQUEST, "1 giờ");
    }

    @Test
    void negativeLeadTimeIsClampedToZero() {
        service = newService(14, 2, -500, 3, 24, NOW);
        service.book("an", req("2026-11-23T11:00"));
        verify(bookings).saveAndFlush(any());
        assertRefused("2026-11-23T09:00", HttpStatus.BAD_REQUEST, "đã qua");
    }

    @Test
    void leadTimeIsComputedInTheShopZoneEvenWhenTheInstantIsStillYesterdayInUtc() {
        // 00:30 UTC on the 23rd is 07:30 in Ho Chi Minh: an 08:00 slot is only 30 minutes away
        TestRideService utcServer = Fx.rideService(bookings, showrooms, products, users, audit, 14, 2, 120, 3, 24,
            java.time.Clock.fixed(java.time.Instant.parse("2026-11-23T00:30:00Z"), Fx.VN));
        ResponseStatusException e = thrownBy(() -> utcServer.book("an", req("2026-11-23T08:00")));
        assertThat(e.getReason()).contains("ít nhất");
        // while 10:00 local (3 h away) is fine
        utcServer.book("an", req("2026-11-23T10:00"));
        verify(bookings).saveAndFlush(any());
    }

    @Test
    void justAfterLocalMidnightTheShopAlreadyLivesOnTheNextDay() {
        // 17:30 UTC on the 22nd = 00:30 on the 23rd in Ho Chi Minh
        TestRideService s = Fx.rideService(bookings, showrooms, products, users, audit, 14, 2, 120, 3, 24,
            java.time.Clock.fixed(java.time.Instant.parse("2026-11-22T17:30:00Z"), Fx.VN));
        ResponseStatusException e = thrownBy(() -> s.book("an", req("2026-11-22T20:00")));
        assertThat(e.getReason()).contains("đã qua");
        s.book("an", req("2026-11-23T08:00"));
        verify(bookings).saveAndFlush(any());
    }

    // ------------------------------------------------------------------ 14-day window

    @Test
    void the14thDayAfterTodayIsBookableAndTheNextIsNot() {
        service.book("an", req("2026-12-07T10:00")); // 23 Nov + 14
        verify(bookings).saveAndFlush(any());
        assertRefused("2026-12-08T10:00", HttpStatus.BAD_REQUEST, "14 ngày");
    }

    @Test
    void lateEveningOfTheLastDayIsStillInsideTheWindow() {
        service.book("an", req("2026-12-07T20:00"));
        verify(bookings).saveAndFlush(any());
    }

    @Test
    void windowFollowsTheConfiguredNumberOfDays() {
        service = newService(7, 2, 120, 3, 24, NOW);
        service.book("an", req("2026-11-30T10:00"));
        assertRefused("2026-12-01T10:00", HttpStatus.BAD_REQUEST, "7 ngày");
    }

    @Test
    void zeroDaysAheadIsClampedToOneDay() {
        service = newService(0, 2, 120, 3, 24, NOW);
        service.book("an", req("2026-11-24T10:00"));
        assertRefused("2026-11-25T10:00", HttpStatus.BAD_REQUEST, "1 ngày");
    }

    // ------------------------------------------------------------------ capacity, open limit, rate limit, overlap

    @Test
    void secondPlaceInTheSlotIsStillFreeButTheThirdIsNot() {
        when(bookings.countAt(1L, LocalDateTime.parse("2026-11-23T12:00"), TestRideRules.OPEN)).thenReturn(1L);
        service.book("an", req("2026-11-23T12:00"));
        verify(bookings).saveAndFlush(any());

        when(bookings.countAt(1L, LocalDateTime.parse("2026-11-23T13:00"), TestRideRules.OPEN)).thenReturn(2L);
        assertRefused("2026-11-23T13:00", HttpStatus.CONFLICT, "đã đủ chỗ");
    }

    @Test
    void capacityCountsOnlyOpenBookingsOfThatShowroomAndSlot() {
        service.book("an", req("2026-11-23T14:00"));
        verify(bookings).countAt(eq(1L), eq(LocalDateTime.parse("2026-11-23T14:00")), eq(TestRideRules.OPEN));
    }

    @Test
    void capacityOfZeroIsClampedToOne() {
        service = newService(14, 0, 120, 3, 24, NOW);
        when(bookings.countAt(anyLong(), any(), anyCollection())).thenReturn(1L);
        assertRefused("2026-11-23T12:00", HttpStatus.CONFLICT, "đã đủ chỗ");
        when(bookings.countAt(anyLong(), any(), anyCollection())).thenReturn(0L);
        service.book("an", req("2026-11-23T12:00"));
    }

    @Test
    void customerWithThreeOpenFutureBookingsCannotBookAFourth() {
        when(bookings.countByUserIdAndStatusInAndSlotStartGreaterThanEqual(eq(7L), eq(TestRideRules.OPEN), eq(LocalDateTime.parse(NOW)))).thenReturn(3L);
        ResponseStatusException e = thrownBy(() -> service.book("an", req("2026-11-24T12:00")));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getReason()).contains("3 lịch");
    }

    @Test
    void customerWithTwoOpenBookingsMayBookTheThird() {
        when(bookings.countByUserIdAndStatusInAndSlotStartGreaterThanEqual(eq(7L), eq(TestRideRules.OPEN), any())).thenReturn(2L);
        service.book("an", req("2026-11-24T12:00"));
        verify(bookings).saveAndFlush(any());
    }

    @Test
    void openLimitFollowsTheSetting() {
        service = newService(14, 2, 120, 1, 24, NOW);
        when(bookings.countByUserIdAndStatusInAndSlotStartGreaterThanEqual(eq(7L), any(), any())).thenReturn(1L);
        assertRefused("2026-11-24T12:00", HttpStatus.CONFLICT, "1 lịch");
    }

    @Test
    void maxOpenOfZeroIsClampedToOne() {
        service = newService(14, 2, 120, 0, 24, NOW);
        when(bookings.countByUserIdAndStatusInAndSlotStartGreaterThanEqual(eq(7L), any(), any())).thenReturn(1L);
        assertRefused("2026-11-24T12:00", HttpStatus.CONFLICT, "1 lịch");
    }

    @Test
    void tenBookingsInTheLastTenMinutesBlockTheEleventhWith429() {
        when(bookings.countByUserIdAndCreatedAtGreaterThanEqual(eq(7L), eq(LocalDateTime.parse("2026-11-23T09:50")))).thenReturn(10L);
        ResponseStatusException e = thrownBy(() -> service.book("an", req("2026-11-24T12:00")));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void nineBookingsInTheLastTenMinutesStillAllowTheTenth() {
        when(bookings.countByUserIdAndCreatedAtGreaterThanEqual(eq(7L), any())).thenReturn(9L);
        service.book("an", req("2026-11-24T12:00"));
        verify(bookings).saveAndFlush(any());
    }

    @Test
    void rateLimitWindowStartsExactlyTenMinutesBack() {
        service.book("an", req("2026-11-24T12:00"));
        verify(bookings).countByUserIdAndCreatedAtGreaterThanEqual(7L, LocalDateTime.parse("2026-11-23T09:50"));
    }

    @Test
    void overlapCheckLooksStrictlyBetweenOneHourBeforeAndOneHourAfter() {
        service.book("an", req("2026-11-24T12:00"));
        verify(bookings).existsByUserIdAndStatusInAndSlotStartGreaterThanAndSlotStartLessThan(
            7L, TestRideRules.OPEN, LocalDateTime.parse("2026-11-24T11:00"), LocalDateTime.parse("2026-11-24T13:00"));
    }

    @Test
    void anOpenBookingOverlappingTheHourIsAConflict() {
        when(bookings.existsByUserIdAndStatusInAndSlotStartGreaterThanAndSlotStartLessThan(eq(7L), any(), any(), any())).thenReturn(true);
        ResponseStatusException e = thrownBy(() -> service.book("an", req("2026-11-24T12:00")));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getReason()).contains("đã có một lịch");
    }

    @Test
    void concurrentDuplicateCaughtByTheUniqueKeyIsAConflictNotA500() {
        when(bookings.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("boom",
            new RuntimeException("Duplicate entry for key 'uk_test_ride_active_user_slot'")));
        ResponseStatusException e = thrownBy(() -> service.book("an", req("2026-11-24T12:00")));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void anyOtherConstraintViolationIsRethrownUnchanged() {
        DataIntegrityViolationException other = new DataIntegrityViolationException("Data too long for column");
        when(bookings.saveAndFlush(any())).thenThrow(other);
        assertThatThrownBy(() -> service.book("an", req("2026-11-24T12:00"))).isSameAs(other);
    }

    @Test
    void showroomRowIsLockedBeforeTheUserRow() {
        service.book("an", req("2026-11-24T12:00"));
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(showrooms, users, products);
        order.verify(showrooms).findLockedById(1L);
        order.verify(users).findLockedByUsername("an");
        order.verify(products).findById(5L);
    }

    // ------------------------------------------------------------------ cancelOwn

    private TestRideBooking owned(String status, String slot) {
        TestRideBooking b = Fx.booking(40L, user, showroom, bike, LocalDateTime.parse(slot), status);
        when(bookings.findLockedById(40L)).thenReturn(Optional.of(b));
        return b;
    }

    @Test
    void customerCancelsOwnRequestedBookingWithoutTheStaffBacklogStamp() {
        TestRideBooking b = owned("REQUESTED", "2026-11-23T12:00");
        TestRideResponse r = service.cancelOwn("an", 40L);
        assertThat(b.getStatus()).isEqualTo("CANCELLED");
        assertThat(b.getCancelSource()).isEqualTo(TestRideBooking.BY_CUSTOMER);
        assertThat(b.getCancelledByCustomerAt()).isNull();
        assertThat(b.getUpdatedAt()).isEqualTo(LocalDateTime.parse(NOW));
        assertThat(r.cancelledBy()).isEqualTo("CUSTOMER");
        assertThat(r.canCancel()).isFalse();
    }

    @Test
    void cancellingAConfirmedBookingStampsTheBacklogTimestamp() {
        TestRideBooking b = owned("CONFIRMED", "2026-11-23T12:00");
        service.cancelOwn("an", 40L);
        assertThat(b.getCancelledByCustomerAt()).isEqualTo(LocalDateTime.parse(NOW));
        assertThat(b.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancellingAtTheVeryStartIsTooLate() {
        owned("CONFIRMED", NOW);
        ResponseStatusException e = thrownBy(() -> service.cancelOwn("an", 40L));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cancellingOneNanosecondBeforeTheStartWorks() {
        owned("CONFIRMED", "2026-11-23T10:00:00.000000001");
        service.cancelOwn("an", 40L);
    }

    @Test
    void aClosedBookingCannotBeCancelledAgain() {
        for (String status : new String[] {"CANCELLED", "DONE", "NO_SHOW"}) {
            owned(status, "2026-11-24T12:00");
            assertThat(thrownBy(() -> service.cancelOwn("an", 40L)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Test
    void cancellingTwiceIsRefusedTheSecondTime() {
        owned("REQUESTED", "2026-11-24T12:00");
        service.cancelOwn("an", 40L);
        assertThat(thrownBy(() -> service.cancelOwn("an", 40L)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void anotherCustomersBookingLooksLikeItDoesNotExist() {
        owned("REQUESTED", "2026-11-24T12:00");
        ResponseStatusException e = thrownBy(() -> service.cancelOwn("mallory", 40L));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unknownBookingIs404AndBlankUsernameIs401() {
        when(bookings.findLockedById(404L)).thenReturn(Optional.empty());
        assertThat(thrownBy(() -> service.cancelOwn("an", 404L)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(thrownBy(() -> service.cancelOwn(" ", 40L)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(thrownBy(() -> service.cancelOwn(null, 40L)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void bookingWithoutAnOwnerIsNeverCancellableByAUsername() {
        TestRideBooking b = owned("REQUESTED", "2026-11-24T12:00");
        b.setUser(null);
        assertThat(thrownBy(() -> service.cancelOwn("an", 40L)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------------ constructor safety

    @Test
    void publicConstructorAcceptsAZoneNameAndRejectsGarbage() {
        new TestRideService(bookings, showrooms, products, users, audit, 14, 2, 120, 3, 24, "Asia/Ho_Chi_Minh");
        new TestRideService(bookings, showrooms, products, users, audit, 14, 2, 120, 3, 24, "UTC");
        assertThatThrownBy(() -> new TestRideService(bookings, showrooms, products, users, audit, 14, 2, 120, 3, 24, "Mars/Olympus"))
            .isInstanceOf(java.time.DateTimeException.class);
    }
}
