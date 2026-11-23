package com.ebike.service.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** V27: every word of a document exists in both languages, and the holes of a sentence match. */
class DocumentTextTest {

    @Test
    void everyKeyHasBothLanguages() {
        DocumentText.all().forEach((key, pair) -> {
            assertFalse(pair[0].isBlank(), "Vietnamese text of " + key);
            assertFalse(pair[1].isBlank(), "English text of " + key);
        });
    }

    @Test
    void bothLanguagesHaveTheSameHoles() {
        DocumentText.all().forEach((key, pair) -> {
            for (int hole = 0; hole < 4; hole++) {
                String marker = "{" + hole + "}";
                assertEquals(pair[0].contains(marker), pair[1].contains(marker), "hole " + marker + " of " + key);
            }
        });
    }

    @Test
    void theLanguageChoosesTheText() {
        assertEquals("PHIẾU BÁN HÀNG", DocumentText.text("vi", "receipt.title"));
        assertEquals("SALES RECEIPT", DocumentText.text("en", "receipt.title"));
        assertEquals("PHIẾU BÁN HÀNG", DocumentText.text(null, "receipt.title"));
        assertNotEquals(DocumentText.labels("vi").get("card.title"), DocumentText.labels("en").get("card.title"));
    }

    @Test
    void holesAreFilledAndAnUnknownKeyShowsItself() {
        assertEquals("Hello Lan,", DocumentText.text("en", "mail.greeting", "Lan"));
        assertEquals("no.such.key", DocumentText.text("vi", "no.such.key"));
    }

    @Test
    void aValueThatLooksLikeAHoleIsPrintedAsItIsAndNotFilledAgain() {
        // mail.subject has the shop in {0} and the order number in {1}: a shop called "Shop {1}" must not swallow the order number
        String subject = DocumentText.text("en", "mail.subject", "Shop {1}", "ORD-1");
        assertTrue(subject.contains("Shop {1}"), subject);
        assertTrue(subject.contains("ORD-1"), subject);
        assertEquals("a$b\\c", DocumentText.text("en", "mail.greeting", "a$b\\c").replace("Hello ", "").replace(",", ""));
    }
}
