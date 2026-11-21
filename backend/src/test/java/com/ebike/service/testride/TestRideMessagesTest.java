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
    void anythingButEnglishIsVietnamese() {
        assertEquals("en", TestRideMessages.normalize("en"));
        assertEquals("en", TestRideMessages.normalize(" EN-GB "));
        assertEquals("vi", TestRideMessages.normalize("vi"));
        assertEquals("vi", TestRideMessages.normalize(null));
        assertEquals("vi", TestRideMessages.normalize("fr"));
    }

    @Test
    void theTimeReadsNaturallyInEachLanguage() {
        assertEquals("09:05 22/11/2026", TestRideMessages.when("vi", SLOT));
        assertEquals("22/11/2026 at 09:05", TestRideMessages.when("en", SLOT));
    }

    @Test
    void everyKindHasATitleAndABodyInBothLanguages() {
        for (String kind : KINDS) {
            String vi = TestRideMessages.title("vi", kind);
            String en = TestRideMessages.title("en", kind);
            assertFalse(vi.isBlank() || en.isBlank(), kind);
            assertNotEquals(vi, en, kind);
            String viBody = TestRideMessages.body("vi", kind, "Kinetic X", "Quan 1", SLOT, null);
            String enBody = TestRideMessages.body("en", kind, "Kinetic X", "Quan 1", SLOT, null);
            assertTrue(viBody.contains("Kinetic X") && viBody.contains("Quan 1") && viBody.contains("09:05 22/11/2026"), viBody);
            assertTrue(enBody.contains("Kinetic X") && enBody.contains("Quan 1") && enBody.contains("22/11/2026 at 09:05"), enBody);
            assertNotEquals(viBody, enBody, kind);
        }
    }

    @Test
    void theStoresReasonIsShownWhenThereIsOne() {
        String withReason = TestRideMessages.body("en", TestRideMessages.CANCELLED, "Kinetic X", "Quan 1", SLOT, "  The bike is in service  ");
        assertTrue(withReason.contains("Note from the store: The bike is in service"), withReason);
        assertTrue(TestRideMessages.body("vi", TestRideMessages.CANCELLED, "Kinetic X", "Quan 1", SLOT, "Xe đang bảo dưỡng").contains("Ghi chú của cửa hàng: Xe đang bảo dưỡng"));
        assertFalse(TestRideMessages.body("en", TestRideMessages.CANCELLED, "Kinetic X", "Quan 1", SLOT, "  ").contains("Note from the store"));
        assertFalse(TestRideMessages.body("en", TestRideMessages.CANCELLED, "Kinetic X", "Quan 1", SLOT, null).contains("Note from the store"));
    }

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
    void theEmailCarriesTheAddressTheLinkAndTheReasonForReceivingIt() {
        String text = TestRideMessages.emailBody("en", TestRideMessages.REMINDER, "Kinetic X", "Quan 1", "187 Nguyen Thai Hoc",
            SLOT, null, TestRideMessages.bookingsUrl("https://shop.example/"));
        assertTrue(text.contains("Quan 1, 187 Nguyen Thai Hoc"), text);
        assertTrue(text.contains("https://shop.example/customer/test-rides"), text);
        assertTrue(text.contains("you booked a test ride"), text);
        String vi = TestRideMessages.emailBody("vi", TestRideMessages.CONFIRMED, "Kinetic X", "Quan 1", null, SLOT, null, null);
        assertTrue(vi.contains("Quan 1"), vi);
        assertFalse(vi.contains("customer/test-rides"), "no link without a configured address");
        assertTrue(vi.contains("đã đặt lịch lái thử"), vi);
    }

    @Test
    void emailSubjectsNameTheBikeInBothLanguages() {
        for (String kind : List.of(TestRideMessages.CONFIRMED, TestRideMessages.CANCELLED, TestRideMessages.REMINDER)) {
            assertTrue(TestRideMessages.emailSubject("en", kind, "Kinetic X").contains("Kinetic X"), kind);
            assertTrue(TestRideMessages.emailSubject("vi", kind, "Kinetic X").contains("Kinetic X"), kind);
            assertNotEquals(TestRideMessages.emailSubject("en", kind, "Kinetic X"), TestRideMessages.emailSubject("vi", kind, "Kinetic X"));
        }
    }

    @Test
    void theBookingsLinkNeedsAnAddress() {
        assertNull(TestRideMessages.bookingsUrl(null));
        assertNull(TestRideMessages.bookingsUrl("  "));
        assertEquals("http://localhost:8080/customer/test-rides", TestRideMessages.bookingsUrl("http://localhost:8080"));
    }
}
