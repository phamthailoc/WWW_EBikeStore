package com.ebike.qa.unit.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebike.service.testride.TestRideRules;
import com.ebike.service.testride.TestRideRules.Hours;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Every format of showrooms.opening_hours that the root README ("Lai thu" row) and the Javadoc of TestRideRules promise. */
class OpeningHoursParsingTest {

    private static final LocalTime T8 = LocalTime.of(8, 0);
    private static final LocalTime T21 = LocalTime.of(21, 0);

    private static Hours hours(LocalTime open, LocalTime close, DayOfWeek... closed) {
        return new Hours(open, close, closed.length == 0 ? Set.of() : Set.of(closed));
    }

    private static Hours hours(int openH, int closeH, DayOfWeek... closed) {
        return hours(LocalTime.of(openH, 0), LocalTime.of(closeH, 0), closed);
    }

    private static Arguments row(String text, Hours expected) {
        return Arguments.of(text, expected);
    }

    // ------------------------------------------------------------------ the plain clock formats

    static Stream<Arguments> clockFormats() {
        return Stream.of(
            row("08:00 - 21:00", hours(8, 21)),
            row("08:00-21:00", hours(8, 21)),
            row("  08:00   -   21:00  ", hours(8, 21)),
            row("8:00 - 21:00", hours(8, 21)),
            row("09:00 - 18:00", hours(9, 18)),
            row("08:30-17:00", hours(LocalTime.of(8, 30), LocalTime.of(17, 0))),
            row("08:00 - 17:45", hours(LocalTime.of(8, 0), LocalTime.of(17, 45))),
            row("8h - 21h", hours(8, 21)),
            row("8H - 21H", hours(8, 21)),
            row("8h30 - 17h30", hours(LocalTime.of(8, 30), LocalTime.of(17, 30))),
            row("8h-21h", hours(8, 21)),
            row("8 - 21h", hours(8, 21)),
            row("8-21h", hours(8, 21)),
            row("8 - 21", hours(8, 21)),
            row("8-21", hours(8, 21)),
            row("7 - 22", hours(7, 22)),
            row("8:00 AM - 9:00 PM", hours(8, 21)),
            row("8:00 am - 9:00 pm", hours(8, 21)),
            row("8:00AM - 9:00PM", hours(8, 21)),
            row("8:00 A.M. - 9:00 P.M.", hours(8, 21)),
            row("8 AM - 9 PM", hours(8, 21)),
            row("9 AM - 6 PM", hours(9, 18)),
            row("12:00 PM - 9:00 PM", hours(12, 21)),
            row("12:00 AM - 12:00 PM", hours(0, 12)),
            row("08:00 – 21:00", hours(8, 21)),
            row("08:00 — 21:00", hours(8, 21)),
            row("Open 08:00 - 21:00 daily", hours(8, 21)),
            row("Giờ mở cửa: 09:00 - 20:00", hours(9, 20)));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @MethodSource("clockFormats")
    @DisplayName("clock formats named in the README")
    void clockFormatsAreRead(String text, Hours expected) {
        assertThat(TestRideRules.parseOpeningHours(text)).isEqualTo(expected);
    }

    @Test
    void closingAt2400MeansEndOfDayAndSlotsRunToMidnight() {
        Hours parsed = TestRideRules.parseOpeningHours("08:00 - 24:00");
        assertThat(parsed.open()).isEqualTo(T8);
        assertThat(parsed.close()).isEqualTo(TestRideRules.END_OF_DAY);
        assertThat(parsed.closedDays()).isEmpty();
    }

    @Test
    void closingAt24hWritten8hTo24hIsAlsoEndOfDay() {
        assertThat(TestRideRules.parseOpeningHours("8h - 24h").close()).isEqualTo(TestRideRules.END_OF_DAY);
    }

    @Test
    void openingAt2400IsNotAnOpeningTimeSoFallsBackToDefault() {
        // 24:00 as an opening time would make open=END_OF_DAY, close must be after it: impossible
        assertThat(TestRideRules.parseOpeningHours("24:00 - 08:00")).isEqualTo(hours(T8, T21));
    }

    // ------------------------------------------------------------------ bare opening hour sharing AM/PM

    static Stream<Arguments> sharedMeridiem() {
        return Stream.of(
            row("5 - 9 PM", hours(17, 21)),
            row("5-9 PM", hours(17, 21)),
            row("5 - 9 pm", hours(17, 21)),
            row("6 - 10 PM", hours(18, 22)),
            row("8 - 9 PM", hours(8, 21)),
            row("8 - 9 AM", hours(8, 9)),
            row("9 - 10 PM", hours(9, 22)),
            row("1 - 4 PM", hours(13, 16)),
            row("6 - 9 PM", hours(18, 21)));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @MethodSource("sharedMeridiem")
    @DisplayName("a bare opening hour shares PM only when that gives >= 3 hours of opening")
    void bareOpeningHourSharesMeridiemOnlyWhenSpanIsAtLeastThreeHours(String text, Hours expected) {
        assertThat(TestRideRules.parseOpeningHours(text)).isEqualTo(expected);
    }

    // ------------------------------------------------------------------ weekdays are not hours

    static Stream<Arguments> weekdayPrefixes() {
        return Stream.of(
            row("Thứ 2-7: 08:00 - 21:00", hours(8, 21, DayOfWeek.SUNDAY)),
            row("Thứ 2 - Thứ 7: 08:00 - 21:00", hours(8, 21, DayOfWeek.SUNDAY)),
            row("T2 - T7: 8:00 - 20:00", hours(8, 20, DayOfWeek.SUNDAY)),
            row("T2-T6: 8:00 - 18:00", hours(8, 18, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
            row("Mon-Fri 9:00 - 17:00", hours(9, 17, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
            row("Mon-Sat 8:00 - 21:00", hours(8, 21, DayOfWeek.SUNDAY)),
            row("Monday to Saturday: 8:00 - 21:00", hours(8, 21, DayOfWeek.SUNDAY)),
            row("Thứ Hai - Thứ Bảy: 8h - 21h", hours(8, 21, DayOfWeek.SUNDAY)),
            row("Thu hai - Thu bay: 8h - 21h", hours(8, 21, DayOfWeek.SUNDAY)),
            row("T2-CN: 8:00 - 21:00", hours(8, 21)),
            row("Mon-Sun 8:00 - 21:00", hours(8, 21)));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @MethodSource("weekdayPrefixes")
    @DisplayName("weekday ranges in front of the hours: the number of the day is not an hour, the days not named are shut")
    void weekdayPrefixes(String text, Hours expected) {
        assertThat(TestRideRules.parseOpeningHours(text)).isEqualTo(expected);
    }

    @Test
    void thu2To7IsNotReadAs0200To0700() {
        Hours parsed = TestRideRules.parseOpeningHours("Thứ 2-7: 08:00 - 21:00");
        assertThat(parsed.open()).isEqualTo(T8);
        assertThat(parsed.close()).isEqualTo(T21);
    }

    @Test
    void twoWeekdayClausesEachWithItsHoursUseTheFirstRangeAndOpenBothDays() {
        // "hours that differ by weekday are not understood": the first range wins, and both days are open
        Hours parsed = TestRideRules.parseOpeningHours("T2-T6: 8:00 - 18:00; T7: 9:00 - 12:00");
        assertThat(parsed.open()).isEqualTo(T8);
        assertThat(parsed.close()).isEqualTo(LocalTime.of(18, 0));
        assertThat(parsed.closedDays()).containsExactly(DayOfWeek.SUNDAY);
    }

    // ------------------------------------------------------------------ closed weekdays

    static Stream<Arguments> closedDays() {
        return Stream.of(
            row("CN đóng cửa", hours(T8, T21, DayOfWeek.SUNDAY)),
            row("CN Đóng cửa", hours(T8, T21, DayOfWeek.SUNDAY)),
            row("cn dong cua", hours(T8, T21, DayOfWeek.SUNDAY)),
            row("Chủ nhật nghỉ", hours(T8, T21, DayOfWeek.SUNDAY)),
            row("Chu nhat nghi", hours(T8, T21, DayOfWeek.SUNDAY)),
            row("T7 nghỉ", hours(T8, T21, DayOfWeek.SATURDAY)),
            row("T7-CN nghỉ", hours(T8, T21, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
            row("T7 - CN nghỉ", hours(T8, T21, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
            row("Nghỉ T7, CN", hours(T8, T21, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
            row("Nghỉ T7 và CN", hours(T8, T21, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
            row("T6, T7, CN nghỉ", hours(T8, T21, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
            row("Thứ Bảy, Chủ nhật nghỉ", hours(T8, T21, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
            row("Thu bay, chu nhat nghi", hours(T8, T21, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
            row("Thứ 7 nghỉ", hours(T8, T21, DayOfWeek.SATURDAY)),
            row("Closed Sat, Sun", hours(T8, T21, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
            row("Closed Saturday, Sunday", hours(T8, T21, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
            row("Sat-Sun closed", hours(T8, T21, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
            row("Sunday closed", hours(T8, T21, DayOfWeek.SUNDAY)),
            row("Sun off", hours(T8, T21, DayOfWeek.SUNDAY)),
            row("Mon closed", hours(T8, T21, DayOfWeek.MONDAY)),
            row("T2 nghỉ", hours(T8, T21, DayOfWeek.MONDAY)),
            row("Thứ Ba nghỉ", hours(T8, T21, DayOfWeek.TUESDAY)),
            row("Thứ Tư nghỉ", hours(T8, T21, DayOfWeek.WEDNESDAY)),
            row("Thứ Năm nghỉ", hours(T8, T21, DayOfWeek.THURSDAY)),
            row("Thứ Sáu nghỉ", hours(T8, T21, DayOfWeek.FRIDAY)),
            row("T5 nghỉ", hours(T8, T21, DayOfWeek.THURSDAY)),
            row("CN, ngày lễ: nghỉ", hours(T8, T21, DayOfWeek.SUNDAY)),
            row("CN nghỉ từ 12h", hours(T8, T21, DayOfWeek.SUNDAY)),
            row("T3-T5 nghỉ", hours(T8, T21, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)),
            row("Wed-Fri closed", hours(T8, T21, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)),
            // a range may wrap through the end of the week
            row("T7-T2 nghỉ", hours(T8, T21, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY)));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @MethodSource("closedDays")
    @DisplayName("a clause that only gives days off closes those days and keeps the default hours")
    void closedDayClauses(String text, Hours expected) {
        assertThat(TestRideRules.parseOpeningHours(text)).isEqualTo(expected);
    }

    static Stream<Arguments> closedDaysAlongsideHours() {
        return Stream.of(
            row("08:00 - 21:00 trừ Chủ nhật", hours(8, 21, DayOfWeek.SUNDAY)),
            row("8:00 - 21:00 trừ CN", hours(8, 21, DayOfWeek.SUNDAY)),
            row("8:00 - 21:00 except Sunday", hours(8, 21, DayOfWeek.SUNDAY)),
            row("8:00 - 21:00 excluding Sunday", hours(8, 21, DayOfWeek.SUNDAY)),
            row("Mon-Sat 8:00 - 21:00 Sun closed", hours(8, 21, DayOfWeek.SUNDAY)),
            row("T2-T7 8:00 - 21:00 CN nghỉ", hours(8, 21, DayOfWeek.SUNDAY)),
            row("08:00 - 21:00 (T7, CN nghỉ)", hours(8, 21, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
            row("08:00 - 21:00, CN nghỉ", hours(8, 21, DayOfWeek.SUNDAY)),
            row("08:00 - 21:00; T7, CN nghỉ", hours(8, 21, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
            row("08:00 - 21:00 | CN đóng cửa", hours(8, 21, DayOfWeek.SUNDAY)),
            row("08:00 - 21:00\nCN đóng cửa", hours(8, 21, DayOfWeek.SUNDAY)),
            row("CN đóng cửa, 8h - 20h", hours(8, 20, DayOfWeek.SUNDAY)));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @MethodSource("closedDaysAlongsideHours")
    @DisplayName("days off written together with the hours")
    void closedDaysTogetherWithHours(String text, Hours expected) {
        assertThat(TestRideRules.parseOpeningHours(text)).isEqualTo(expected);
    }

    // ------------------------------------------------------------------ the whole showroom is shut

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {
        "Đóng cửa", "đóng cửa", "ĐÓNG CỬA", "Dong cua", "Tạm nghỉ", "Tam nghi", "Tạm ngưng", "Tạm ngừng",
        "Ngừng hoạt động", "Ngưng hoạt động", "Nghỉ", "Closed", "CLOSED", "Off", "off",
        "Tạm nghỉ đến 01/12", "Closed for renovation", "Đóng cửa để sửa chữa", "Tạm nghỉ; Đóng cửa",
        "Tạm nghỉ. Đóng cửa", "Closed, Off"})
    @DisplayName("text that only says the showroom is shut takes no bookings at all")
    void fullyClosedShowroom(String text) {
        Hours parsed = TestRideRules.parseOpeningHours(text);
        assertThat(parsed).isEqualTo(Hours.CLOSED);
        assertThat(TestRideRules.slotsOn(java.time.LocalDate.of(2026, 11, 23), parsed)).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {
        "Mở cửa hằng ngày, đóng cửa ngày lễ", "Mở cửa hàng ngày, đóng cửa ngày lễ", "Open daily, closed on holidays",
        "Open every day, closed Tet", "Closed for lunch", "Nghỉ lễ", "Nghỉ Tết", "Đóng cửa buổi sáng",
        "Closed in the morning", "Closed on public holidays", "Đóng cửa ngày lễ", "Tạm nghỉ trưa"})
    @DisplayName("a closing that is only a holiday, a break or part of the day is not a full closure")
    void notFullyClosed(String text) {
        Hours parsed = TestRideRules.parseOpeningHours(text);
        assertThat(parsed).isNotEqualTo(Hours.CLOSED);
        assertThat(parsed.open()).isEqualTo(T8);
        assertThat(parsed.close()).isEqualTo(T21);
        assertThat(parsed.closedDays()).isEmpty();
    }

    @Test
    void openDailyClosedOnHolidaysKeepsDefaultHoursAndTakesBookings() {
        Hours parsed = TestRideRules.parseOpeningHours("Mở cửa hằng ngày, đóng cửa ngày lễ");
        assertThat(TestRideRules.slotsOn(java.time.LocalDate.of(2026, 11, 23), parsed)).hasSize(13);
    }

    @Test
    void shutWordInsideAnotherWordIsNotAClosure() {
        // each word stands alone: "official", "nghiệp", "offer" must not read as "off"/"nghỉ"
        assertThat(TestRideRules.parseOpeningHours("Official showroom")).isEqualTo(hours(T8, T21));
        assertThat(TestRideRules.parseOpeningHours("Chuyên nghiệp")).isEqualTo(hours(T8, T21));
        assertThat(TestRideRules.parseOpeningHours("Special offer")).isEqualTo(hours(T8, T21));
    }

    @Test
    void monthIsNotAWeekday() {
        // "month" contains "mon"; "Month closed" must not close Mondays
        Hours parsed = TestRideRules.parseOpeningHours("Month closed");
        assertThat(parsed.closedDays()).doesNotContain(DayOfWeek.MONDAY);
    }

    // ------------------------------------------------------------------ fall back to the default

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   \t ", "by appointment", "Theo lịch hẹn", "24/7", "????", "abc", "- ", "08:00", "21:00 - 08:00",
        "22:00 - 02:00", "8:00 - 8:00", "25:00 - 26:00", "8:60 - 21:00", "8:00 AM - 13:00 PM", "0 AM - 9 PM", "8:00 - 21:00 - 22:00x"})
    @DisplayName("what cannot be read as a same-day range falls back to 08:00 - 21:00")
    void fallsBackToDefaultHours(String text) {
        Hours parsed = TestRideRules.parseOpeningHours(text);
        assertThat(parsed.open()).isEqualTo(TestRideRules.DEFAULT_OPEN);
        assertThat(parsed.close()).isEqualTo(TestRideRules.DEFAULT_CLOSE);
        assertThat(parsed.closedDays()).isEmpty();
    }

    @Test
    void nullTextIsTheDefaultHours() {
        assertThat(TestRideRules.parseOpeningHours(null)).isEqualTo(hours(T8, T21));
    }

    @Test
    void onlyTheFirstRangeIsReadWhenThereIsALunchBreak() {
        assertThat(TestRideRules.parseOpeningHours("8:00 - 12:00, 13:30 - 21:00")).isEqualTo(hours(8, 12));
    }

    @Test
    void overnightHoursFallBackToDefault() {
        assertThat(TestRideRules.parseOpeningHours("22:00 - 02:00")).isEqualTo(hours(T8, T21));
    }

    @Test
    void dayOffWithOvernightGarbageStillClosesTheDay() {
        assertThat(TestRideRules.parseOpeningHours("22:00 - 02:00, CN nghỉ")).isEqualTo(hours(T8, T21, DayOfWeek.SUNDAY));
    }

    @Test
    void hoursOfTheFirstRangeWinOverALaterOne() {
        assertThat(TestRideRules.parseOpeningHours("9:00 - 17:00 / 10:00 - 20:00")).isEqualTo(hours(9, 17));
    }

    @Test
    void closedDaysSetIsImmutable() {
        Hours parsed = TestRideRules.parseOpeningHours("CN nghỉ");
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () -> parsed.closedDays().add(DayOfWeek.MONDAY));
    }

    // ------------------------------------------------------------------ hoursOf (the two time columns)

    @Test
    void columnsDecideTheHoursAndTheTextOnlyGivesDaysOff() {
        Hours parsed = TestRideRules.hoursOf("9:00 - 17:00, CN nghỉ", LocalTime.of(7, 30), LocalTime.of(20, 0));
        assertThat(parsed).isEqualTo(hours(LocalTime.of(7, 30), LocalTime.of(20, 0), DayOfWeek.SUNDAY));
    }

    @Test
    void columnsWinOverFreeTextHours() {
        assertThat(TestRideRules.hoursOf("8:00 - 21:00", LocalTime.of(10, 0), LocalTime.of(12, 0))).isEqualTo(hours(10, 12));
    }

    @Test
    void columnsWithTextWeekdayPrefixStillShutTheDaysNotListed() {
        Hours parsed = TestRideRules.hoursOf("T2 - T7: 8:00 - 20:00", LocalTime.of(8, 0), LocalTime.of(20, 0));
        assertThat(parsed).isEqualTo(hours(8, 20, DayOfWeek.SUNDAY));
    }

    @ParameterizedTest
    @MethodSource("badColumns")
    @DisplayName("columns that are missing or not open<close fall back to the free text")
    void invalidColumnsFallBackToText(LocalTime open, LocalTime close) {
        assertThat(TestRideRules.hoursOf("9:00 - 17:00", open, close)).isEqualTo(hours(9, 17));
    }

    static Stream<Arguments> badColumns() {
        return Stream.of(
            Arguments.of(null, null),
            Arguments.of(LocalTime.of(8, 0), null),
            Arguments.of(null, LocalTime.of(20, 0)),
            Arguments.of(LocalTime.of(20, 0), LocalTime.of(8, 0)),
            Arguments.of(LocalTime.of(8, 0), LocalTime.of(8, 0)));
    }

    @Test
    void nullTextAndNoColumnsGivesDefault() {
        assertThat(TestRideRules.hoursOf(null, null, null)).isEqualTo(hours(T8, T21));
    }

    @Test
    void columnsWithNullTextTakeNoDaysOff() {
        assertThat(TestRideRules.hoursOf(null, LocalTime.of(9, 0), LocalTime.of(18, 0))).isEqualTo(hours(9, 18));
    }

    @Test
    void columnsOverrideATemporaryClosureText() {
        // README: once both columns are set they decide the hours; the text is only read for days off
        Hours parsed = TestRideRules.hoursOf("Tạm nghỉ đến 01/12", LocalTime.of(8, 0), LocalTime.of(21, 0));
        assertThat(parsed).isEqualTo(hours(T8, T21));
    }
}
