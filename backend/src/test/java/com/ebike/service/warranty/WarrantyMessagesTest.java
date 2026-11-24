package com.ebike.service.warranty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** V20: the server's own words come in Vietnamese or English, chosen by the saved profile language. */
class WarrantyMessagesTest {

    @Test
    void claimNoticeIsInVietnamese() {
        assertEquals("Cập nhật yêu cầu bảo hành", WarrantyMessages.claimTitle());
        assertEquals("Yêu cầu bảo hành xe Bike (số khung F1) đã được cửa hàng tiếp nhận.", WarrantyMessages.claimBody("Bike", "F1", "RECEIVED"));
        assertTrue(WarrantyMessages.claimBody("Bike", "F1", "REJECTED").contains("không được chấp nhận"));
        assertTrue(WarrantyMessages.claimBody("Bike", "F1", WarrantyMessages.NOTE_UPDATED).contains("ghi chú mới từ cửa hàng"));
    }

    @Test
    void maintenanceNoticeAndEmail() {
        assertEquals("Đến hạn bảo dưỡng", WarrantyMessages.maintenanceTitle());
        assertTrue(WarrantyMessages.maintenanceBody("Bike", "F1", "P6M").contains("6 tháng kể từ ngày giao"));
        assertEquals("Kinetic E-Bike: xe Bike đến hạn bảo dưỡng", WarrantyMessages.maintenanceEmailSubject("Bike"));
        assertTrue(WarrantyMessages.maintenanceEmailBody("Bike", "F1", "P12M", null).contains("12 tháng kể từ ngày giao"));
        assertTrue(WarrantyMessages.maintenanceEmailBody("Bike", "F1", "P12M", "https://shop.test/customer/warranty").contains("https://shop.test/customer/warranty"));
        assertEquals("https://shop.test/customer/warranty", WarrantyMessages.warrantyUrl("https://shop.test/"));
        assertEquals(null, WarrantyMessages.warrantyUrl(" "));
    }
}
