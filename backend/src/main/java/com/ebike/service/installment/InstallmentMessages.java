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
    private static final DateTimeFormatter EN = DateTimeFormatter.ofPattern("dd/MM/yyyy 'at' HH:mm (O)", Locale.ENGLISH);

    private InstallmentMessages() {
    }

    public static String normalize(String language) {
        return language != null && language.trim().toLowerCase(Locale.ROOT).startsWith("en") ? "en" : "vi";
    }

    private static boolean isEnglish(String language) {
        return "en".equals(normalize(language));
    }

    /** "12.345.678 ₫" in Vietnamese, "12,345,678 VND" in English. */
    public static String money(String language, BigDecimal amount) {
        boolean english = isEnglish(language);
        NumberFormat format = NumberFormat.getIntegerInstance(english ? Locale.ENGLISH : Locale.forLanguageTag("vi-VN"));
        String digits = format.format(amount == null ? BigDecimal.ZERO : amount);
        return english ? digits + " VND" : digits + " ₫";
    }

    /** A moment with its offset, in the shop's zone: "09:30 25/11/2026 (GMT+7)" or "25/11/2026 at 09:30 (GMT+7)". */
    public static String when(String language, OffsetDateTime time) {
        return time.format(isEnglish(language) ? EN : VI);
    }

    // ---- notification (in-app) ----

    public static String title(String language, String kind) {
        boolean english = isEnglish(language);
        return APPROVED.equals(kind)
            ? (english ? "Instalment application approved" : "Hồ sơ trả góp đã được duyệt")
            : (english ? "Instalment application rejected" : "Hồ sơ trả góp bị từ chối");
    }

    /** {@code payBefore} and {@code note} are left out of the text when null or blank. */
    public static String body(String language, String kind, String orderNumber, BigDecimal downPayment, BigDecimal monthlyPayment,
                              int termMonths, OffsetDateTime payBefore, String note) {
        boolean english = isEnglish(language);
        String reason = note == null || note.isBlank() ? "" : (english ? " Reason: " : " Lý do: ") + note.trim();
        if (APPROVED.equals(kind)) {
            String deadline = payBefore == null ? "" : (english ? " Please pay it before " : " Vui lòng thanh toán trước ") + when(language, payBefore)
                + (english ? ", or the order is cancelled." : ", sau thời hạn đơn sẽ bị hủy.");
            String plan = english
                ? " Then you pay " + money(language, monthlyPayment) + " a month for " + termMonths + " months (simulation: no finance company is connected)."
                : " Sau đó bạn trả " + money(language, monthlyPayment) + " mỗi tháng trong " + termMonths + " tháng (mô phỏng, chưa kết nối công ty tài chính).";
            String note2 = note == null || note.isBlank() ? "" : (english ? " Note from the store: " : " Ghi chú của cửa hàng: ") + note.trim();
            return english
                ? "Your instalment application for order " + orderNumber + " was approved. The down payment is " + money(language, downPayment)
                    + ", payable through VNPay on the order page or at the store." + deadline + plan + note2
                : "Hồ sơ trả góp của đơn " + orderNumber + " đã được duyệt. Khoản trả trước là " + money(language, downPayment)
                    + ", thanh toán qua VNPay ở trang đơn hàng hoặc tại cửa hàng." + deadline + plan + note2;
        }
        return english
            ? "Your instalment application for order " + orderNumber + " was rejected." + reason
                + " The order has been cancelled. You can order again and choose another payment method."
            : "Hồ sơ trả góp của đơn " + orderNumber + " bị từ chối." + reason
                + " Đơn hàng đã được hủy. Bạn có thể đặt lại và chọn hình thức thanh toán khác.";
    }

    // ---- the payment of the down payment (in-app notification) ----

    public static String downPaymentTitle(String language, boolean paid) {
        boolean english = isEnglish(language);
        return paid
            ? (english ? "Down payment received" : "Đã nhận khoản trả trước")
            : (english ? "Down payment not paid" : "Thanh toán khoản trả trước thất bại");
    }

    public static String downPaymentBody(String language, boolean paid, String orderNumber) {
        boolean english = isEnglish(language);
        if (paid) {
            return english
                ? "The down payment of order " + orderNumber + " was received. The store will now process your order."
                : "Khoản trả trước của đơn " + orderNumber + " đã được thanh toán. Cửa hàng sẽ xử lý đơn hàng của bạn.";
        }
        return english
            ? "The down payment of order " + orderNumber + " did not go through. Please try VNPay again on the order page, or pay it at the store."
            : "Khoản trả trước của đơn " + orderNumber + " chưa thanh toán được. Vui lòng thử lại bằng VNPay ở trang đơn hàng hoặc thanh toán tại cửa hàng.";
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

    public static String emailSubject(String language, String kind, String orderNumber) {
        boolean english = isEnglish(language);
        return APPROVED.equals(kind)
            ? (english ? "Kinetic E-Bike: your instalment application for " + orderNumber + " is approved" : "Kinetic E-Bike: hồ sơ trả góp đơn " + orderNumber + " đã được duyệt")
            : (english ? "Kinetic E-Bike: your instalment application for " + orderNumber + " was rejected" : "Kinetic E-Bike: hồ sơ trả góp đơn " + orderNumber + " bị từ chối");
    }

    /** {@code orderUrl} is left out of the text when blank. */
    public static String emailBody(String language, String kind, String orderNumber, BigDecimal downPayment, BigDecimal monthlyPayment,
                                   int termMonths, OffsetDateTime payBefore, String note, String orderUrl) {
        boolean english = isEnglish(language);
        StringBuilder text = new StringBuilder();
        text.append(english ? "Hello,\n\n" : "Xin chào,\n\n");
        text.append(body(language, kind, orderNumber, downPayment, monthlyPayment, termMonths, payBefore, note)).append("\n\n");
        if (orderUrl != null && !orderUrl.isBlank()) {
            text.append(english ? "Your order: " : "Đơn hàng của bạn: ").append(orderUrl.trim()).append("\n\n");
        }
        text.append(english
            ? "This is a simulated instalment plan: no real loan is made.\nKinetic E-Bike"
            : "Đây là gói trả góp mô phỏng, chưa phát sinh khoản vay thật.\nKinetic E-Bike");
        return text.toString();
    }
}
