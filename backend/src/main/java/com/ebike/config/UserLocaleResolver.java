package com.ebike.config;

import com.ebike.model.entity.user.UserPreference;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Picks the page language (V19): the choice made on this device (cookie {@value #COOKIE}), else the language saved on
 * the signed-in account, else Vietnamese. Only "vi" and "en" exist. The account is looked up lazily and once per
 * request, so static files and visitors never touch the database.
 */
public class UserLocaleResolver implements LocaleResolver {
    public static final String COOKIE = "ebike_lang";
    public static final Locale VIETNAMESE = Locale.forLanguageTag("vi");
    public static final Locale ENGLISH = Locale.ENGLISH;
    private static final String REQUEST_ATTRIBUTE = UserLocaleResolver.class.getName() + ".locale";

    private final UserRepository users;
    private final UserPreferenceRepository preferences;

    public UserLocaleResolver(UserRepository users, UserPreferenceRepository preferences) {
        this.users = users;
        this.preferences = preferences;
    }

    public static boolean supported(String code) {
        return "vi".equals(code) || "en".equals(code);
    }

    public static Locale of(String code) {
        return "en".equals(code) ? ENGLISH : VIETNAMESE;
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        Object cached = request.getAttribute(REQUEST_ATTRIBUTE);
        if (cached instanceof Locale locale) {
            return locale;
        }
        Locale locale = fromCookie(request);
        if (locale == null) {
            locale = fromAccount();
        }
        if (locale == null) {
            locale = VIETNAMESE;
        }
        request.setAttribute(REQUEST_ATTRIBUTE, locale);
        return locale;
    }

    private static Locale fromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE.equals(cookie.getName()) && supported(cookie.getValue())) {
                return of(cookie.getValue());
            }
        }
        return null;
    }

    private Locale fromAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return users.findByUsername(authentication.getName())
            .or(() -> users.findByEmail(authentication.getName()))
            .flatMap(user -> preferences.findByUserId(user.getId()))
            .map(UserPreference::getLanguage)
            .filter(UserLocaleResolver::supported)
            .map(UserLocaleResolver::of)
            .orElse(null);
    }

    /** The language is changed through {@code /lang/{code}} (see LanguageController), not through this resolver. */
    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        throw new UnsupportedOperationException("Use /lang/{code} to change the language");
    }
}
