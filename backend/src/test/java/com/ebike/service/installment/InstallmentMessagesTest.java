package com.ebike.service.installment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** V24: the notification and email words are in the language of the customer's profile, and say what to do next. */
class InstallmentMessagesTest {

    private static final BigDecimal DOWN = new BigDecimal("9000000");
    private static final BigDecimal MONTHLY = new BigDecimal("1925000");
    private static final OffsetDateTime DEADLINE = OffsetDateTime.of(2026, 11, 25, 9, 30, 0, 0, ZoneOffset.ofHours(7));

    @Test
    void anApprovalWithoutADeadlineLeavesTheDeadlineSentenceOut() {
        String text = InstallmentMessages.body(InstallmentMessages.APPROVED, "ORD-10", DOWN, MONTHLY, 6, null, null);
        assertFalse(text.contains("Vui lòng thanh toán trước"));
    }

    @Test
    void moneyUsesTheDongSignAndDotGrouping() {
        assertEquals("9.000.000 ₫", InstallmentMessages.money(DOWN));
        assertEquals("0 ₫", InstallmentMessages.money(null));
    }

    @Test
    void theTitleNamesTheVerdict() {
        assertEquals("Hồ sơ trả góp đã được duyệt", InstallmentMessages.title(InstallmentMessages.APPROVED));
        assertEquals("Hồ sơ trả góp bị từ chối", InstallmentMessages.title(InstallmentMessages.REJECTED));
    }

    @Test
    void anApprovalSaysHowMuchToPayWhereAndByWhen() {
        String text = InstallmentMessages.body(InstallmentMessages.APPROVED, "ORD-10", DOWN, MONTHLY, 12, DEADLINE, "Mang theo CCCD");
        assertTrue(text.contains("ORD-10"));
        assertTrue(text.contains("9.000.000 ₫"));
        assertTrue(text.contains("09:30 25/11/2026 (GMT+7)"), "the offset is part of the deadline");
        assertTrue(text.contains("1.925.000 ₫"));
        assertTrue(text.contains("12 tháng"));
        assertTrue(text.contains("Mang theo CCCD"));
        assertTrue(text.contains("mô phỏng"));
    }

    @Test
    void aRejectionGivesTheReasonAndSaysTheOrderWasCancelled() {
        String text = InstallmentMessages.body(InstallmentMessages.REJECTED, "ORD-10", DOWN, MONTHLY, 12, null, "Thu nhập chưa đủ");
        assertTrue(text.contains("bị từ chối"));
        assertTrue(text.contains("Lý do: Thu nhập chưa đủ"));
        assertTrue(text.contains("Đơn hàng đã được hủy"));
    }

    @Test
    void theEmailLinksToTheOrderOnlyWhenTheShopHasAPublicAddress() {
        assertEquals("https://shop.example/customer/orders/10", InstallmentMessages.orderUrl("https://shop.example/", 10L));
        assertEquals("https://shop.example/customer/orders/10", InstallmentMessages.orderUrl("https://shop.example", 10L));
        assertNull(InstallmentMessages.orderUrl("", 10L));
        assertNull(InstallmentMessages.orderUrl(null, 10L));
        assertNull(InstallmentMessages.orderUrl("https://shop.example", null));
        String withLink = InstallmentMessages.emailBody(InstallmentMessages.APPROVED, "ORD-10", DOWN, MONTHLY, 12, DEADLINE, null, "https://shop.example/customer/orders/10");
        assertTrue(withLink.contains("Đơn hàng của bạn: https://shop.example/customer/orders/10"));
        String withoutLink = InstallmentMessages.emailBody(InstallmentMessages.REJECTED, "ORD-10", DOWN, MONTHLY, 12, null, "Hết xe mẫu", null);
        assertFalse(withoutLink.contains("http"));
        assertTrue(withoutLink.contains("Xin chào"));
        assertTrue(withoutLink.contains("mô phỏng"));
    }

    @Test
    void theDownPaymentNoticeDoesNotOfferAnotherPaymentMethod() {
        assertEquals("Đã nhận khoản trả trước", InstallmentMessages.downPaymentTitle(true));
        assertEquals("Thanh toán khoản trả trước thất bại", InstallmentMessages.downPaymentTitle(false));
        String failed = InstallmentMessages.downPaymentBody(false, "ORD-10");
        assertTrue(failed.contains("ORD-10") && failed.contains("tại cửa hàng"));
        assertFalse(failed.contains("hình thức khác"));
    }

    @Test
    void theEmailSubjectNamesTheOrderAndTheVerdict() {
        assertEquals("Kinetic E-Bike: hồ sơ trả góp đơn ORD-10 đã được duyệt", InstallmentMessages.emailSubject(InstallmentMessages.APPROVED, "ORD-10"));
        assertTrue(InstallmentMessages.emailSubject(InstallmentMessages.REJECTED, "ORD-10").contains("ORD-10"));
    }
}
