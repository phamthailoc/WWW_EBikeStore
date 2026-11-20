package com.ebike.service.stock;

/**
 * V22: the words of the back-in-stock notification and email, in the language the customer chose in the profile
 * (user_preferences.language). Anything other than "en" is Vietnamese, the shop's base language.
 */
public final class StockAlertMessages {

    private StockAlertMessages() {
    }

    public static String normalize(String language) {
        return language != null && language.trim().toLowerCase(java.util.Locale.ROOT).startsWith("en") ? "en" : "vi";
    }

    public static String notificationTitle(String language) {
        return "en".equals(normalize(language)) ? "Back in stock" : "Xe đã có hàng lại";
    }

    public static String notificationBody(String language, String productName, String colorName) {
        return "en".equals(normalize(language))
            ? productName + " in " + colorName + " is back in stock. You can order it now."
            : productName + " màu " + colorName + " đã có hàng lại. Bạn có thể đặt mua ngay.";
    }

    public static String emailSubject(String language, String productName, String colorName) {
        return "en".equals(normalize(language))
            ? "Kinetic E-Bike: " + productName + " (" + colorName + ") is back in stock"
            : "Kinetic E-Bike: " + productName + " (" + colorName + ") đã có hàng lại";
    }

    public static String emailBody(String language, String productName, String colorName) {
        if ("en".equals(normalize(language))) {
            return "The " + productName + " in " + colorName + " that you were waiting for is back in stock.\n\n"
                + "Open the bike page in the Kinetic store to order it. Quantities are limited, so order early. "
                + "You are receiving this email because you chose \"Notify me when back in stock\".\n";
        }
        return "Xe " + productName + ", màu " + colorName + " mà bạn đang chờ đã có hàng lại.\n\n"
            + "Vào cửa hàng Kinetic và mở trang xe để đặt mua. Số lượng có hạn, bạn nên đặt sớm. "
            + "Bạn nhận email này vì đã chọn \"Báo tôi khi có hàng\".\n";
    }
}
