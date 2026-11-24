package com.ebike.service.installment;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * V24: the words of the instalment decision that the server writes itself (notifications and emails), in Vietnamese or English.
 * A notification or email uses the language saved in the profile (user_preferences.language); anything other than "en" is Vietnamese,
 * the shop's base language. The words of an API error are chosen by com.ebike.common.i18n.RequestLanguage.
 */
public final class InstallmentMessages {

    /** The staff approved the application: the customer now pays the down payment. */
    public static final String APPROVED = "APPROVED";
    /** The staff rejected it (and the order was cancelled with it). */
    public static final String REJECTED = "REJECTED";

    // the time is shown with its offset ("GMT+7"): the server may run in another zone than the shop, and a deadline must not be read 7 hours off
    private static final DateTimeFormatter VI = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy (O)", Locale.ENGLISH);

    private InstallmentMessages() {
    }

    /** "12.345.678 ₫" in Vietnamese, "12,345,678 VND" in English. */
    public static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
        String digits = format.format(amount == null ? BigDecimal.ZERO : amount);
        return digits + " ₫";
    }

    /** A moment with its offset, in the shop's zone: "09:30 25/11/2026 (GMT+7)" or "25/11/2026 at 09:30 (GMT+7)". */
    public static String when(OffsetDateTime time) {
        return time.format(VI);
    }

    // ---- notification (in-app) ----

    public static String title(String kind) {
        return APPROVED.equals(kind)
            ? ("Hồ sơ trả góp đã được duyệt")
            : ("Hồ sơ trả góp bị từ chối");
    }

    /** {@code payBefore} and {@code note} are left out of the text when null or blank. */
    public static String body(String kind, String orderNumber, BigDecimal downPayment, BigDecimal monthlyPayment,
                              int termMonths, OffsetDateTime payBefore, String note) {
        String reason = note == null || note.isBlank() ? "" : (" Lý do: ") + note.trim();
        if (APPROVED.equals(kind)) {
            String deadline = payBefore == null ? "" : (" Vui lòng thanh toán trước ") + when(payBefore)
                + (", sau thời hạn đơn sẽ bị hủy.");
            String plan = (" Sau đó bạn trả " + money(monthlyPayment) + " mỗi tháng trong " + termMonths + " tháng (mô phỏng, chưa kết nối công ty tài chính).");
            String note2 = note == null || note.isBlank() ? "" : (" Ghi chú của cửa hàng: ") + note.trim();
            return ("Hồ sơ trả góp của đơn " + orderNumber + " đã được duyệt. Khoản trả trước là " + money(downPayment)
                    + ", thanh toán qua VNPay ở trang đơn hàng hoặc tại cửa hàng." + deadline + plan + note2);
        }
        return "Hồ sơ trả góp của đơn " + orderNumber + " bị từ chối." + reason
                + " Đơn hàng đã được hủy. Bạn có thể đặt lại và chọn hình thức thanh toán khác.";
    }

    // ---- the payment of the down payment (in-app notification) ----

    public static String downPaymentTitle(boolean paid) {
        return paid
            ? ("Đã nhận khoản trả trước")
            : ("Thanh toán khoản trả trước thất bại");
    }

    public static String downPaymentBody(boolean paid, String orderNumber) {
        if (paid) {
            return "Khoản trả trước của đơn " + orderNumber + " đã được thanh toán. Cửa hàng sẽ xử lý đơn hàng của bạn.";
        }
        return "Khoản trả trước của đơn " + orderNumber + " chưa thanh toán được. Vui lòng thử lại bằng VNPay ở trang đơn hàng hoặc thanh toán tại cửa hàng.";
    }

    // ---- email ----

    /** The absolute link to the customer's order, or null when no public address of the shop is configured. */
    public static String orderUrl(String baseUrl, Long orderId) {
        if (baseUrl == null || baseUrl.isBlank() || orderId == null) {
            return null;
        }
        String base = baseUrl.trim();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/customer/orders/" + orderId;
    }

    public static String emailSubject(String kind, String orderNumber) {
        return APPROVED.equals(kind)
            ? ("Kinetic E-Bike: hồ sơ trả góp đơn " + orderNumber + " đã được duyệt")
            : ("Kinetic E-Bike: hồ sơ trả góp đơn " + orderNumber + " bị từ chối");
    }

    /** {@code orderUrl} is left out of the text when blank. */
    public static String emailBody(String kind, String orderNumber, BigDecimal downPayment, BigDecimal monthlyPayment,
                                   int termMonths, OffsetDateTime payBefore, String note, String orderUrl) {
        StringBuilder text = new StringBuilder();
        text.append("Xin chào,\n\n");
        text.append(body(kind, orderNumber, downPayment, monthlyPayment, termMonths, payBefore, note)).append("\n\n");
        if (orderUrl != null && !orderUrl.isBlank()) {
            text.append("Đơn hàng của bạn: ").append(orderUrl.trim()).append("\n\n");
        }
        text.append("Đây là gói trả góp mô phỏng, chưa phát sinh khoản vay thật.\nKinetic E-Bike");
        return text.toString();
    }
}
