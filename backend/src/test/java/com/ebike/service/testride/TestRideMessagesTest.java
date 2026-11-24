package com.ebike.service.testride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** V23: the notices and emails of a test ride are written in the language of the profile. */
class TestRideMessagesTest {

    private static final LocalDateTime SLOT = LocalDateTime.of(2026, 11, 22, 9, 5);
    private static final List<String> KINDS = List.of(
        TestRideMessages.CONFIRMED, TestRideMessages.CANCELLED, TestRideMessages.NO_SHOW, TestRideMessages.EXPIRED, TestRideMessages.REMINDER,
        TestRideMessages.NOTE_UPDATED);

    @Test
    void onlyConfirmationsStoreCancellationsAndRemindersAreEmailed() {
        assertTrue(TestRideMessages.sendsEmail(TestRideMessages.CONFIRMED));
        assertTrue(TestRideMessages.sendsEmail(TestRideMessages.CANCELLED));
        assertTrue(TestRideMessages.sendsEmail(TestRideMessages.REMINDER));
        assertFalse(TestRideMessages.sendsEmail(TestRideMessages.NO_SHOW));
        assertFalse(TestRideMessages.sendsEmail(TestRideMessages.EXPIRED));
        assertFalse(TestRideMessages.sendsEmail(TestRideMessages.NOTE_UPDATED));
    }

    @Test
    void theBookingsLinkNeedsAnAddress() {
        assertNull(TestRideMessages.bookingsUrl(null));
        assertNull(TestRideMessages.bookingsUrl("  "));
        assertEquals("http://localhost:8080/customer/test-rides", TestRideMessages.bookingsUrl("http://localhost:8080"));
    }

    @Test
    void theTimeReadsNaturally() {
        assertEquals("09:05 22/11/2026", TestRideMessages.when(SLOT));
    }

    @Test
    void everyKindHasATitleAndABody() {
        for (String kind : KINDS) {
            assertFalse(TestRideMessages.title(kind).isBlank(), kind);
            String body = TestRideMessages.body(kind, "Kinetic X", "Quan 1", SLOT, null);
            assertTrue(body.contains("Kinetic X") && body.contains("Quan 1") && body.contains("09:05 22/11/2026"), body);
        }
        assertEquals("Lịch lái thử đã được xác nhận", TestRideMessages.title(TestRideMessages.CONFIRMED));
        assertEquals("Lịch lái thử đã bị hủy", TestRideMessages.title(TestRideMessages.CANCELLED));
    }

    @Test
    void theStoresReasonIsShownWhenThereIsOne() {
        String withReason = TestRideMessages.body(TestRideMessages.CANCELLED, "Kinetic X", "Quan 1", SLOT, "  Xe đang bảo dưỡng  ");
        assertTrue(withReason.contains("Ghi chú của cửa hàng: Xe đang bảo dưỡng"), withReason);
        assertFalse(TestRideMessages.body(TestRideMessages.CANCELLED, "Kinetic X", "Quan 1", SLOT, "  ").contains("Ghi chú của cửa hàng"));
        assertFalse(TestRideMessages.body(TestRideMessages.CANCELLED, "Kinetic X", "Quan 1", SLOT, null).contains("Ghi chú của cửa hàng"));
        // a visit that did not happen also tells the customer why
        assertTrue(TestRideMessages.body(TestRideMessages.NO_SHOW, "Kinetic X", "Quan 1", SLOT, "Đã gọi không được").contains("Ghi chú của cửa hàng: Đã gọi không được"));
    }

    @Test
    void theEmailCarriesTheAddressTheLinkAndTheReasonForReceivingIt() {
        String text = TestRideMessages.emailBody(TestRideMessages.REMINDER, "Kinetic X", "Quan 1", "187 Nguyen Thai Hoc",
            SLOT, null, TestRideMessages.bookingsUrl("https://shop.example/"));
        assertTrue(text.contains("Quan 1, 187 Nguyen Thai Hoc"), text);
        assertTrue(text.contains("https://shop.example/customer/test-rides"), text);
        assertTrue(text.contains("đã đặt lịch lái thử"), text);
        String withoutLink = TestRideMessages.emailBody(TestRideMessages.CONFIRMED, "Kinetic X", "Quan 1", null, SLOT, null, null);
        assertTrue(withoutLink.contains("Quan 1"), withoutLink);
        assertFalse(withoutLink.contains("customer/test-rides"), "no link without a configured address");
    }

    @Test
    void emailSubjectsNameTheBike() {
        for (String kind : List.of(TestRideMessages.CONFIRMED, TestRideMessages.CANCELLED, TestRideMessages.REMINDER)) {
            assertTrue(TestRideMessages.emailSubject(kind, "Kinetic X").contains("Kinetic X"), kind);
        }
        assertEquals("Kinetic E-Bike: đã xác nhận lịch lái thử Kinetic X", TestRideMessages.emailSubject(TestRideMessages.CONFIRMED, "Kinetic X"));
    }
}
