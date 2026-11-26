package com.ebike.qa.unit.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebike.service.testride.TestRideRules;
import com.ebike.service.testride.TestRideRules.Hours;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Less obvious but plausible ways a manager types the opening hours. */
class OpeningHoursNastyTest {

    private static Hours h(int o, int c, DayOfWeek... closed) {
        return new Hours(LocalTime.of(o, 0), LocalTime.of(c, 0), closed.length == 0 ? Set.of() : Set.of(closed));
    }

    static Stream<Arguments> cases() {
        return Stream.of(
            Arguments.of("Mon–Fri 9am–5pm", h(9, 17, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
            Arguments.of("8am - 5pm", h(8, 17)),
            Arguments.of("8AM-5PM", h(8, 17)),
            Arguments.of("Thứ 2 - CN: 8h - 21h", h(8, 21)),
            Arguments.of("CN: đóng cửa", h(8, 21, DayOfWeek.SUNDAY)),
            Arguments.of("Chủ nhật: nghỉ", h(8, 21, DayOfWeek.SUNDAY)),
            Arguments.of("Sunday: closed", h(8, 21, DayOfWeek.SUNDAY)),
            Arguments.of("Closed on Sundays", h(8, 21, DayOfWeek.SUNDAY)),
            Arguments.of("Closed Mondays", h(8, 21, DayOfWeek.MONDAY)),
            Arguments.of("Đóng cửa Chủ nhật", h(8, 21, DayOfWeek.SUNDAY)),
            Arguments.of("Nghỉ Thứ 7, Chủ nhật", h(8, 21, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
            Arguments.of("8:00-21:00 (CN nghỉ)", h(8, 21, DayOfWeek.SUNDAY)),
            Arguments.of("Mở cửa 8h-21h, nghỉ CN", h(8, 21, DayOfWeek.SUNDAY)),
            Arguments.of("08:00 - 21:00. Closed Sunday.", h(8, 21, DayOfWeek.SUNDAY)),
            Arguments.of("Open 24 hours", h(8, 21)),
            Arguments.of("Mở cửa 7:30 - 20:30", new Hours(LocalTime.of(7, 30), LocalTime.of(20, 30))),
            Arguments.of("9:00 - 18:00 | Thứ 2 - Thứ 7", h(9, 18)),
            Arguments.of("T2-T7: 8h-20h, CN: 9h-12h", h(8, 20)),
            Arguments.of("Hằng ngày 8h - 22h", h(8, 22)),
            Arguments.of("Daily 10:00 - 22:00", h(10, 22)),
            Arguments.of("10:00 PM - 11:00 PM", h(22, 23)),
            Arguments.of("0:00 - 24:00", new Hours(LocalTime.MIDNIGHT, TestRideRules.END_OF_DAY)));
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @MethodSource("cases")
    void reads(String text, Hours expected) {
        assertThat(TestRideRules.parseOpeningHours(text)).isEqualTo(expected);
    }
}
