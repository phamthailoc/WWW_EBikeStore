package com.ebike.service.testride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.model.entity.product.VehicleType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** V23: opening hours, slots and the steps of a test-ride booking. */
class TestRideRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 0);

    @Test
    void readsTheOpeningHoursOfTheSeedShowrooms() {
        TestRideRules.Hours hours = TestRideRules.parseOpeningHours("08:00 - 21:00");
        assertEquals(LocalTime.of(8, 0), hours.open());
        assertEquals(LocalTime.of(21, 0), hours.close());
    }

    @Test
    void readsOtherWaysToWriteTheHours() {
        assertEquals(new TestRideRules.Hours(LocalTime.of(8, 0), LocalTime.of(21, 0)), TestRideRules.parseOpeningHours("8h - 21h"));
        assertEquals(new TestRideRules.Hours(LocalTime.of(8, 30), LocalTime.of(17, 0)), TestRideRules.parseOpeningHours("08:30-17:00"));
        assertEquals(new TestRideRules.Hours(LocalTime.of(9, 0), LocalTime.of(18, 30)), TestRideRules.parseOpeningHours("Thứ 2 - Thứ 7: 9h00 – 18h30"));
    }

    @Test
    void theVehicleTypeNameFollowsTheSameRuleAsTheEnum() {
        for (com.ebike.model.entity.product.VehicleType type : com.ebike.model.entity.product.VehicleType.values()) {
            assertEquals(TestRideRules.isRideable(type), TestRideRules.isRideableName(type.name()), type.name());
        }
        assertFalse(TestRideRules.isRideableName(null));
        assertFalse(TestRideRules.isRideableName("HOVERBOARD"));
    }

    @Test
    void readsTwelveHourTimes() {
        assertEquals(new TestRideRules.Hours(LocalTime.of(8, 0), LocalTime.of(21, 0)), TestRideRules.parseOpeningHours("8:00 AM - 9:00 PM"));
        assertEquals(new TestRideRules.Hours(LocalTime.of(8, 0), LocalTime.of(21, 0)), TestRideRules.parseOpeningHours("8:00am - 9:00pm"));
        assertEquals(new TestRideRules.Hours(LocalTime.of(8, 30), LocalTime.of(17, 0)), TestRideRules.parseOpeningHours("Mon-Sat: 8:30 a.m. - 5 p.m."));
        assertEquals(new TestRideRules.Hours(LocalTime.of(9, 0), LocalTime.of(18, 0)), TestRideRules.parseOpeningHours("9 AM - 6 PM"));
        assertEquals(new TestRideRules.Hours(LocalTime.of(0, 0), LocalTime.of(12, 0)), TestRideRules.parseOpeningHours("12:00 AM - 12:00 PM"));
        // a word that merely starts with "am" or "pm" is not a meridiem
        assertEquals(new TestRideRules.Hours(LocalTime.of(8, 0), LocalTime.of(12, 0)), TestRideRules.parseOpeningHours("8:00 - 12:00 amazing deals"));
        // "13:00 PM" cannot be a 12-hour time: the default hours, not a wrong range
        assertEquals(new TestRideRules.Hours(TestRideRules.DEFAULT_OPEN, TestRideRules.DEFAULT_CLOSE), TestRideRules.parseOpeningHours("8:00 AM - 13:00 PM"));
    }

    @Test
    void aClosingTimeOf24RunsToTheEndOfTheDay() {
        TestRideRules.Hours hours = TestRideRules.parseOpeningHours("08:00 - 24:00");
        assertEquals(new TestRideRules.Hours(LocalTime.of(8, 0), TestRideRules.END_OF_DAY), hours);
        List<LocalDateTime> slots = TestRideRules.slotsOn(LocalDate.of(2026, 11, 23), hours);
        assertEquals(16, slots.size());
        assertEquals(LocalDateTime.of(2026, 11, 23, 23, 0), slots.get(slots.size() - 1), "the 23:00 slot ends at midnight");
        assertTrue(TestRideRules.isOnGrid(LocalDateTime.of(2026, 11, 23, 23, 0), hours));
    }

    @Test
    void aWeekdayOffTakesNoBookingsThatDay() {
        LocalDate sunday = LocalDate.of(2026, 11, 22);
        LocalDate saturday = LocalDate.of(2026, 11, 21);
        TestRideRules.Hours hours = TestRideRules.parseOpeningHours("Thứ 2-7: 8h-17h, CN đóng cửa");
        assertEquals(LocalTime.of(8, 0), hours.open());
        assertEquals(LocalTime.of(17, 0), hours.close());
        assertEquals(java.util.Set.of(java.time.DayOfWeek.SUNDAY), hours.closedDays());
        assertTrue(TestRideRules.slotsOn(sunday, hours).isEmpty());
        assertEquals(9, TestRideRules.slotsOn(saturday, hours).size());
        assertFalse(TestRideRules.isOnGrid(sunday.atTime(9, 0), hours));
        assertEquals(java.util.Set.of(java.time.DayOfWeek.SUNDAY), TestRideRules.parseOpeningHours("08:00 - 21:00; Sunday: closed").closedDays());
        assertEquals(java.util.Set.of(java.time.DayOfWeek.SATURDAY), TestRideRules.parseOpeningHours("08:00 - 21:00 | T7 nghỉ").closedDays());
        // only a day off and no hours: the default hours, and the day off still counts (the showroom is not shut as a whole)
        TestRideRules.Hours dayOffOnly = TestRideRules.parseOpeningHours("CN đóng cửa");
        assertEquals(TestRideRules.DEFAULT_OPEN, dayOffOnly.open());
        assertEquals(java.util.Set.of(java.time.DayOfWeek.SUNDAY), dayOffOnly.closedDays());
        // a clause with hours in it is never a day off
        assertTrue(TestRideRules.parseOpeningHours("Thứ 2-7: 08:00 - 21:00").closedDays().isEmpty());
        assertTrue(TestRideRules.parseOpeningHours("CN: 9h - 12h").closedDays().isEmpty());
    }

    @Test
    void theDaysOfTheWeekAreNotTakenForTheHours() {
        TestRideRules.Hours expected = new TestRideRules.Hours(LocalTime.of(8, 0), LocalTime.of(21, 0));
        assertEquals(expected, TestRideRules.parseOpeningHours("Thứ 2-7: 08:00 - 21:00"));
        assertEquals(expected, TestRideRules.parseOpeningHours("T2-T7: 8h - 21h"));
        assertEquals(new TestRideRules.Hours(LocalTime.of(9, 0), LocalTime.of(17, 0)), TestRideRules.parseOpeningHours("9-17"));
        // days only, no clock time: nothing to read, so the default hours
        assertEquals(expected, TestRideRules.parseOpeningHours("Thứ 2-7"));
    }

    @Test
    void textThatIsNotARangeFallsBackToTheDefaultHours() {
        TestRideRules.Hours fallback = new TestRideRules.Hours(TestRideRules.DEFAULT_OPEN, TestRideRules.DEFAULT_CLOSE);
        assertEquals(fallback, TestRideRules.parseOpeningHours(null));
        assertEquals(fallback, TestRideRules.parseOpeningHours(""));
        assertEquals(fallback, TestRideRules.parseOpeningHours("by appointment"));
        // closing before opening, or an hour that does not exist, cannot be a day's opening hours
        assertEquals(fallback, TestRideRules.parseOpeningHours("21:00 - 08:00"));
        assertEquals(fallback, TestRideRules.parseOpeningHours("08:00 - 25:00"));
        assertEquals(fallback, TestRideRules.parseOpeningHours("08:00 - 08:00"));
    }

    @Test
    void aShowroomThatSaysItIsShutTakesNoBookings() {
        for (String text : List.of("Đóng cửa", "Tạm nghỉ", "Dong cua", "Closed", "Tạm ngưng hoạt động", "Đóng   cửa")) {
            assertEquals(TestRideRules.Hours.CLOSED, TestRideRules.parseOpeningHours(text), text);
        }
        LocalDate day = LocalDate.of(2026, 11, 22);
        assertTrue(TestRideRules.slotsOn(day, TestRideRules.Hours.CLOSED).isEmpty());
        // written hours win over a word of the same text
        assertEquals(new TestRideRules.Hours(LocalTime.of(8, 0), LocalTime.of(20, 0)), TestRideRules.parseOpeningHours("08:00 - 20:00 (closed on holidays)"));
    }

    @Test
    void slotsRunHourlyAndTheLastOneEndsByClosingTime() {
        LocalDate day = LocalDate.of(2026, 11, 22);
        List<LocalDateTime> slots = TestRideRules.slotsOn(day, new TestRideRules.Hours(LocalTime.of(8, 0), LocalTime.of(21, 0)));
        assertEquals(13, slots.size());
        assertEquals(day.atTime(8, 0), slots.get(0));
        assertEquals(day.atTime(20, 0), slots.get(12));
    }

    @Test
    void aHalfHourOpeningTimeShiftsTheWholeGridAndDropsAPartialLastSlot() {
        LocalDate day = LocalDate.of(2026, 11, 22);
        List<LocalDateTime> slots = TestRideRules.slotsOn(day, new TestRideRules.Hours(LocalTime.of(8, 30), LocalTime.of(11, 0)));
        assertEquals(List.of(day.atTime(8, 30), day.atTime(9, 30)), slots);
    }

    @Test
    void aShowroomOpenForLessThanAnHourHasNoSlots() {
        assertTrue(TestRideRules.slotsOn(LocalDate.of(2026, 11, 22), new TestRideRules.Hours(LocalTime.of(8, 0), LocalTime.of(8, 45))).isEmpty());
    }

    @Test
    void aSlotMustBeOnTheGrid() {
        TestRideRules.Hours hours = new TestRideRules.Hours(LocalTime.of(8, 0), LocalTime.of(21, 0));
        assertTrue(TestRideRules.isOnGrid(LocalDateTime.of(2026, 11, 22, 9, 0), hours));
        assertFalse(TestRideRules.isOnGrid(LocalDateTime.of(2026, 11, 22, 9, 30), hours), "half past is between two slots");
        assertFalse(TestRideRules.isOnGrid(LocalDateTime.of(2026, 11, 22, 21, 0), hours), "a ride at closing time has no hour left");
        assertFalse(TestRideRules.isOnGrid(LocalDateTime.of(2026, 11, 22, 7, 0), hours));
        assertFalse(TestRideRules.isOnGrid(LocalDateTime.of(2026, 11, 22, 9, 0, 1), hours), "seconds are not allowed");
    }

    @Test
    void aBookingNeedsTheLeadTimeAndStaysInsideTheWindow() {
        assertTrue(TestRideRules.isFarEnough(NOW.plusHours(2), NOW, 120), "exactly the lead time is enough");
        assertFalse(TestRideRules.isFarEnough(NOW.plusHours(2).minusMinutes(1), NOW, 120));
        assertFalse(TestRideRules.isFarEnough(NOW.minusHours(1), NOW, 0));
        assertTrue(TestRideRules.isWithinWindow(LocalDateTime.of(2026, 12, 5, 20, 0), NOW, 14), "the 14th day after today is the last one");
        assertFalse(TestRideRules.isWithinWindow(LocalDateTime.of(2026, 12, 6, 8, 0), NOW, 14));
    }

    @Test
    void batteriesAndAccessoriesCannotBeRidden() {
        assertTrue(TestRideRules.isRideable(VehicleType.E_SCOOTER));
        assertTrue(TestRideRules.isRideable(VehicleType.E_BIKE));
        assertTrue(TestRideRules.isRideable(VehicleType.E_MOTORBIKE));
        assertFalse(TestRideRules.isRideable(VehicleType.BATTERY));
        assertFalse(TestRideRules.isRideable(VehicleType.ACCESSORY));
        assertFalse(TestRideRules.isRideable(null), "no vehicle type: not offered");
    }

    @Test
    void phoneNumbersAreVietnameseAndSeparatorsAreIgnored() {
        assertEquals("0901234567", TestRideRules.normalizePhone(" 0901 234.567 "));
        assertEquals("0901234567", TestRideRules.normalizePhone("0901-234-567"));
        assertEquals("0901234567", TestRideRules.normalizePhone("+84 901 234 567"));
        assertEquals("0901234567", TestRideRules.normalizePhone("84901234567"));
        assertEquals("0901234567", TestRideRules.normalizePhone("+84 (0) 901 234 567"));
        assertEquals("02839201234", TestRideRules.normalizePhone("+842839201234"));
        assertFalse(TestRideRules.isValidPhone(TestRideRules.normalizePhone("8412345")), "too short stays invalid");
        assertTrue(TestRideRules.isValidPhone("0901234567"));
        assertTrue(TestRideRules.isValidPhone("02839201234"));
        assertFalse(TestRideRules.isValidPhone("901234567"));
        assertFalse(TestRideRules.isValidPhone("090123456"));
        assertFalse(TestRideRules.isValidPhone("090123456789"));
        assertFalse(TestRideRules.isValidPhone("09012x4567"));
        assertFalse(TestRideRules.isValidPhone(null));
        assertEquals("", TestRideRules.normalizePhone(null));
    }

    @Test
    void stepsFollowTheOrderRequestedConfirmedThenClosed() {
        assertTrue(TestRideRules.canMove("REQUESTED", "CONFIRMED"));
        assertTrue(TestRideRules.canMove("REQUESTED", "CANCELLED"));
        assertTrue(TestRideRules.canMove("REQUESTED", "DONE"), "a customer may ride at an unconfirmed request");
        assertTrue(TestRideRules.canMove("REQUESTED", "NO_SHOW"));
        assertTrue(TestRideRules.canMove("CONFIRMED", "DONE"));
        assertTrue(TestRideRules.canMove("CONFIRMED", "NO_SHOW"));
        assertTrue(TestRideRules.canMove("CONFIRMED", "CANCELLED"));
        assertFalse(TestRideRules.canMove("CONFIRMED", "REQUESTED"));
        for (String closed : List.of("DONE", "CANCELLED", "NO_SHOW")) {
            for (String next : List.of("REQUESTED", "CONFIRMED", "DONE", "CANCELLED", "NO_SHOW")) {
                assertFalse(TestRideRules.canMove(closed, next), closed + " is final, not allowed to " + next);
            }
        }
        assertFalse(TestRideRules.canMove(null, "CONFIRMED"));
    }

    @Test
    void theStaffCanOnlyDoWhatTheClockAllows() {
        LocalDateTime slot = NOW.plusHours(5);
        assertEquals(List.of("CONFIRMED", "CANCELLED"), TestRideRules.nextSteps("REQUESTED", slot, NOW));
        // confirmed, ride not started: only cancel (done is allowed from half an hour before the start)
        assertEquals(List.of("CANCELLED"), TestRideRules.nextSteps("CONFIRMED", slot, NOW));
        assertEquals(List.of("DONE", "CANCELLED"), TestRideRules.nextSteps("CONFIRMED", NOW.plusMinutes(20), NOW));
        // from the start on, the customer may be absent
        assertEquals(List.of("DONE", "NO_SHOW", "CANCELLED"), TestRideRules.nextSteps("CONFIRMED", NOW, NOW));
        // once the hour is over, cancelling is no longer offered: the ride was done or the customer did not come
        assertEquals(List.of("DONE", "NO_SHOW", "CANCELLED"), TestRideRules.nextSteps("CONFIRMED", NOW.minusMinutes(59), NOW));
        assertEquals(List.of("DONE", "NO_SHOW"), TestRideRules.nextSteps("CONFIRMED", NOW.minusHours(1), NOW));
        assertEquals(List.of("DONE", "NO_SHOW"), TestRideRules.nextSteps("CONFIRMED", NOW.minusDays(1), NOW));
        assertEquals(List.of("DONE", "NO_SHOW"), TestRideRules.nextSteps("REQUESTED", NOW.minusDays(1), NOW));
        // a request whose slot began can no longer be confirmed, but the staff can still record what happened
        assertEquals(List.of("DONE", "NO_SHOW", "CANCELLED"), TestRideRules.nextSteps("REQUESTED", NOW.minusMinutes(1), NOW));
        // before the start a request can only be confirmed or cancelled
        assertEquals(List.of("CONFIRMED", "CANCELLED"), TestRideRules.nextSteps("REQUESTED", NOW.plusHours(2), NOW));
        assertTrue(TestRideRules.nextSteps("DONE", NOW.minusDays(1), NOW).isEmpty());
    }

    @Test
    void aShowroomOrBikeThatIsNoLongerAvailableCannotConfirmButCanStillBeCancelled() {
        LocalDateTime slot = NOW.plusHours(5);
        assertEquals(List.of("CANCELLED"), TestRideRules.nextSteps("REQUESTED", slot, NOW, false));
        assertEquals(List.of("CONFIRMED", "CANCELLED"), TestRideRules.nextSteps("REQUESTED", slot, NOW, true));
        assertEquals(List.of("DONE", "NO_SHOW"), TestRideRules.nextSteps("CONFIRMED", NOW.minusHours(2), NOW, false));
    }

    @Test
    void aCancellationIsTimedByTheEndOfTheHour() {
        assertTrue(TestRideRules.timingAllows("CANCELLED", NOW.plusHours(3), NOW));
        assertTrue(TestRideRules.timingAllows("CANCELLED", NOW.minusMinutes(59), NOW));
        assertFalse(TestRideRules.timingAllows("CANCELLED", NOW.minusHours(1), NOW));
        assertFalse(TestRideRules.timingAllows("CANCELLED", NOW.minusDays(1), NOW));
    }

    @Test
    void aCustomerCancelsAnOpenBookingOnlyBeforeItBegins() {
        assertTrue(TestRideRules.canCustomerCancel("REQUESTED", NOW.plusMinutes(1), NOW));
        assertTrue(TestRideRules.canCustomerCancel("CONFIRMED", NOW.plusHours(3), NOW));
        assertFalse(TestRideRules.canCustomerCancel("CONFIRMED", NOW, NOW), "at the start time it is the staff's to close");
        assertFalse(TestRideRules.canCustomerCancel("CONFIRMED", NOW.minusHours(1), NOW));
        assertFalse(TestRideRules.canCustomerCancel("CANCELLED", NOW.plusHours(3), NOW));
        assertFalse(TestRideRules.canCustomerCancel("DONE", NOW.plusHours(3), NOW));
    }

    @Test
    void theReminderGoesOnceToAConfirmedRideInsideTheWindow() {
        assertTrue(TestRideRules.reminderDue("CONFIRMED", NOW.plusHours(23), null, NOW, 24));
        assertTrue(TestRideRules.reminderDue("CONFIRMED", NOW.plusHours(24), null, NOW, 24));
        assertFalse(TestRideRules.reminderDue("CONFIRMED", NOW.plusHours(25), null, NOW, 24), "too early");
        assertFalse(TestRideRules.reminderDue("CONFIRMED", NOW.minusMinutes(1), null, NOW, 24), "already started");
        assertFalse(TestRideRules.reminderDue("CONFIRMED", NOW.plusHours(3), NOW.minusHours(1), NOW, 24), "sent already");
        assertFalse(TestRideRules.reminderDue("REQUESTED", NOW.plusHours(3), null, NOW, 24), "not confirmed");
        assertFalse(TestRideRules.reminderDue("CANCELLED", NOW.plusHours(3), null, NOW, 24));
    }
}
