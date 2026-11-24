package com.ebike.service.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** V22: notification and email text follows the language saved in the customer's profile. */
class StockAlertMessagesTest {

    @Test
    void emailLinksToTheBikePage() {
        String url = StockAlertMessages.productUrl("https://shop.example/", "kinetic-x");
        assertEquals("https://shop.example/product/kinetic-x", url);
        for (String language : new String[] {"vi", "en"}) {
            assertTrue(StockAlertMessages.emailBody("Kinetic X", "White", url).contains(url));
        }
        assertEquals(null, StockAlertMessages.productUrl("", "kinetic-x"));
    }

    @Test
    void aLinkTooLongForTheNotificationColumnIsDropped() {
        assertEquals("/product/kinetic-x", StockAlertMessages.notificationPath("kinetic-x"));
        assertEquals(255, StockAlertMessages.notificationPath("a".repeat(255 - "/product/".length())).length());
        assertEquals(null, StockAlertMessages.notificationPath("a".repeat(256 - "/product/".length())));
        // encoding can make the path longer than the slug
        assertEquals(null, StockAlertMessages.notificationPath("é".repeat(100)));
        assertEquals(null, StockAlertMessages.notificationPath(" "));
    }

    @Test
    void missingColourNeverPrintsNull() {
        for (String language : new String[] {"vi", "en"}) {
            assertFalse(StockAlertMessages.emailBody("Kinetic X", null, null).contains("null"));
            assertFalse(StockAlertMessages.emailSubject("Kinetic X", " ").contains("()"));
            assertFalse(StockAlertMessages.notificationBody("Kinetic X", null).contains("null"));
        }
    }
}
