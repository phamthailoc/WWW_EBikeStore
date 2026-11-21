package com.ebike.common.i18n;

import com.ebike.config.UserLocaleResolver;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * The language of the words the server writes itself in an API error: the same rule as the pages (UserLocaleResolver): the
 * choice made on the device (cookie), else the signed-in account's language, else Vietnamese. The browser's Accept-Language
 * is not used, so a page and the errors it shows always speak the same language.
 */
public final class RequestLanguage {

    private RequestLanguage() {
    }

    /** For code that runs inside the dispatcher (controller, service): the locale resolver has already chosen. Without a request it is Vietnamese. */
    public static String current() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            return "vi";
        }
        return "en".equals(LocaleContextHolder.getLocale().getLanguage()) ? "en" : "vi";
    }

    public static String text(String vietnamese, String english) {
        return "en".equals(current()) ? english : vietnamese;
    }

    /**
     * For a servlet filter that runs before the dispatcher and before anyone is signed in (rate limit, token check): only the cookie
     * can be known there, otherwise Vietnamese.
     */
    public static String text(HttpServletRequest request, String vietnamese, String english) {
        return "en".equals(fromCookie(request)) ? english : vietnamese;
    }

    /**
     * For a filter that runs after the sign-in (the access-denied handler): the cookie, else the language saved on the account.
     * The dispatcher's own locale is not set yet there, so the resolver is asked directly.
     */
    public static String text(HttpServletRequest request, UserLocaleResolver resolver, String vietnamese, String english) {
        return "en".equals(resolver.languageOf(request)) ? english : vietnamese;
    }

    /** "en" or "vi" when the device has made a choice, else null. */
    static String fromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (UserLocaleResolver.COOKIE.equals(cookie.getName()) && UserLocaleResolver.supported(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
