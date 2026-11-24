package com.ebike.service.warranty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** V20: the server's own words come in Vietnamese or English, chosen by the saved profile language. */
class WarrantyMessagesTest {

    @Test
    void anythingButEnglishIsVietnamese() {
        assertEquals("en", WarrantyMessages.normalize("en"));
        assertEquals("en", WarrantyMessages.normalize(" EN-us "));
        assertEquals("vi", WarrantyMessages.normalize("vi"));
        assertEquals("vi", WarrantyMessages.normalize(null));
        assertEquals("vi", WarrantyMessages.normalize("fr"));
    }

}
