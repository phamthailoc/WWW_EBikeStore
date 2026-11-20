package com.ebike.service.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** V22: notification and email text follows the language saved in the customer's profile. */
class StockAlertMessagesTest {

    @Test
    void anythingButEnglishIsVietnamese() {
        assertEquals("vi", StockAlertMessages.normalize(null));
        assertEquals("vi", StockAlertMessages.normalize("vi"));
        assertEquals("vi", StockAlertMessages.normalize("fr"));
        assertEquals("en", StockAlertMessages.normalize("en"));
        assertEquals("en", StockAlertMessages.normalize(" EN-us "));
    }

    @Test
    void notificationIsInTheCustomersLanguage() {
        assertEquals("Back in stock", StockAlertMessages.notificationTitle("en"));
        assertEquals("Xe đã có hàng lại", StockAlertMessages.notificationTitle("vi"));
        assertTrue(StockAlertMessages.notificationBody("en", "Kinetic X", "White").contains("is back in stock"));
        assertTrue(StockAlertMessages.notificationBody("vi", "Kinetic X", "Trắng").contains("đã có hàng lại"));
    }

    @Test
    void emailNamesTheBikeAndColourInBothLanguages() {
        for (String language : new String[] {"vi", "en"}) {
            assertTrue(StockAlertMessages.emailSubject(language, "Kinetic X", "White").contains("Kinetic X"));
            assertTrue(StockAlertMessages.emailBody(language, "Kinetic X", "White", null).contains("White"));
        }
        assertFalse(StockAlertMessages.emailBody("en", "Kinetic X", "White", null).contains("đã"));
        assertFalse(StockAlertMessages.emailSubject("vi", "Kinetic X", "Trắng").contains("back in stock"));
    }

    @Test
    void emailLinksToTheBikePage() {
        String url = StockAlertMessages.productUrl("https://shop.example/", "kinetic-x");
        assertEquals("https://shop.example/product/kinetic-x", url);
        for (String language : new String[] {"vi", "en"}) {
            assertTrue(StockAlertMessages.emailBody(language, "Kinetic X", "White", url).contains(url));
        }
        assertEquals(null, StockAlertMessages.productUrl("", "kinetic-x"));
    }

    @Test
    void missingColourNeverPrintsNull() {
        for (String language : new String[] {"vi", "en"}) {
            assertFalse(StockAlertMessages.emailBody(language, "Kinetic X", null, null).contains("null"));
            assertFalse(StockAlertMessages.emailSubject(language, "Kinetic X", " ").contains("()"));
            assertFalse(StockAlertMessages.notificationBody(language, "Kinetic X", null).contains("null"));
        }
    }
}
