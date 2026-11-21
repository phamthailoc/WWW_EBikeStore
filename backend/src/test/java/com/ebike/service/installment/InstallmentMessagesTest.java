package com.ebike.service.installment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** V24: the notification and email words are in the language of the customer's profile, and say what to do next. */
class InstallmentMessagesTest {

    private static final BigDecimal DOWN = new BigDecimal("9000000");
    private static final BigDecimal MONTHLY = new BigDecimal("1925000");
    private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 11, 25, 9, 30);

    @Test
    void anythingButEnglishIsVietnamese() {
        assertEquals("en", InstallmentMessages.normalize("en"));
        assertEquals("en", InstallmentMessages.normalize(" EN-GB "));
        assertEquals("vi", InstallmentMessages.normalize("vi"));
        assertEquals("vi", InstallmentMessages.normalize(null));
        assertEquals("vi", InstallmentMessages.normalize("fr"));
    }

    @Test
    void moneyUsesTheLanguagesOwnGroupingAndUnit() {
        assertEquals("9.000.000 ₫", InstallmentMessages.money("vi", DOWN));
        assertEquals("9,000,000 VND", InstallmentMessages.money("en", DOWN));
        assertEquals("0 ₫", InstallmentMessages.money("vi", null));
    }

    @Test
    void theTitleNamesTheVerdictInBothLanguages() {
        assertEquals("Hồ sơ trả góp đã được duyệt", InstallmentMessages.title("vi", InstallmentMessages.APPROVED));
        assertEquals("Instalment application approved", InstallmentMessages.title("en", InstallmentMessages.APPROVED));
        assertEquals("Hồ sơ trả góp bị từ chối", InstallmentMessages.title("vi", InstallmentMessages.REJECTED));
        assertEquals("Instalment application rejected", InstallmentMessages.title("en", InstallmentMessages.REJECTED));
    }

    @Test
    void anApprovalSaysHowMuchToPayWhereAndByWhen() {
        String vi = InstallmentMessages.body("vi", InstallmentMessages.APPROVED, "ORD-10", DOWN, MONTHLY, 12, DEADLINE, "Mang theo CCCD");
        assertTrue(vi.contains("ORD-10"));
        assertTrue(vi.contains("9.000.000 ₫"));
        assertTrue(vi.contains("09:30 25/11/2026"));
        assertTrue(vi.contains("1.925.000 ₫"));
        assertTrue(vi.contains("12 tháng"));
        assertTrue(vi.contains("Mang theo CCCD"));
        assertTrue(vi.contains("mô phỏng"));
        String en = InstallmentMessages.body("en", InstallmentMessages.APPROVED, "ORD-10", DOWN, MONTHLY, 12, DEADLINE, null);
        assertTrue(en.contains("9,000,000 VND"));
        assertTrue(en.contains("25/11/2026 at 09:30"));
        assertTrue(en.contains("1,925,000 VND a month for 12 months"));
        assertTrue(en.contains("simulation"));
        assertFalse(en.contains("Note from the store"));
    }

    @Test
    void anApprovalWithoutADeadlineLeavesTheDeadlineSentenceOut() {
        String en = InstallmentMessages.body("en", InstallmentMessages.APPROVED, "ORD-10", DOWN, MONTHLY, 6, null, null);
        assertFalse(en.contains("Please pay it before"));
    }

    @Test
    void aRejectionGivesTheReasonAndSaysTheOrderWasCancelled() {
        String vi = InstallmentMessages.body("vi", InstallmentMessages.REJECTED, "ORD-10", DOWN, MONTHLY, 12, null, "Thu nhập chưa đủ");
        assertTrue(vi.contains("bị từ chối"));
        assertTrue(vi.contains("Lý do: Thu nhập chưa đủ"));
        assertTrue(vi.contains("Đơn hàng đã được hủy"));
        String en = InstallmentMessages.body("en", InstallmentMessages.REJECTED, "ORD-10", DOWN, MONTHLY, 12, null, "Income too low");
        assertTrue(en.contains("was rejected"));
        assertTrue(en.contains("Reason: Income too low"));
        assertTrue(en.contains("has been cancelled"));
    }

    @Test
    void theEmailLinksToTheOrderOnlyWhenTheShopHasAPublicAddress() {
        assertEquals("https://shop.example/customer/orders/10", InstallmentMessages.orderUrl("https://shop.example/", 10L));
        assertEquals("https://shop.example/customer/orders/10", InstallmentMessages.orderUrl("https://shop.example", 10L));
        assertNull(InstallmentMessages.orderUrl("", 10L));
        assertNull(InstallmentMessages.orderUrl(null, 10L));
        assertNull(InstallmentMessages.orderUrl("https://shop.example", null));
        String withLink = InstallmentMessages.emailBody("en", InstallmentMessages.APPROVED, "ORD-10", DOWN, MONTHLY, 12, DEADLINE, null, "https://shop.example/customer/orders/10");
        assertTrue(withLink.contains("Your order: https://shop.example/customer/orders/10"));
        String withoutLink = InstallmentMessages.emailBody("vi", InstallmentMessages.REJECTED, "ORD-10", DOWN, MONTHLY, 12, null, "Hết xe mẫu", null);
        assertFalse(withoutLink.contains("http"));
        assertTrue(withoutLink.contains("Xin chào"));
        assertTrue(withoutLink.contains("mô phỏng"));
    }

    @Test
    void theDownPaymentNoticeIsInBothLanguagesAndDoesNotOfferAnotherPaymentMethod() {
        assertEquals("Đã nhận khoản trả trước", InstallmentMessages.downPaymentTitle("vi", true));
        assertEquals("Down payment received", InstallmentMessages.downPaymentTitle("en", true));
        assertEquals("Down payment not paid", InstallmentMessages.downPaymentTitle("en", false));
        String failedEn = InstallmentMessages.downPaymentBody("en", false, "ORD-10");
        String failedVi = InstallmentMessages.downPaymentBody("vi", false, "ORD-10");
        assertTrue(failedEn.contains("ORD-10") && failedEn.contains("at the store"));
        assertTrue(failedVi.contains("ORD-10") && failedVi.contains("tại cửa hàng"));
        assertFalse(failedVi.contains("hình thức khác"));
        assertTrue(InstallmentMessages.downPaymentBody("en", true, "ORD-10").contains("was received"));
    }

    @Test
    void theEmailSubjectNamesTheOrderAndTheVerdictInBothLanguages() {
        assertEquals("Kinetic E-Bike: hồ sơ trả góp đơn ORD-10 đã được duyệt", InstallmentMessages.emailSubject("vi", InstallmentMessages.APPROVED, "ORD-10"));
        assertEquals("Kinetic E-Bike: your instalment application for ORD-10 was rejected", InstallmentMessages.emailSubject("en", InstallmentMessages.REJECTED, "ORD-10"));
    }
}
