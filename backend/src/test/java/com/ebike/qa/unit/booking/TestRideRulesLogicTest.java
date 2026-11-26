package com.ebike.qa.unit.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebike.model.entity.product.VehicleType;
import com.ebike.model.entity.testride.TestRideBooking;
import com.ebike.service.testride.TestRideRules;
import com.ebike.service.testride.TestRideRules.Hours;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Slots, lead time, window, steps, phone numbers and reminders of the pure rules. */
class TestRideRulesLogicTest {

    private static final String R = TestRideBooking.REQUESTED;
    private static final String C = TestRideBooking.CONFIRMED;
    private static final String D = TestRideBooking.DONE;
    private static final String X = TestRideBooking.CANCELLED;
    private static final String N = TestRideBooking.NO_SHOW;
    private static final Hours STD = new Hours(LocalTime.of(8, 0), LocalTime.of(21, 0));
    private static final LocalDate MON = LocalDate.of(2026, 11, 23);

    private static LocalDateTime at(String iso) {
        return LocalDateTime.parse(iso);
    }

    // ------------------------------------------------------------------ slots

    @Test
    void mondayIs23Nov2026() {
        assertThat(MON.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void slotsOfAStandardDayAreThirteenOneHourSlotsFrom8To20() {
        List<LocalDateTime> slots = TestRideRules.slotsOn(MON, STD);
        assertThat(slots).hasSize(13);
        assertThat(slots.get(0)).isEqualTo(at("2026-11-23T08:00"));
        assertThat(slots.get(12)).isEqualTo(at("2026-11-23T20:00"));
    }

    @Test
    void thereIsNoSlotStartingAtClosingTime() {
        assertThat(TestRideRules.slotsOn(MON, STD)).doesNotContain(at("2026-11-23T21:00"));
        assertThat(TestRideRules.isOnGrid(at("2026-11-23T21:00"), STD)).isFalse();
        assertThat(TestRideRules.isOnGrid(at("2026-11-23T20:00"), STD)).isTrue();
    }

    @Test
    void aSlotMustEndByClosingTimeSoAHalfHourOffsetLosesTheLastOne() {
        Hours h = new Hours(LocalTime.of(8, 30), LocalTime.of(17, 0));
        List<LocalDateTime> slots = TestRideRules.slotsOn(MON, h);
        assertThat(slots).hasSize(8); // 8:30 .. 15:30 (the 16:30 slot would end at 17:30)
        assertThat(slots.get(7)).isEqualTo(at("2026-11-23T15:30"));
        assertThat(TestRideRules.isOnGrid(at("2026-11-23T16:00"), h)).isFalse();
        assertThat(TestRideRules.isOnGrid(at("2026-11-23T16:30"), h)).isFalse();
    }

    @Test
    void anOpeningWindowShorterThanOneHourHasNoSlot() {
        assertThat(TestRideRules.slotsOn(MON, new Hours(LocalTime.of(8, 0), LocalTime.of(8, 59)))).isEmpty();
        assertThat(TestRideRules.slotsOn(MON, new Hours(LocalTime.of(8, 0), LocalTime.of(9, 0)))).hasSize(1);
    }

    @Test
    void closingAtMidnightGivesTheLateNightSlotOf2300() {
        Hours h = new Hours(LocalTime.of(8, 0), TestRideRules.END_OF_DAY);
        List<LocalDateTime> slots = TestRideRules.slotsOn(MON, h);
        assertThat(slots).hasSize(16);
        assertThat(slots.get(15)).isEqualTo(at("2026-11-23T23:00"));
        assertThat(TestRideRules.isOnGrid(at("2026-11-23T23:00"), h)).isTrue();
        // the slot at 00:00 of the next day belongs to the next day's grid, not to this one
        assertThat(slots).allMatch(slot -> slot.toLocalDate().equals(MON));
    }

    @Test
    void midnightOpeningSlotIsAllowedWhenOpenFrom0000() {
        Hours h = new Hours(LocalTime.MIDNIGHT, LocalTime.of(3, 0));
        assertThat(TestRideRules.slotsOn(MON, h)).containsExactly(at("2026-11-23T00:00"), at("2026-11-23T01:00"), at("2026-11-23T02:00"));
    }

    @Test
    void aClosedWeekdayHasNoSlotsAndOtherDaysDo() {
        Hours h = new Hours(LocalTime.of(8, 0), LocalTime.of(21, 0), Set.of(DayOfWeek.SUNDAY));
        assertThat(TestRideRules.slotsOn(LocalDate.of(2026, 11, 22), h)).isEmpty();
        assertThat(TestRideRules.slotsOn(LocalDate.of(2026, 11, 23), h)).isNotEmpty();
        assertThat(TestRideRules.isOnGrid(at("2026-11-22T10:00"), h)).isFalse();
    }

    @Test
    void closedShowroomHasNoSlotAnyDay() {
        for (int i = 0; i < 7; i++) {
            assertThat(TestRideRules.slotsOn(MON.plusDays(i), Hours.CLOSED)).isEmpty();
        }
    }

    @Test
    void slotWithSecondsOrNanosIsNotOnTheGrid() {
        assertThat(TestRideRules.isOnGrid(at("2026-11-23T09:00:01"), STD)).isFalse();
        assertThat(TestRideRules.isOnGrid(at("2026-11-23T09:00").plusNanos(1), STD)).isFalse();
    }

    @Test
    void slotBeforeOpeningIsOffGrid() {
        assertThat(TestRideRules.isOnGrid(at("2026-11-23T07:00"), STD)).isFalse();
        assertThat(TestRideRules.isOnGrid(at("2026-11-23T08:00"), STD)).isTrue();
    }

    @Test
    void slotLengthIsOneHour() {
        assertThat(TestRideRules.SLOT).isEqualTo(Duration.ofHours(1));
    }

    // ------------------------------------------------------------------ lead time and window

    @Test
    void exactlyTwoHoursAheadIsFarEnough() {
        LocalDateTime now = at("2026-11-23T10:00");
        assertThat(TestRideRules.isFarEnough(at("2026-11-23T12:00"), now, 120)).isTrue();
    }

    @Test
    void oneMinuteInsideTwoHoursIsTooSoon() {
        LocalDateTime now = at("2026-11-23T10:00");
        assertThat(TestRideRules.isFarEnough(at("2026-11-23T11:59"), now, 120)).isFalse();
        assertThat(TestRideRules.isFarEnough(at("2026-11-23T11:59:59"), now, 120)).isFalse();
    }

    @Test
    void aSlotInThePastIsNeverFarEnough() {
        assertThat(TestRideRules.isFarEnough(at("2026-11-23T09:00"), at("2026-11-23T10:00"), 0)).isFalse();
        assertThat(TestRideRules.isFarEnough(at("2026-11-23T10:00"), at("2026-11-23T10:00"), 0)).isTrue();
    }

    @Test
    void leadTimeCrossesMidnight() {
        assertThat(TestRideRules.isFarEnough(at("2026-11-24T01:00"), at("2026-11-23T23:01"), 120)).isFalse();
        assertThat(TestRideRules.isFarEnough(at("2026-11-24T01:00"), at("2026-11-23T23:00"), 120)).isTrue();
    }

    @Test
    void the14thDayAfterTodayIsInsideTheWindowAndTheNextOneIsNot() {
        LocalDateTime now = at("2026-11-23T10:00");
        assertThat(TestRideRules.isWithinWindow(at("2026-12-07T08:00"), now, 14)).isTrue();
        assertThat(TestRideRules.isWithinWindow(at("2026-12-07T23:00"), now, 14)).isTrue();
        assertThat(TestRideRules.isWithinWindow(at("2026-12-08T00:00"), now, 14)).isFalse();
        assertThat(TestRideRules.isWithinWindow(at("2026-12-08T08:00"), now, 14)).isFalse();
    }

    @Test
    void windowIsCountedInCalendarDaysNotIn24HourBlocks() {
        // late in the evening the 14th calendar day is still bookable up to 23:00
        LocalDateTime now = at("2026-11-23T23:30");
        assertThat(TestRideRules.isWithinWindow(at("2026-12-07T23:00"), now, 14)).isTrue();
        assertThat(TestRideRules.isWithinWindow(at("2026-12-08T08:00"), now, 14)).isFalse();
    }

    @Test
    void windowIsValidAcrossLeapDayAndYearEnd() {
        assertThat(TestRideRules.isWithinWindow(at("2027-01-05T10:00"), at("2026-12-22T10:00"), 14)).isTrue();
        assertThat(TestRideRules.isWithinWindow(at("2027-01-06T10:00"), at("2026-12-22T10:00"), 14)).isFalse();
        assertThat(TestRideRules.isWithinWindow(at("2028-02-29T10:00"), at("2028-02-15T10:00"), 14)).isTrue();
        assertThat(TestRideRules.isWithinWindow(at("2028-03-01T10:00"), at("2028-02-15T10:00"), 14)).isFalse();
    }

    // ------------------------------------------------------------------ rideable vehicles

    @ParameterizedTest
    @EnumSource(value = VehicleType.class, names = {"E_BIKE", "E_SCOOTER", "E_MOTORBIKE"})
    void bikesAreRideable(VehicleType type) {
        assertThat(TestRideRules.isRideable(type)).isTrue();
        assertThat(TestRideRules.isRideableName(type.name())).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = VehicleType.class, names = {"BATTERY", "ACCESSORY"})
    void batteriesAndAccessoriesAreNotRideable(VehicleType type) {
        assertThat(TestRideRules.isRideable(type)).isFalse();
        assertThat(TestRideRules.isRideableName(type.name())).isFalse();
    }

    @Test
    void unknownVehicleTypeIsNotRideable() {
        assertThat(TestRideRules.isRideable(null)).isFalse();
        assertThat(TestRideRules.isRideableName(null)).isFalse();
        assertThat(TestRideRules.isRideableName("")).isFalse();
        assertThat(TestRideRules.isRideableName("UNICYCLE")).isFalse();
        assertThat(TestRideRules.isRideableName("e_bike")).isFalse(); // names are exact
        assertThat(TestRideRules.isRideableName("  E_BIKE  ")).isTrue(); // but trimmed
    }

    // ------------------------------------------------------------------ phone numbers

    @ParameterizedTest
    @ValueSource(strings = {"0901234567", "09012345678", "0281234567", "02812345678"})
    void validVietnamesePhones(String phone) {
        assertThat(TestRideRules.isValidPhone(phone)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "901234567", "090123456", "090123456789", "+84901234567", "0901 234 567", "abcdefghij", "09012345a7", "1901234567", "090-123-4567"})
    void invalidVietnamesePhones(String phone) {
        assertThat(TestRideRules.isValidPhone(phone)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "0901234567|0901234567",
        "090 123 4567|0901234567",
        "090.123.4567|0901234567",
        "090-123-4567|0901234567",
        "(090) 123 4567|0901234567",
        "+84901234567|0901234567",
        "+84 90 123 4567|0901234567",
        "84901234567|0901234567",
        "0084901234567|0901234567",
        "0084 90 123 4567|0901234567",
        "+840901234567|0901234567",
        "  0901234567  |0901234567"})
    void phonesAreNormalisedToTheLocalForm(String raw, String expected) {
        assertThat(TestRideRules.normalizePhone(raw)).isEqualTo(expected);
        assertThat(TestRideRules.isValidPhone(TestRideRules.normalizePhone(raw))).isTrue();
    }

    @Test
    void nullPhoneNormalisesToEmptyAndIsInvalid() {
        assertThat(TestRideRules.normalizePhone(null)).isEmpty();
        assertThat(TestRideRules.isValidPhone(TestRideRules.normalizePhone(null))).isFalse();
    }

    @Test
    void aNumberStartingWith84ButWrongLengthIsLeftAlone() {
        // 84 + 8 digits is not an international number; it must not be turned into 0xxxxxxxx
        assertThat(TestRideRules.normalizePhone("8412345678")).isEqualTo("8412345678");
        assertThat(TestRideRules.isValidPhone(TestRideRules.normalizePhone("8412345678"))).isFalse();
    }

    @Test
    void internationalNumberWithTooFewDigitsStaysInvalid() {
        assertThat(TestRideRules.isValidPhone(TestRideRules.normalizePhone("+8490123456"))).isFalse(); // 0 + 8 digits
    }

    // ------------------------------------------------------------------ statuses and steps

    @ParameterizedTest
    @ValueSource(strings = {"REQUESTED", "CONFIRMED", "DONE", "CANCELLED", "NO_SHOW"})
    void knownStatuses(String status) {
        assertThat(TestRideRules.isKnownStatus(status)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"requested", "ALL", "PENDING", "DONE ", "done"})
    void unknownStatuses(String status) {
        assertThat(TestRideRules.isKnownStatus(status)).isFalse();
    }

    @Test
    void openStatusesAreRequestedAndConfirmed() {
        assertThat(TestRideRules.OPEN).containsExactlyInAnyOrder(R, C);
        assertThat(TestRideRules.ALL).containsExactlyInAnyOrder(R, C, D, X, N);
    }

    @ParameterizedTest
    @CsvSource({
        "REQUESTED,CONFIRMED,true", "REQUESTED,CANCELLED,true", "REQUESTED,DONE,true", "REQUESTED,NO_SHOW,false",
        "REQUESTED,REQUESTED,false",
        "CONFIRMED,DONE,true", "CONFIRMED,NO_SHOW,true", "CONFIRMED,CANCELLED,true", "CONFIRMED,REQUESTED,false",
        "CONFIRMED,CONFIRMED,false",
        "DONE,CONFIRMED,false", "DONE,CANCELLED,false", "DONE,NO_SHOW,false", "DONE,REQUESTED,false", "DONE,DONE,false",
        "CANCELLED,CONFIRMED,false", "CANCELLED,REQUESTED,false", "CANCELLED,DONE,false", "CANCELLED,CANCELLED,false",
        "NO_SHOW,DONE,false", "NO_SHOW,CONFIRMED,false", "NO_SHOW,CANCELLED,false", "NO_SHOW,NO_SHOW,false"})
    void transitionTable(String from, String to, boolean allowed) {
        assertThat(TestRideRules.canMove(from, to)).isEqualTo(allowed);
    }

    @Test
    void nullStatusesCannotMove() {
        assertThat(TestRideRules.canMove(null, C)).isFalse();
        assertThat(TestRideRules.canMove(R, null)).isFalse();
        assertThat(TestRideRules.canMove(null, null)).isFalse();
        assertThat(TestRideRules.canMove("GARBAGE", D)).isFalse();
    }

    // ------------------------------------------------------------------ timing of steps

    private static final LocalDateTime SLOT = at("2026-11-23T10:00");

    @Test
    void confirmIsAllowedUpToTheStartButNotAtTheStart() {
        assertThat(TestRideRules.timingAllows(C, SLOT, SLOT.minusNanos(1))).isTrue();
        assertThat(TestRideRules.timingAllows(C, SLOT, SLOT)).isFalse();
        assertThat(TestRideRules.timingAllows(C, SLOT, SLOT.plusMinutes(5))).isFalse();
    }

    @Test
    void doneIsAllowedFrom30MinutesBeforeTheStart() {
        assertThat(TestRideRules.timingAllows(D, SLOT, SLOT.minusMinutes(31))).isFalse();
        assertThat(TestRideRules.timingAllows(D, SLOT, SLOT.minusMinutes(30))).isTrue();
        assertThat(TestRideRules.timingAllows(D, SLOT, SLOT.plusDays(3))).isTrue();
        assertThat(TestRideRules.EARLY_DONE).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void noShowIsAllowedOnlyFromTheStart() {
        assertThat(TestRideRules.timingAllows(N, SLOT, SLOT.minusNanos(1))).isFalse();
        assertThat(TestRideRules.timingAllows(N, SLOT, SLOT)).isTrue();
    }

    @Test
    void cancelByStaffIsAllowedUntilTheHourIsOver() {
        assertThat(TestRideRules.timingAllows(X, SLOT, SLOT.plusMinutes(59).plusSeconds(59))).isTrue();
        assertThat(TestRideRules.timingAllows(X, SLOT, SLOT.plusHours(1))).isFalse();
    }

    @Test
    void timingOfAnUnknownTargetImposesNothing() {
        assertThat(TestRideRules.timingAllows(R, SLOT, SLOT.plusYears(1))).isTrue();
    }

    @Test
    void nextStepsOfARequestFarBeforeTheSlotAreConfirmAndCancel() {
        assertThat(TestRideRules.nextSteps(R, SLOT, SLOT.minusDays(1))).containsExactly(C, X);
    }

    @Test
    void nextStepsOfARequestInTheEarlyDoneWindow() {
        assertThat(TestRideRules.nextSteps(R, SLOT, SLOT.minusMinutes(10))).containsExactly(C, D, X);
    }

    @Test
    void nextStepsOfARequestDuringItsHourAreDoneAndCancelAndNeverNoShow() {
        assertThat(TestRideRules.nextSteps(R, SLOT, SLOT.plusMinutes(10))).containsExactly(D, X);
    }

    @Test
    void nextStepsOfARequestAfterItsHourAreOnlyDone() {
        assertThat(TestRideRules.nextSteps(R, SLOT, SLOT.plusHours(1))).containsExactly(D);
    }

    @Test
    void nextStepsOfAConfirmedRideBeforeTheStartAreCancelOnlyUntilTheEarlyWindow() {
        assertThat(TestRideRules.nextSteps(C, SLOT, SLOT.minusHours(2))).containsExactly(X);
        assertThat(TestRideRules.nextSteps(C, SLOT, SLOT.minusMinutes(30))).containsExactly(D, X);
    }

    @Test
    void nextStepsOfAConfirmedRideAtAndAfterTheStart() {
        assertThat(TestRideRules.nextSteps(C, SLOT, SLOT)).containsExactly(D, N, X);
        assertThat(TestRideRules.nextSteps(C, SLOT, SLOT.plusHours(1))).containsExactly(D, N);
        assertThat(TestRideRules.nextSteps(C, SLOT, SLOT.plusDays(2))).containsExactly(D, N);
    }

    @Test
    void closedBookingsHaveNoNextSteps() {
        for (String status : List.of(D, X, N)) {
            assertThat(TestRideRules.nextSteps(status, SLOT, SLOT)).isEmpty();
        }
    }

    @Test
    void unavailableShowroomOrBikeRemovesConfirmOnly() {
        assertThat(TestRideRules.nextSteps(R, SLOT, SLOT.minusDays(1), false)).containsExactly(X);
        assertThat(TestRideRules.nextSteps(C, SLOT, SLOT.plusMinutes(1), false)).containsExactly(D, N, X);
    }

    // ------------------------------------------------------------------ customer cancel, expiry, reminder

    @Test
    void customerMayCancelAnOpenBookingOnlyBeforeItStarts() {
        assertThat(TestRideRules.canCustomerCancel(R, SLOT, SLOT.minusNanos(1))).isTrue();
        assertThat(TestRideRules.canCustomerCancel(C, SLOT, SLOT.minusMinutes(1))).isTrue();
        assertThat(TestRideRules.canCustomerCancel(R, SLOT, SLOT)).isFalse();
        assertThat(TestRideRules.canCustomerCancel(C, SLOT, SLOT.plusMinutes(1))).isFalse();
    }

    @Test
    void customerCannotCancelAClosedBooking() {
        for (String status : List.of(D, X, N, "GARBAGE")) {
            assertThat(TestRideRules.canCustomerCancel(status, SLOT, SLOT.minusDays(2))).isFalse();
        }
    }

    // Expected: a null status is "not open", so false (canMove, isKnownStatus and reminderDue all tolerate null).
    // Actual: TestRideRules.java:440 calls OPEN.contains(null) on an immutable Set.of(...) and throws NullPointerException.
    @Tag("bug")
    @Test
    void bug_canCustomerCancelWithNullStatusThrowsNpe() {
        assertThat(TestRideRules.canCustomerCancel(null, SLOT, SLOT.minusDays(2))).isFalse();
    }

    @Test
    void expiryCutoffIsOneHourBeforeNow() {
        assertThat(TestRideRules.expiryCutoff(at("2026-11-23T10:00"))).isEqualTo(at("2026-11-23T09:00"));
        assertThat(TestRideRules.expiryCutoff(at("2026-11-23T00:30"))).isEqualTo(at("2026-11-22T23:30"));
    }

    @Test
    void reminderIsDueForAConfirmedUnremindedRideInTheNext24Hours() {
        LocalDateTime now = at("2026-11-23T10:00");
        assertThat(TestRideRules.reminderDue(C, now.plusHours(24), null, now, 24)).isTrue();
        assertThat(TestRideRules.reminderDue(C, now.plusHours(24).plusNanos(1), null, now, 24)).isFalse();
        assertThat(TestRideRules.reminderDue(C, now.plusNanos(1), null, now, 24)).isTrue();
        assertThat(TestRideRules.reminderDue(C, now, null, now, 24)).isFalse();
        assertThat(TestRideRules.reminderDue(C, now.minusMinutes(1), null, now, 24)).isFalse();
    }

    @Test
    void reminderIsSentOnceAndOnlyForConfirmedRides() {
        LocalDateTime now = at("2026-11-23T10:00");
        LocalDateTime slot = now.plusHours(5);
        assertThat(TestRideRules.reminderDue(C, slot, now.minusHours(1), now, 24)).isFalse();
        for (String status : List.of(R, D, X, N)) {
            assertThat(TestRideRules.reminderDue(status, slot, null, now, 24)).isFalse();
        }
    }
}
