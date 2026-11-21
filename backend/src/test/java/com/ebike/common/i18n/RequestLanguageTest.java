package com.ebike.common.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ebike.config.UserLocaleResolver;
import com.ebike.model.entity.auth.User;
import com.ebike.model.entity.user.UserPreference;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.user.UserPreferenceRepository;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** The words of an API error follow the page rule: device cookie, else the account's language, else Vietnamese; Accept-Language is ignored. */
class RequestLanguageTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserPreferenceRepository preferences = mock(UserPreferenceRepository.class);
    private final UserLocaleResolver resolver = new UserLocaleResolver(users, preferences);

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
        LocaleContextHolder.resetLocaleContext();
        SecurityContextHolder.clearContext();
    }

    private void signedInWithLanguage(String language) {
        User user = new User();
        user.setId(5L);
        user.setUsername("an");
        UserPreference preference = new UserPreference();
        preference.setLanguage(language);
        when(users.findByUsername("an")).thenReturn(Optional.of(user));
        when(preferences.findByUserId(5L)).thenReturn(Optional.of(preference));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("an", "x", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
    }

    @Test
    void withoutARequestTheTextIsVietnamese() {
        assertEquals("xin chào", RequestLanguage.text("xin chào", "hello"));
    }

    @Test
    void insideTheDispatcherTheLocaleResolversChoiceIsUsed() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        LocaleContextHolder.setLocale(UserLocaleResolver.ENGLISH);
        assertEquals("hello", RequestLanguage.text("xin chào", "hello"));
        LocaleContextHolder.setLocale(UserLocaleResolver.VIETNAMESE);
        assertEquals("xin chào", RequestLanguage.text("xin chào", "hello"));
    }

    @Test
    void theBrowsersAcceptLanguageIsIgnored() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "en-GB,en;q=0.8");
        assertEquals("xin chào", RequestLanguage.text(request, "xin chào", "hello"));
        assertEquals("xin chào", RequestLanguage.text(request, resolver, "xin chào", "hello"));
    }

    @Test
    void theChoiceMadeOnTheDeviceIsUsedByAFilter() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("ebike_lang", "en"));
        assertEquals("hello", RequestLanguage.text(request, "xin chào", "hello"));
        request.setCookies(new Cookie("ebike_lang", "xx"));
        assertEquals("xin chào", RequestLanguage.text(request, "xin chào", "hello"));
    }

    @Test
    void aSignedInAccountWithoutACookieGetsItsSavedLanguageEvenWhenTheBrowserIsVietnamese() {
        signedInWithLanguage("en");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addPreferredLocale(UserLocaleResolver.VIETNAMESE);
        request.addHeader("Accept-Language", "vi-VN");
        LocaleContextHolder.setLocale(UserLocaleResolver.VIETNAMESE); // what RequestContextFilter sets from the browser
        assertEquals("hello", RequestLanguage.text(request, resolver, "xin chào", "hello"));
        assertEquals("en", resolver.languageOf(request));
    }

    @Test
    void theCookieBeatsTheAccountLanguage() {
        signedInWithLanguage("en");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("ebike_lang", "vi"));
        assertEquals("xin chào", RequestLanguage.text(request, resolver, "xin chào", "hello"));
    }

    @Test
    void aVisitorWithNothingSavedGetsVietnameseAndNothingIsRemembered() {
        SecurityContextHolder.getContext().setAuthentication(
            new AnonymousAuthenticationToken("k", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertEquals("vi", resolver.languageOf(request));
        // a later look, once the account is known, must not reuse an earlier answer
        signedInWithLanguage("en");
        assertEquals("en", resolver.languageOf(request));
        when(users.findByUsername(any())).thenReturn(Optional.empty());
    }
}
