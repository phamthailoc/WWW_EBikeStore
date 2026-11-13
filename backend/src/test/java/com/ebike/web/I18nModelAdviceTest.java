package com.ebike.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.controller.web.I18nModelAdvice;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

/** V19: the browser gets the strings its scripts need in the page language. */
class I18nModelAdviceTest {
    private final I18nModelAdvice advice = new I18nModelAdvice();

    @AfterEach void reset() { LocaleContextHolder.resetLocaleContext(); }

    @Test void englishPagesGetTheEnglishDictionaryAndTheKeyedMessages() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        var messages = advice.jsMessages();
        assertEquals("Cart", messages.get("Giỏ hàng"));
        assertEquals("Proceed to checkout →", messages.get("Tiến hành thanh toán →"));
        assertEquals("Incorrect username or password.", messages.get("js.login.failed"));
    }

    @Test void vietnamesePagesGetNoDictionaryBecauseTheScriptsAlreadyHoldVietnamese() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("vi"));
        var messages = advice.jsMessages();
        assertFalse(messages.containsKey("Giỏ hàng"));
        assertEquals("Tên đăng nhập hoặc mật khẩu chưa chính xác.", messages.get("js.login.failed"));
    }

    @Test void everyScriptPhraseHasAnEnglishText() throws Exception {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        var messages = advice.jsMessages();
        long untranslated = messages.entrySet().stream()
            .filter(entry -> !entry.getKey().startsWith("js."))
            .filter(entry -> entry.getKey().equals(entry.getValue()))
            .count();
        // a few phrases are the same in both languages (for example the "Tiếng Việt" language name)
        assertTrue(untranslated <= 2, "phrases left untranslated: " + untranslated);
    }
}
