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

    @Test
    void claimNoticeFollowsTheProfileLanguage() {
        assertEquals("Warranty request update", WarrantyMessages.claimTitle("en"));
        assertEquals("Cập nhật yêu cầu bảo hành", WarrantyMessages.claimTitle("vi"));
        assertEquals("Your warranty request for Bike (frame number F1) was received by the store.",
            WarrantyMessages.claimBody("en", "Bike", "F1", "RECEIVED"));
        assertEquals("Yêu cầu bảo hành xe Bike (số khung F1) đã được cửa hàng tiếp nhận.",
            WarrantyMessages.claimBody("vi", "Bike", "F1", "RECEIVED"));
        assertTrue(WarrantyMessages.claimBody("en", "Bike", "F1", "REJECTED").contains("not accepted"));
    }

    @Test
    void maintenanceNoticeAndEmailFollowTheProfileLanguage() {
        assertEquals("Service due", WarrantyMessages.maintenanceTitle("en"));
        assertTrue(WarrantyMessages.maintenanceBody("en", "Bike", "F1", "P6M").contains("6 months after delivery"));
        assertTrue(WarrantyMessages.maintenanceBody("vi", "Bike", "F1", "P6M").contains("6 tháng kể từ ngày giao"));
        assertTrue(WarrantyMessages.maintenanceBody("en", "Bike", "F1", "P1M").contains("1 month after"));
        assertEquals("Kinetic E-Bike: your Bike is due for service", WarrantyMessages.maintenanceEmailSubject("en", "Bike"));
        assertEquals("Kinetic E-Bike: xe Bike đến hạn bảo dưỡng", WarrantyMessages.maintenanceEmailSubject("vi", "Bike"));
        assertTrue(WarrantyMessages.maintenanceEmailBody("en", "Bike", "F1", "P12M", null).contains("12 months after delivery"));
        assertTrue(WarrantyMessages.maintenanceEmailBody("vi", "Bike", "F1", "P12M", null).contains("12 tháng kể từ ngày giao"));
        assertTrue(WarrantyMessages.maintenanceEmailBody("en", "Bike", "F1", "P12M", "https://shop.test/customer/warranty").contains("https://shop.test/customer/warranty"));
        assertEquals("https://shop.test/customer/warranty", WarrantyMessages.warrantyUrl("https://shop.test/"));
        assertEquals(null, WarrantyMessages.warrantyUrl(" "));
    }
}
