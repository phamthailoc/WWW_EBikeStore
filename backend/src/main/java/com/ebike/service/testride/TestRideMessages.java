package com.ebike.service.testride;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * V23: the words of the test-ride booking that the server writes itself (notifications and emails), in Vietnamese or English.
 * A notification or email uses the language saved in the profile (user_preferences.language); anything other than "en" is
 * Vietnamese, the shop's base language. The words of an API error are chosen by com.ebike.common.i18n.RequestLanguage.
 */
public final class TestRideMessages {

    /** What happened to a booking, as the customer is told. */
    public static final String CONFIRMED = "CONFIRMED";
    /** The staff cancelled it (the customer's own cancellation tells nobody). */
    public static final String CANCELLED = "CANCELLED";
    public static final String NO_SHOW = "NO_SHOW";
    /** Nobody confirmed the request before its time came. */
    public static final String EXPIRED = "EXPIRED";
    public static final String REMINDER = "REMINDER";
    /** The store changed only the note of a confirmed ride (arrival time, where to ask...): in-app only. */
    public static final String NOTE_UPDATED = "NOTE_UPDATED";

    private static final DateTimeFormatter VI = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy", Locale.ROOT);
    private static final DateTimeFormatter EN = DateTimeFormatter.ofPattern("dd/MM/yyyy 'at' HH:mm", Locale.ENGLISH);

    private TestRideMessages() {
    }

    public static String normalize(String language) {
        return language != null && language.trim().toLowerCase(Locale.ROOT).startsWith("en") ? "en" : "vi";
    }

    /** The slot as one phrase: "09:00 22/11/2026" in Vietnamese, "22/11/2026 at 09:00" in English. */
    public static String when(String language, LocalDateTime slotStart) {
        return slotStart.format(isEnglish(language) ? EN : VI);
    }

    // ---- notification (in-app) ----

    public static String title(String language, String kind) {
        boolean english = isEnglish(language);
        return switch (kind) {
            case CONFIRMED -> english ? "Test ride confirmed" : "Lịch lái thử đã được xác nhận";
            case CANCELLED -> english ? "Test ride cancelled" : "Lịch lái thử đã bị hủy";
            case NO_SHOW -> english ? "Test ride missed" : "Lịch lái thử đã bỏ lỡ";
            case EXPIRED -> english ? "Test ride request expired" : "Yêu cầu lái thử đã hết hạn";
            case NOTE_UPDATED -> english ? "New note on your test ride" : "Lịch lái thử có ghi chú mới";
            default -> english ? "Test ride reminder" : "Nhắc lịch lái thử";
        };
    }

    /** The showroom as one phrase: its name, then its address when there is one. */
    public static String place(String showroomName, String address) {
        return address == null || address.isBlank() ? showroomName : showroomName + ", " + address.trim();
    }

    /** {@code place} is the showroom as {@link #place(String, String)} writes it, so the customer reads where to go and not only a name. */
    public static String body(String language, String kind, String productName, String place, LocalDateTime slotStart, String note) {
        boolean english = isEnglish(language);
        String bike = productName + " - " + place + ", " + when(language, slotStart);
        String reason = note == null || note.isBlank() ? "" : (english ? " Note from the store: " : " Ghi chú của cửa hàng: ") + note.trim();
        return switch (kind) {
            case CONFIRMED -> english
                ? "Your test ride is confirmed: " + bike + ". Please bring your ID." + reason
                : "Lịch lái thử của bạn đã được xác nhận: " + bike + ". Vui lòng mang theo giấy tờ tùy thân." + reason;
            case CANCELLED -> english
                ? "The store had to cancel your test ride: " + bike + "." + reason + " You can book another time."
                : "Cửa hàng phải hủy lịch lái thử của bạn: " + bike + "." + reason + " Bạn có thể đặt lại khung giờ khác.";
            case NO_SHOW -> english
                ? "You did not come to your test ride: " + bike + ". You can book another time."
                : "Bạn đã không đến buổi lái thử: " + bike + ". Bạn có thể đặt lại khung giờ khác.";
            case EXPIRED -> english
                ? "Your test ride request was not confirmed in time and has been closed: " + bike + ". You can book another time."
                : "Yêu cầu lái thử chưa được xác nhận kịp và đã được đóng: " + bike + ". Bạn có thể đặt lại khung giờ khác.";
            case NOTE_UPDATED -> english
                ? "The store updated the note on your test ride: " + bike + "." + reason
                : "Cửa hàng vừa cập nhật ghi chú cho lịch lái thử của bạn: " + bike + "." + reason;
            default -> english
                ? "Your test ride is coming up: " + bike + ". Please bring your ID. If you cannot come, cancel it so another customer can have the place."
                : "Sắp đến lịch lái thử của bạn: " + bike + ". Vui lòng mang theo giấy tờ tùy thân. Nếu không đến được, hãy hủy lịch để nhường chỗ cho khách khác.";
        };
    }

    // ---- email ----

    /** The absolute link to the customer's bookings, or null when no public address of the shop is configured. */
    public static String bookingsUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String base = baseUrl.trim();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/customer/test-rides";
    }

    /** Whether a kind is also sent by email: confirmations, cancellations by the store and the reminder (the others are only in-app). */
    public static boolean sendsEmail(String kind) {
        return CONFIRMED.equals(kind) || CANCELLED.equals(kind) || REMINDER.equals(kind);
    }

    public static String emailSubject(String language, String kind, String productName) {
        boolean english = isEnglish(language);
        return switch (kind) {
            case CONFIRMED -> english ? "Kinetic E-Bike: your test ride of " + productName + " is confirmed" : "Kinetic E-Bike: đã xác nhận lịch lái thử " + productName;
            case CANCELLED -> english ? "Kinetic E-Bike: your test ride of " + productName + " was cancelled" : "Kinetic E-Bike: lịch lái thử " + productName + " đã bị hủy";
            default -> english ? "Kinetic E-Bike: reminder, test ride of " + productName : "Kinetic E-Bike: nhắc lịch lái thử " + productName;
        };
    }

    /** {@code address} and {@code bookingsUrl} are left out of the text when blank. */
    public static String emailBody(String language, String kind, String productName, String showroomName, String address,
                                   LocalDateTime slotStart, String note, String bookingsUrl) {
        boolean english = isEnglish(language);
        String where = place(showroomName, address);
        String link = bookingsUrl == null || bookingsUrl.isBlank() ? null : bookingsUrl.trim();
        StringBuilder text = new StringBuilder();
        text.append(body(language, kind, productName, where, slotStart, note)).append("\n\n");
        if (link != null) {
            text.append(english ? "Your test rides: " : "Lịch lái thử của bạn: ").append(link).append("\n\n");
        }
        text.append(english
            ? "You are receiving this email because you booked a test ride at Kinetic.\n"
            : "Bạn nhận email này vì đã đặt lịch lái thử tại Kinetic.\n");
        return text.toString();
    }

    private static boolean isEnglish(String language) {
        return "en".equals(normalize(language));
    }
}
