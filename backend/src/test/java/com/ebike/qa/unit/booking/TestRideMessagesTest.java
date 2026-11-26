package com.ebike.qa.unit.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.ebike.service.testride.TestRideMessages;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TestRideMessagesTest {

    static final LocalDateTime SLOT = LocalDateTime.parse("2026-11-22T09:05");
    static final String PLACE = "Kinetic Q1, 1 Le Loi";

    @Test
    void whenIsHourMinuteDayMonthYearWithZeroPadding() {
        assertThat(TestRideMessages.when(SLOT)).isEqualTo("09:05 22/11/2026");
        assertThat(TestRideMessages.when(LocalDateTime.parse("2026-01-02T00:00"))).isEqualTo("00:00 02/01/2026");
        assertThat(TestRideMessages.when(LocalDateTime.parse("2026-12-31T23:00"))).isEqualTo("23:00 31/12/2026");
    }

    @Test
    void titlesAreDistinctPerKindAndTheReminderIsTheDefault() {
        String[] kinds = {TestRideMessages.CONFIRMED, TestRideMessages.CANCELLED, TestRideMessages.NO_SHOW, TestRideMessages.EXPIRED,
            TestRideMessages.NOTE_UPDATED, TestRideMessages.REMINDER};
        assertThat(java.util.Arrays.stream(kinds).map(TestRideMessages::title).distinct()).hasSize(kinds.length);
        assertThat(TestRideMessages.title("SOMETHING_NEW")).isEqualTo(TestRideMessages.title(TestRideMessages.REMINDER));
        assertThat(TestRideMessages.title(TestRideMessages.CONFIRMED)).isEqualTo("Lịch lái thử đã được xác nhận");
    }

    @Test
    void placeJoinsNameAndTrimmedAddressAndSkipsABlankAddress() {
        assertThat(TestRideMessages.place("Kinetic Q1", " 1 Le Loi ")).isEqualTo("Kinetic Q1, 1 Le Loi");
        assertThat(TestRideMessages.place("Kinetic Q1", null)).isEqualTo("Kinetic Q1");
        assertThat(TestRideMessages.place("Kinetic Q1", "   ")).isEqualTo("Kinetic Q1");
        assertThat(TestRideMessages.place("Kinetic Q1", "")).isEqualTo("Kinetic Q1");
    }

    @Test
    void confirmedBodyNamesBikePlaceTimeAndAsksForId() {
        String body = TestRideMessages.body(TestRideMessages.CONFIRMED, "Aero X", PLACE, SLOT, null);
        assertThat(body).contains("Aero X").contains(PLACE).contains("09:05 22/11/2026").contains("giấy tờ tùy thân").doesNotContain("Ghi chú của cửa hàng");
    }

    @Test
    void bodiesAppendTheStaffNoteTrimmedWhenThereIsOne() {
        for (String kind : new String[] {TestRideMessages.CONFIRMED, TestRideMessages.CANCELLED, TestRideMessages.NO_SHOW, TestRideMessages.NOTE_UPDATED}) {
            assertThat(TestRideMessages.body(kind, "Aero X", PLACE, SLOT, "  Bike in repair  ")).as(kind).contains("Ghi chú của cửa hàng: Bike in repair");
            assertThat(TestRideMessages.body(kind, "Aero X", PLACE, SLOT, "   ")).as(kind).doesNotContain("Ghi chú của cửa hàng");
            assertThat(TestRideMessages.body(kind, "Aero X", PLACE, SLOT, null)).as(kind).doesNotContain("null");
        }
    }

    @Test
    void cancelledBodyOffersToRebookAndExpiredNeverBlamesTheCustomer() {
        assertThat(TestRideMessages.body(TestRideMessages.CANCELLED, "Aero X", PLACE, SLOT, "x")).contains("hủy").contains("đặt lại");
        String expired = TestRideMessages.body(TestRideMessages.EXPIRED, "Aero X", PLACE, SLOT, "ignored note");
        assertThat(expired).contains("chưa được xác nhận").contains("đặt lại").doesNotContain("không đến");
    }

    @Test
    void noShowBodyTellsTheCustomerTheyDidNotCome() {
        assertThat(TestRideMessages.body(TestRideMessages.NO_SHOW, "Aero X", PLACE, SLOT, null)).contains("không đến");
    }

    @Test
    void reminderBodyAsksToCancelWhenUnableToCome() {
        String body = TestRideMessages.body(TestRideMessages.REMINDER, "Aero X", PLACE, SLOT, null);
        assertThat(body).contains("Sắp đến lịch").contains("hủy lịch").contains("giấy tờ tùy thân");
    }

    @Test
    void unknownKindFallsBackToTheReminderBody() {
        assertThat(TestRideMessages.body("???", "Aero X", PLACE, SLOT, null)).isEqualTo(TestRideMessages.body(TestRideMessages.REMINDER, "Aero X", PLACE, SLOT, null));
    }

    @Test
    void onlyConfirmationsStoreCancellationsAndRemindersAreEmailed() {
        assertThat(TestRideMessages.sendsEmail(TestRideMessages.CONFIRMED)).isTrue();
        assertThat(TestRideMessages.sendsEmail(TestRideMessages.CANCELLED)).isTrue();
        assertThat(TestRideMessages.sendsEmail(TestRideMessages.REMINDER)).isTrue();
        assertThat(TestRideMessages.sendsEmail(TestRideMessages.NO_SHOW)).isFalse();
        assertThat(TestRideMessages.sendsEmail(TestRideMessages.EXPIRED)).isFalse();
        assertThat(TestRideMessages.sendsEmail(TestRideMessages.NOTE_UPDATED)).isFalse();
        assertThat(TestRideMessages.sendsEmail(null)).isFalse();
    }

    @Test
    void emailSubjectsNameTheBike() {
        assertThat(TestRideMessages.emailSubject(TestRideMessages.CONFIRMED, "Aero X")).contains("đã xác nhận").contains("Aero X");
        assertThat(TestRideMessages.emailSubject(TestRideMessages.CANCELLED, "Aero X")).contains("đã bị hủy").contains("Aero X");
        assertThat(TestRideMessages.emailSubject(TestRideMessages.REMINDER, "Aero X")).contains("nhắc lịch").contains("Aero X");
    }

    @Test
    void bookingsUrlStripsOneTrailingSlashAndAppendsThePage() {
        assertThat(TestRideMessages.bookingsUrl("https://shop.vn")).isEqualTo("https://shop.vn/customer/test-rides");
        assertThat(TestRideMessages.bookingsUrl("https://shop.vn/")).isEqualTo("https://shop.vn/customer/test-rides");
        assertThat(TestRideMessages.bookingsUrl("  https://shop.vn/  ")).isEqualTo("https://shop.vn/customer/test-rides");
        assertThat(TestRideMessages.bookingsUrl("http://localhost:8080")).isEqualTo("http://localhost:8080/customer/test-rides");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void noBaseUrlMeansNoLink(String base) {
        assertThat(TestRideMessages.bookingsUrl(base)).isNull();
    }

    @Test
    void emailBodyContainsTheLinkOnlyWhenOneIsConfigured() {
        String with = TestRideMessages.emailBody(TestRideMessages.CONFIRMED, "Aero X", "Kinetic Q1", "1 Le Loi", SLOT, null, " https://shop.vn/customer/test-rides ");
        assertThat(with).contains("Lịch lái thử của bạn: https://shop.vn/customer/test-rides").contains("Kinetic Q1, 1 Le Loi").contains("Kinetic.");
        String without = TestRideMessages.emailBody(TestRideMessages.CONFIRMED, "Aero X", "Kinetic Q1", null, SLOT, null, null);
        assertThat(without).doesNotContain("Lịch lái thử của bạn:").contains("Kinetic Q1").doesNotContain("null").doesNotContain("Kinetic Q1, null");
        assertThat(TestRideMessages.emailBody(TestRideMessages.REMINDER, "Aero X", "Kinetic Q1", "x", SLOT, null, "   ")).doesNotContain("Lịch lái thử của bạn:");
    }

    @Test
    void emailBodyEndsWithTheReasonForTheEmail() {
        assertThat(TestRideMessages.emailBody(TestRideMessages.CANCELLED, "Aero X", "Kinetic Q1", null, SLOT, "n", null))
            .endsWith("Bạn nhận email này vì đã đặt lịch lái thử tại Kinetic.\n");
    }
}
