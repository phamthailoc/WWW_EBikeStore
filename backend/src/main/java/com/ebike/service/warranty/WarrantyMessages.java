package com.ebike.service.warranty;

import com.ebike.config.UserLocaleResolver;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * V20: the words of the warranty book that the server writes itself (API errors, notifications, emails), in Vietnamese or English.
 * A notification or email uses the language saved in the profile (user_preferences.language); an API error uses the language of the
 * request: the choice made on the device, else the signed-in account's language, else the browser's Accept-Language, else Vietnamese.
 * Anything other than "en" is Vietnamese, the shop's base language.
 */
public final class WarrantyMessages {

    private WarrantyMessages() {
    }

    public static String normalize(String language) {
        return language != null && language.trim().toLowerCase(Locale.ROOT).startsWith("en") ? "en" : "vi";
    }

    /** Picks the text for the language of the current request. */
    public static String text(String vietnamese, String english) {
        return "en".equals(requestLanguage()) ? english : vietnamese;
    }

    public static String requestLanguage() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            return "vi";
        }
        HttpServletRequest request = servlet.getRequest();
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (UserLocaleResolver.COOKIE.equals(cookie.getName()) && UserLocaleResolver.supported(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
            // the locale resolver already read the account's language
            return normalize(LocaleContextHolder.getLocale().getLanguage());
        }
        return fromAcceptLanguage(request.getHeader("Accept-Language"));
    }

    /** The first language of the header that the shop has: "en-US,en;q=0.9" is English, "fr,vi;q=0.5" is Vietnamese, nothing known is Vietnamese. */
    static String fromAcceptLanguage(String header) {
        if (header == null || header.isBlank()) {
            return "vi";
        }
        List<Locale.LanguageRange> ranges;
        try {
            ranges = Locale.LanguageRange.parse(header);
        } catch (IllegalArgumentException exception) {
            return "vi";
        }
        for (Locale.LanguageRange range : ranges) {
            String tag = range.getRange().toLowerCase(Locale.ROOT);
            if (tag.equals("en") || tag.startsWith("en-")) {
                return "en";
            }
            if (tag.equals("vi") || tag.startsWith("vi-")) {
                return "vi";
            }
        }
        return "vi";
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

    public static String maintenanceEmailSubject(String language, String productName) {
        return isEnglish(language)
            ? "Kinetic E-Bike: your " + productName + " is due for service"
            : "Kinetic E-Bike: xe " + productName + " đến hạn bảo dưỡng";
    }

    public static String maintenanceEmailBody(String language, String productName, String frameNumber, String milestone) {
        if (isEnglish(language)) {
            return "Your " + productName + " (frame number " + frameNumber + ") has reached its regular service, "
                + MaintenanceMilestones.describe(milestone, language) + " after delivery.\n\n"
                + "Please bring it to a Kinetic store for a check. You can see your warranty book on the Warranty page of your account. "
                + "You are receiving this email because you bought a bike at Kinetic and have notifications turned on.\n";
        }
        return "Xe " + productName + " (số khung " + frameNumber + ") đã đến mốc bảo dưỡng định kỳ, "
            + MaintenanceMilestones.describe(milestone, language) + " kể từ ngày giao.\n\n"
            + "Vui lòng mang xe đến cửa hàng Kinetic để được kiểm tra. Xem sổ bảo hành của bạn tại trang Bảo hành trong tài khoản. "
            + "Bạn nhận email này vì đã mua xe tại Kinetic và đang bật nhận thông báo.\n";
    }

    private static boolean isEnglish(String language) {
        return "en".equals(normalize(language));
    }
}
