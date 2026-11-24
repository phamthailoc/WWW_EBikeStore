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
}
