package com.ebike.service.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.ebike.model.entity.order.OrderStatus;
import com.ebike.model.entity.order.PaymentMethod;
import com.ebike.model.entity.order.PaymentStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportTextTest {

    @Test
    void everyTextHasBothLanguages() {
        for (Map.Entry<String, String[]> entry : ReportText.all().entrySet()) {
            assertFalse(entry.getValue()[0].isBlank(), entry.getKey() + " has no Vietnamese text");
            assertFalse(entry.getValue()[1].isBlank(), entry.getKey() + " has no English text");
        }
    }

    @Test
    void everyStoredStatusHasAName() {
        for (OrderStatus status : OrderStatus.values()) {
            assertFalse(ReportText.code("OrderStatus", status.name(), "en").equals(status.name()), "OrderStatus." + status);
            assertFalse(ReportText.code("OrderStatus", status.name(), "vi").equals(status.name()), "OrderStatus." + status);
        }
        for (PaymentStatus status : PaymentStatus.values()) {
            assertFalse(ReportText.code("PaymentStatus", status.name(), "en").equals(status.name()), "PaymentStatus." + status);
        }
        for (PaymentMethod method : PaymentMethod.values()) {
            assertFalse(ReportText.code("PaymentMethod", method.name(), "en").equals(method.name()) && !method.name().equals("VNPAY"), "PaymentMethod." + method);
        }
    }

    @Test
    void aMissingValueIsADashAndAnUnknownCodeIsShownAsItIs() {
        assertEquals("—", ReportText.code("OrderStatus", null, "vi"));
        assertEquals("—", ReportText.code("OrderStatus", " ", "en"));
        assertEquals("ARCHIVED", ReportText.code("OrderStatus", "ARCHIVED", "vi"));
        assertEquals("sheet.unknown", ReportText.t("sheet.unknown", "vi"));
    }

    @Test
    void anythingButEnglishIsVietnamese() {
        assertEquals("vi", ReportText.language(null));
        assertEquals("vi", ReportText.language("fr"));
        assertEquals("en", ReportText.language(" EN-gb"));
        assertEquals("Summary", ReportText.t("sheet.summary", "en"));
        assertEquals("Tổng quan", ReportText.t("sheet.summary", "de"));
    }
}
