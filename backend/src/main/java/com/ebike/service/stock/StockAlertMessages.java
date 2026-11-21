package com.ebike.service.stock;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * V22: the words of the back-in-stock notification and email, in the language the customer chose in the profile
 * (user_preferences.language). Anything other than "en" is Vietnamese, the shop's base language.
 */
public final class StockAlertMessages {

    private StockAlertMessages() {
    }

    public static String normalize(String language) {
        return language != null && language.trim().toLowerCase(Locale.ROOT).startsWith("en") ? "en" : "vi";
    }

    public static String notificationTitle(String language) {
        return "en".equals(normalize(language)) ? "Back in stock" : "Xe đã có hàng lại";
    }

    public static String notificationBody(String language, String productName, String colorName) {
        return "en".equals(normalize(language))
            ? productName + " in " + colour(language, colorName) + " is back in stock. You can order it now."
            : productName + " " + colour(language, colorName) + " đã có hàng lại. Bạn có thể đặt mua ngay.";
    }

    public static String emailSubject(String language, String productName, String colorName) {
        boolean named = hasText(colorName);
        return "en".equals(normalize(language))
            ? "Kinetic E-Bike: " + productName + (named ? " (" + colorName.trim() + ")" : "") + " is back in stock"
            : "Kinetic E-Bike: " + productName + (named ? " (" + colorName.trim() + ")" : "") + " đã có hàng lại";
    }

    /** {@code productUrl} is the absolute link to the bike page; it is left out of the text when blank. */
    public static String emailBody(String language, String productName, String colorName, String productUrl) {
        String link = hasText(productUrl) ? productUrl.trim() : null;
        if ("en".equals(normalize(language))) {
            return "The " + productName + " in " + colour(language, colorName) + " that you were waiting for is back in stock.\n\n"
                + (link != null ? "Open the bike page to order it: " + link + "\n\n" : "Open the bike page in the Kinetic store to order it.\n\n")
                + "Quantities are limited, so order early. "
                + "You are receiving this email because you chose \"Notify me when back in stock\".\n";
        }
        return "Xe " + productName + ", " + colour(language, colorName) + " mà bạn đang chờ đã có hàng lại.\n\n"
            + (link != null ? "Mở trang xe để đặt mua: " + link + "\n\n" : "Vào cửa hàng Kinetic và mở trang xe để đặt mua.\n\n")
            + "Số lượng có hạn, bạn nên đặt sớm. "
            + "Bạn nhận email này vì đã chọn \"Báo tôi khi có hàng\".\n";
    }

    /** "/product/{slug}" on the shop's public address, with the slug encoded. */
    public static String productUrl(String baseUrl, String slug) {
        if (!hasText(baseUrl) || !hasText(slug)) {
            return null;
        }
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + productPath(slug);
    }

    /** "/product/{slug}" with the slug encoded; null when there is no slug, so no link is better than "/product/null". */
    public static String productPath(String slug) {
        if (!hasText(slug)) {
            return null;
        }
        return "/product/" + URLEncoder.encode(slug.trim(), StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** notifications.target_url holds 255 characters; a longer link is dropped (the notice still shows) rather than failing the insert. */
    public static final int MAX_TARGET_URL_LENGTH = 255;

    /** The link stored with the in-app notice: {@link #productPath} or null when there is none or it would not fit the column. */
    public static String notificationPath(String slug) {
        String path = productPath(slug);
        return path != null && path.length() <= MAX_TARGET_URL_LENGTH ? path : null;
    }

    // A variant with neither a colour nor a name must not print the word "null".
    private static String colour(String language, String colorName) {
        if (hasText(colorName)) {
            return "en".equals(normalize(language)) ? colorName.trim() : "màu " + colorName.trim();
        }
        return "en".equals(normalize(language)) ? "the colour you chose" : "màu bạn chọn";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
