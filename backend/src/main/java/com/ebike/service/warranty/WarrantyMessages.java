package com.ebike.service.warranty;

import java.util.Locale;

/**
 * V20: the words of the warranty book that the server writes itself (API errors, notifications, emails), in Vietnamese or English.
 * A notification or email uses the language saved in the profile (user_preferences.language). The words of an API error are chosen by
 * com.ebike.common.i18n.RequestLanguage. Anything other than "en" is Vietnamese, the shop's base language.
 */
public final class WarrantyMessages {

    private WarrantyMessages() {
    }

    public static String normalize(String language) {
        return language != null && language.trim().toLowerCase(Locale.ROOT).startsWith("en") ? "en" : "vi";
    }

    // ---- notification (in-app) ----

    public static String claimTitle(String language) {
        return isEnglish(language) ? "Warranty request update" : "Cập nhật yêu cầu bảo hành";
    }

    public static String claimBody(String language, String productName, String frameNumber, String status) {
        if (isEnglish(language)) {
            return "Your warranty request for " + productName + " (frame number " + frameNumber + ") " + claimStatus(language, status) + ".";
        }
        return "Yêu cầu bảo hành xe " + productName + " (số khung " + frameNumber + ") " + claimStatus(language, status) + ".";
    }

    static String claimStatus(String language, String status) {
        boolean english = isEnglish(language);
        if ("RECEIVED".equals(status)) {
            return english ? "was received by the store" : "đã được cửa hàng tiếp nhận";
        }
        if ("REPAIRING".equals(status)) {
            return english ? "is being worked on" : "đang được xử lý";
        }
        if ("DONE".equals(status)) {
            return english ? "is completed" : "đã hoàn tất";
        }
        if ("REJECTED".equals(status)) {
            return english ? "was not accepted, see the store's note" : "không được chấp nhận, xem ghi chú của cửa hàng";
        }
        return (english ? "moved to status " : "đã chuyển sang trạng thái ") + status;
    }

    public static String maintenanceTitle(String language) {
        return isEnglish(language) ? "Service due" : "Đến hạn bảo dưỡng";
    }

    public static String maintenanceBody(String language, String productName, String frameNumber, String milestone) {
        if (isEnglish(language)) {
            return "Your " + productName + " (frame number " + frameNumber + ") has reached its regular service ("
                + MaintenanceMilestones.describe(milestone, language) + " after delivery). Please bring it to the store.";
        }
        return "Xe " + productName + " (số khung " + frameNumber + ") đã đến mốc bảo dưỡng định kỳ ("
            + MaintenanceMilestones.describe(milestone, language) + " kể từ ngày giao). Vui lòng mang xe đến cửa hàng.";
    }

    // ---- email ----

    /** The absolute link to the customer's warranty book, or null when no public address of the shop is configured. */
    public static String warrantyUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String base = baseUrl.trim();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/customer/warranty";
    }

    public static String maintenanceEmailSubject(String language, String productName) {
        return isEnglish(language)
            ? "Kinetic E-Bike: your " + productName + " is due for service"
            : "Kinetic E-Bike: xe " + productName + " đến hạn bảo dưỡng";
    }

    /** {@code warrantyUrl} is the absolute link to the customer's warranty book; it is left out of the text when blank. */
    public static String maintenanceEmailBody(String language, String productName, String frameNumber, String milestone, String warrantyUrl) {
        String where = warrantyUrl == null || warrantyUrl.isBlank() ? null : warrantyUrl.trim();
        if (isEnglish(language)) {
            return "Your " + productName + " (frame number " + frameNumber + ") has reached its regular service, "
                + MaintenanceMilestones.describe(milestone, language) + " after delivery.\n\n"
                + "Please bring it to a Kinetic store for a check. "
                + (where != null ? "Your warranty book: " + where : "You can see your warranty book on the Warranty page of your account.") + "\n\n"
                + "You are receiving this email because you bought a bike at Kinetic and have notifications turned on.\n";
        }
        return "Xe " + productName + " (số khung " + frameNumber + ") đã đến mốc bảo dưỡng định kỳ, "
            + MaintenanceMilestones.describe(milestone, language) + " kể từ ngày giao.\n\n"
            + "Vui lòng mang xe đến cửa hàng Kinetic để được kiểm tra. "
            + (where != null ? "Sổ bảo hành của bạn: " + where : "Xem sổ bảo hành của bạn tại trang Bảo hành trong tài khoản.") + "\n\n"
            + "Bạn nhận email này vì đã mua xe tại Kinetic và đang bật nhận thông báo.\n";
    }

    private static boolean isEnglish(String language) {
        return "en".equals(normalize(language));
    }
}
