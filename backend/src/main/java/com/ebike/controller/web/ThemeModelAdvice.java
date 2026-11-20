package com.ebike.controller.web;

import com.ebike.model.entity.user.UserPreference;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Gives every page the signed-in customer's saved theme ("system", "light" or "dark") as {@code userTheme}, so the page
 * renders in that theme from the first paint. Null for visitors, whose choice lives in their browser (see theme.js).
 */
@ControllerAdvice(annotations = org.springframework.stereotype.Controller.class)
public class ThemeModelAdvice {
    private final UserRepository users;
    private final UserPreferenceRepository preferences;

    public ThemeModelAdvice(UserRepository users, UserPreferenceRepository preferences) {
        this.users = users;
        this.preferences = preferences;
    }

    @ModelAttribute("userTheme")
    public String userTheme() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return users.findByUsername(authentication.getName())
            .or(() -> users.findByEmail(authentication.getName()))
            .map(user -> preferences.findByUserId(user.getId()).map(UserPreference::getTheme).orElse("system"))
            .map(ThemeModelAdvice::normalize)
            .orElse(null);
    }

    private static String normalize(String theme) {
        return "light".equals(theme) || "dark".equals(theme) ? theme : "system";
    }
}
