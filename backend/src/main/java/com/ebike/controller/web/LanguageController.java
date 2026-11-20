package com.ebike.controller.web;

import com.ebike.config.UserLocaleResolver;
import com.ebike.model.entity.user.UserPreference;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** {@code /lang/en?back=/products}: remembers the language on this device and, when signed in, on the account. */
@Controller
public class LanguageController {
    private static final long ONE_YEAR_SECONDS = 365L * 24 * 3600;

    private final UserRepository users;
    private final UserPreferenceRepository preferences;

    public LanguageController(UserRepository users, UserPreferenceRepository preferences) {
        this.users = users;
        this.preferences = preferences;
    }

    @GetMapping("/lang/{code}")
    @Transactional
    public String change(@PathVariable String code, @RequestParam(required = false) String back, HttpServletResponse response) {
        if (!UserLocaleResolver.supported(code)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown language");
        }
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(UserLocaleResolver.COOKIE, code)
            .path("/").maxAge(ONE_YEAR_SECONDS).sameSite("Lax").build().toString());
        saveToAccount(code);
        return "redirect:" + safeTarget(back);
    }

    private void saveToAccount(String code) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return;
        }
        users.findByUsername(authentication.getName())
            .or(() -> users.findByEmail(authentication.getName()))
            .ifPresent(user -> {
                UserPreference preference = preferences.findByUserId(user.getId()).orElseGet(() -> {
                    UserPreference created = new UserPreference();
                    created.setUser(user);
                    return created;
                });
                preference.setLanguage(code);
                preferences.save(preference);
            });
    }

    /** Only a path on this site: "//evil.example" or "https://…" would turn this into an open redirect. */
    static String safeTarget(String back) {
        if (back == null || !back.startsWith("/") || back.startsWith("//") || back.startsWith("/\\") || back.contains("\n") || back.contains("\r")) {
            return "/";
        }
        return back;
    }
}
