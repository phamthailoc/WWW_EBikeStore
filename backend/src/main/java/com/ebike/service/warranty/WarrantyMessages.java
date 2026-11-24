package com.ebike.service.warranty;

import java.util.Locale;

/**
 * V20: the words of the warranty book that the server writes itself (API errors, notifications, emails), in Vietnamese or English.
 * A notification or email uses the language saved in the profile (user_preferences.language). The words of an API error are chosen by
 * com.ebike.common.i18n.RequestLanguage. Anything other than "en" is Vietnamese, the shop's base language.
 */
public final class WarrantyMessages {

    /** Stands in for a status in a notice about a claim whose step did not move but whose note from the store changed. */
    public static final String NOTE_UPDATED = "NOTE";

    private WarrantyMessages() {
    }

    // ---- notification (in-app) ----

    public static String claimTitle() {
        return "Cập nhật yêu cầu bảo hành";
    }

    public static String claimBody(String productName, String frameNumber, String status) {
        return "Yêu cầu bảo hành xe " + productName + " (số khung " + frameNumber + ") " + claimStatus(status) + ".";
    }

    static String claimStatus(String status) {
        if (NOTE_UPDATED.equals(status)) {
            return "có ghi chú mới từ cửa hàng, xem trong sổ bảo hành";
        }
        if ("RECEIVED".equals(status)) {
            return "đã được cửa hàng tiếp nhận";
        }
        if ("REPAIRING".equals(status)) {
            return "đang được xử lý";
        }
        if ("DONE".equals(status)) {
            return "đã hoàn tất";
        }
        if ("REJECTED".equals(status)) {
            return "không được chấp nhận, xem ghi chú của cửa hàng";
        }
        return ("đã chuyển sang trạng thái ") + status;
    }

    public static String maintenanceTitle() {
        return "Đến hạn bảo dưỡng";
    }

    public static String maintenanceBody(String productName, String frameNumber, String milestone) {
        return "Xe " + productName + " (số khung " + frameNumber + ") đã đến mốc bảo dưỡng định kỳ ("
            + MaintenanceMilestones.describe(milestone) + " kể từ ngày giao). Vui lòng mang xe đến cửa hàng.";
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

    public static String maintenanceEmailSubject(String productName) {
        return "Kinetic E-Bike: xe " + productName + " đến hạn bảo dưỡng";
    }

    /** {@code warrantyUrl} is the absolute link to the customer's warranty book; it is left out of the text when blank. */
    public static String maintenanceEmailBody(String productName, String frameNumber, String milestone, String warrantyUrl) {
        String where = warrantyUrl == null || warrantyUrl.isBlank() ? null : warrantyUrl.trim();
        return "Xe " + productName + " (số khung " + frameNumber + ") đã đến mốc bảo dưỡng định kỳ, "
            + MaintenanceMilestones.describe(milestone) + " kể từ ngày giao.\n\n"
            + "Vui lòng mang xe đến cửa hàng Kinetic để được kiểm tra. "
            + (where != null ? "Sổ bảo hành của bạn: " + where : "Xem sổ bảo hành của bạn tại trang Bảo hành trong tài khoản.") + "\n\n"
            + "Bạn nhận email này vì đã mua xe tại Kinetic và đang bật nhận thông báo.\n";
    }

}
