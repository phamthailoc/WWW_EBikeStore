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
        assertEquals(new TestRideRules.Hours(LocalTime.of(9, 0), LocalTime.of(18, 30)), hoursOnly("Thứ 2 - Thứ 7: 9h00 – 18h30"));
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
        assertEquals(new TestRideRules.Hours(LocalTime.of(8, 30), LocalTime.of(17, 0)), hoursOnly("Mon-Sat: 8:30 a.m. - 5 p.m."));
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
        assertEquals(java.util.Set.of(java.time.DayOfWeek.SUNDAY), TestRideRules.parseOpeningHours("Thứ 2-7: 08:00 - 21:00").closedDays(), "Sunday has no hours");
        assertTrue(TestRideRules.parseOpeningHours("08:00 - 21:00").closedDays().isEmpty());
        assertTrue(TestRideRules.parseOpeningHours("Mở cửa 9h - 12h").closedDays().isEmpty());
    }

    /** The opening and closing time only, without the weekdays that the text leaves shut. */
    private static TestRideRules.Hours hoursOnly(String text) {
        TestRideRules.Hours hours = TestRideRules.parseOpeningHours(text);
        return new TestRideRules.Hours(hours.open(), hours.close());
    }

    private static TestRideRules.Hours days(String text, java.time.DayOfWeek... expected) {
        TestRideRules.Hours hours = TestRideRules.parseOpeningHours(text);
        assertEquals(java.util.Set.of(expected), hours.closedDays(), text);
        return hours;
    }

    @Test
    void aRangeOfWeekdaysOffGivesEveryDayInIt() {
        java.time.DayOfWeek[] weekdays = {java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY,
            java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY};
        days("T2-T6 nghỉ", weekdays);
        days("Thứ 2 - Thứ 6 đóng cửa", weekdays);
        days("Thứ 2-6 nghỉ", weekdays);
        days("Mon-Fri closed", weekdays);
        days("Monday to Friday closed", weekdays);
        days("T7-CN nghỉ", java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY);
        days("Sat-Sun closed", java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY);
        // a range that wraps past Sunday
        days("T7 - T2 nghỉ", java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY, java.time.DayOfWeek.MONDAY);
        // two days in separate words, and a list split by a comma before the clause that says "off"
        days("T7, CN đóng cửa", java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY);
        days("T7 và CN nghỉ", java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY);
        // the hours stay readable next to it
        TestRideRules.Hours hours = days("8h-17h; Sat-Sun closed", java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY);
        assertEquals(new TestRideRules.Hours(LocalTime.of(8, 0), LocalTime.of(17, 0), hours.closedDays()), hours);
        // a day off written after the hours of the same clause
        TestRideRules.Hours sameClause = days("Mon-Sat 8:00 - 21:00 Sun closed", java.time.DayOfWeek.SUNDAY);
        assertEquals(LocalTime.of(21, 0), sameClause.close());
        // a list cut off from its "off" by a clause with hours does not leak into it
        days("T7, 8-17h, CN nghỉ", java.time.DayOfWeek.SUNDAY);
    }

    @Test
    void aListOfDaysOffIsReadWhateverItsLengthAndOrder() {
        java.time.DayOfWeek sat = java.time.DayOfWeek.SATURDAY;
        java.time.DayOfWeek sun = java.time.DayOfWeek.SUNDAY;
        // the "off" first, then the days
        days("Nghỉ T7, CN", sat, sun);
        days("Closed: Saturday, Sunday", sat, sun);
        days("Mon-Fri 8:00-17:00, closed Sat, Sun", sat, sun);
        days("8h-17h, nghỉ T6, T7, CN", java.time.DayOfWeek.FRIDAY, sat, sun);
        // the days first, then the "off"
        days("T6, T7, CN nghỉ", java.time.DayOfWeek.FRIDAY, sat, sun);
        days("Thứ 2, Thứ 3, Thứ 4, Thứ 5 nghỉ", java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.THURSDAY);
        // a clause about holidays after the days does not lose them
        days("CN, ngày lễ: nghỉ", sun);
        // the list in brackets after the hours
        TestRideRules.Hours bracket = days("08:00 - 21:00 (T7, CN nghỉ)", sat, sun);
        assertEquals(LocalTime.of(21, 0), bracket.close());
        // days off do not leak into a later clause that has nothing to do with them
        days("T7, 8-17h, CN nghỉ", sun);
        days("Nghỉ T7, 8h-17h", sat);
    }

    @Test
    void weekdaysWrittenAsWordsAreReadAndDaysWithNoHoursAreShut() {
        java.time.DayOfWeek sat = java.time.DayOfWeek.SATURDAY;
        java.time.DayOfWeek sun = java.time.DayOfWeek.SUNDAY;
        days("8:00 - 21:00, Thứ Bảy nghỉ", sat);
        days("8:00 - 21:00, thu bay nghi", sat);
        days("Chủ nhật nghỉ", sun);
        days("chu nhat dong cua", sun);
        days("Thứ Hai - Thứ Sáu nghỉ", java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY,
            java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY);
        days("Thứ Tư nghỉ", java.time.DayOfWeek.WEDNESDAY);
        days("Thứ Năm nghỉ", java.time.DayOfWeek.THURSDAY);
        // "except"
        days("8:00 - 21:00 trừ Chủ nhật", sun);
        days("8:00 - 21:00 except Sunday", sun);
        days("8:00 - 21:00 trừ T7, CN", sat, sun);
        // hours written for some days only: the others are shut
        TestRideRules.Hours monToSat = days("T2 - T7: 8:00 - 20:00", sun);
        assertEquals(LocalTime.of(20, 0), monToSat.close());
        days("Thứ Hai - Thứ Sáu: 8h - 17h", sat, sun);
        days("Mon-Fri 8:00-17:00, Sat 9:00-12:00", sun);
        // hours with no day in front are for every day
        days("8:00 - 21:00");
        days("Mở cửa 8:00 - 21:00 hằng ngày");
    }

    @Test
    void offAndClosedAreWholeWordsOnly() {
        TestRideRules.Hours fallback = new TestRideRules.Hours(TestRideRules.DEFAULT_OPEN, TestRideRules.DEFAULT_CLOSE);
        // "nghi" is inside "nghiệp" and "off" inside "official": neither is a day off or a shut showroom
        assertEquals(fallback, TestRideRules.parseOpeningHours("Chuyên nghiệp, T7 phục vụ"));
        assertEquals(fallback, TestRideRules.parseOpeningHours("Chủ nhật official hours"));
        assertEquals(fallback, TestRideRules.parseOpeningHours("Nghiệp vụ bán xe, T7 nghiên cứu"));
        // "month" is no weekday
        assertTrue(TestRideRules.parseOpeningHours("08:00 - 21:00, closed month-end").closedDays().isEmpty());
        assertTrue(TestRideRules.parseOpeningHours("08:00 - 21:00, nghiệp đoàn").closedDays().isEmpty());
    }

    @Test
    void closedCountsOnlyWhenTheWholeTextSaysNothingElse() {
        TestRideRules.Hours fallback = new TestRideRules.Hours(TestRideRules.DEFAULT_OPEN, TestRideRules.DEFAULT_CLOSE);
        assertEquals(fallback, TestRideRules.parseOpeningHours("Mở cửa hằng ngày, đóng cửa ngày lễ"));
        assertEquals(fallback, TestRideRules.parseOpeningHours("Mở cửa hằng ngày đóng cửa ngày lễ"));
        assertEquals(fallback, TestRideRules.parseOpeningHours("Open daily, closed on holidays"));
        assertEquals(fallback, TestRideRules.parseOpeningHours("Đóng cửa buổi trưa"));
        assertEquals(fallback, TestRideRules.parseOpeningHours("Tạm đóng cửa dịp Tết, mở cửa lại sau"));
        // a note around it that is still only about being shut
        assertEquals(TestRideRules.Hours.CLOSED, TestRideRules.parseOpeningHours("Tạm nghỉ"));
        assertEquals(TestRideRules.Hours.CLOSED, TestRideRules.parseOpeningHours("Đóng cửa."));
        assertEquals(TestRideRules.Hours.CLOSED, TestRideRules.parseOpeningHours("Closed; đóng cửa"));
    }

    @Test
    void anHourWithNoUnitBeforeADashIsReadWhenTheClosingTimeHasOne() {
        TestRideRules.Hours eightToNine = new TestRideRules.Hours(LocalTime.of(8, 0), LocalTime.of(21, 0));
        assertEquals(eightToNine, TestRideRules.parseOpeningHours("8 - 21h"));
        assertEquals(eightToNine, TestRideRules.parseOpeningHours("8-21h"));
        assertEquals(eightToNine, TestRideRules.parseOpeningHours("Mở cửa 8 - 21h hằng ngày"));
        // "8 - 9 PM" would be one hour in the evening, so the 8 stays in the morning
        assertEquals(eightToNine, TestRideRules.parseOpeningHours("8 - 9 PM"));
        // the bare hour shares the PM of the closing time when that leaves a sensible stretch
        assertEquals(new TestRideRules.Hours(LocalTime.of(17, 0), LocalTime.of(21, 0)), TestRideRules.parseOpeningHours("5 - 9 PM"));
        assertEquals(new TestRideRules.Hours(LocalTime.of(19, 0), LocalTime.of(23, 0)), TestRideRules.parseOpeningHours("7 - 11 PM"));
        assertEquals(new TestRideRules.Hours(LocalTime.of(8, 0), LocalTime.of(11, 0)), TestRideRules.parseOpeningHours("8 - 11 AM"));
        assertEquals(new TestRideRules.Hours(LocalTime.of(9, 0), LocalTime.of(18, 0)), TestRideRules.parseOpeningHours("9 - 6 PM"));
        assertEquals(new TestRideRules.Hours(LocalTime.of(8, 0), LocalTime.of(12, 0)), TestRideRules.parseOpeningHours("8 - 12 PM"));
        assertEquals(new TestRideRules.Hours(LocalTime.of(9, 0), LocalTime.of(17, 30)), TestRideRules.parseOpeningHours("9 - 17:30"));
        assertEquals(new TestRideRules.Hours(LocalTime.of(11, 0), LocalTime.of(13, 0)), TestRideRules.parseOpeningHours("11 - 1 PM"));
        // the 2 of "T2" and "Thứ 2" are days, not an opening hour
        assertEquals(eightToNine, hoursOnly("T2 - 8h-21h"));
        assertEquals(eightToNine, hoursOnly("T2-T7: 8 - 21h"));
        assertEquals(eightToNine, hoursOnly("Thứ 2 - Thứ 7: 8 - 21h"));
        // a closing time before the opening one falls back
        assertEquals(eightToNine, TestRideRules.parseOpeningHours("21 - 8h"));
    }

    @Test
    void theDaysOfTheWeekAreNotTakenForTheHours() {
        TestRideRules.Hours expected = new TestRideRules.Hours(LocalTime.of(8, 0), LocalTime.of(21, 0));
        assertEquals(expected, hoursOnly("Thứ 2-7: 08:00 - 21:00"));
        assertEquals(expected, hoursOnly("T2-T7: 8h - 21h"));
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
        for (String text : List.of("Đóng cửa", "Tạm nghỉ", "Dong cua", "Closed", "Tạm ngưng hoạt động", "Đóng   cửa", "Off", "off")) {
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
        assertFalse(TestRideRules.canMove("REQUESTED", "NO_SHOW"), "nobody is absent from a ride the shop never agreed to");
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
    void aRequestIsClosedOnlyOnceItsHourIsOver() {
        assertEquals(NOW.minusHours(1), TestRideRules.expiryCutoff(NOW));
        // a request whose ride is under way can still be marked done, so it is not yet expired
        LocalDateTime running = NOW.minusMinutes(30);
        assertFalse(running.isBefore(TestRideRules.expiryCutoff(NOW)) || running.equals(TestRideRules.expiryCutoff(NOW)));
        assertTrue(TestRideRules.nextSteps("REQUESTED", running, NOW).contains("DONE"));
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
        // a request whose slot began can no longer be confirmed and is never a no-show: the staff can record a ride that happened, or cancel it
        assertEquals(List.of("DONE"), TestRideRules.nextSteps("REQUESTED", NOW.minusDays(1), NOW));
        assertEquals(List.of("DONE", "CANCELLED"), TestRideRules.nextSteps("REQUESTED", NOW.minusMinutes(1), NOW));
        assertEquals(List.of("CONFIRMED", "DONE", "CANCELLED"), TestRideRules.nextSteps("REQUESTED", NOW.plusMinutes(20), NOW));
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

    @Test
    void theTwoTimeColumnsDecideTheHoursAndTheTextOnlyGivesDaysOff() {
        // the columns win over a text the parser could not read
        assertEquals(new TestRideRules.Hours(LocalTime.of(9, 0), LocalTime.of(18, 0)), TestRideRules.hoursOf("by appointment", LocalTime.of(9, 0), LocalTime.of(18, 0)));
        // the text still gives the days off
        assertEquals(java.util.Set.of(java.time.DayOfWeek.SUNDAY), TestRideRules.hoursOf("CN nghỉ", LocalTime.of(9, 0), LocalTime.of(18, 0)).closedDays());
        // one column missing, or open not before close: the text is read as before
        assertEquals(TestRideRules.parseOpeningHours("08:00 - 12:00"), TestRideRules.hoursOf("08:00 - 12:00", LocalTime.of(9, 0), null));
        assertEquals(TestRideRules.parseOpeningHours("08:00 - 12:00"), TestRideRules.hoursOf("08:00 - 12:00", LocalTime.of(18, 0), LocalTime.of(9, 0)));
    }
}
