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
    void everyStoredStatusHasAName() {
        for (OrderStatus status : OrderStatus.values()) {
            assertFalse(ReportText.code("OrderStatus", status.name()).equals(status.name()), "OrderStatus." + status);
            assertFalse(ReportText.code("OrderStatus", status.name()).equals(status.name()), "OrderStatus." + status);
        }
        for (PaymentStatus status : PaymentStatus.values()) {
            assertFalse(ReportText.code("PaymentStatus", status.name()).equals(status.name()), "PaymentStatus." + status);
        }
        for (PaymentMethod method : PaymentMethod.values()) {
            assertFalse(ReportText.code("PaymentMethod", method.name()).equals(method.name()) && !method.name().equals("VNPAY"), "PaymentMethod." + method);
        }
    }

    @Test
    void aMissingValueIsADashAndAnUnknownCodeIsShownAsItIs() {
        assertEquals("—", ReportText.code("OrderStatus", null));
        assertEquals("—", ReportText.code("OrderStatus", " "));
        assertEquals("ARCHIVED", ReportText.code("OrderStatus", "ARCHIVED"));
        assertEquals("sheet.unknown", ReportText.t("sheet.unknown"));
    }

    @Test
    void everyTextIsFilledIn() {
        for (Map.Entry<String, String[]> entry : ReportText.all().entrySet()) {
            assertFalse(entry.getValue()[0].isBlank(), entry.getKey() + " has no text");
        }
    }

    @Test
    void sheetNamesAreInVietnamese() {
        assertEquals("Tổng quan", ReportText.t("sheet.summary"));
        assertEquals("Bộ lọc", ReportText.t("sheet.filters"));
    }
}
