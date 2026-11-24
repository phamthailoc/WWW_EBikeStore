package com.ebike.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.ebike.controller.web.I18nModelAdvice;
import org.junit.jupiter.api.Test;

/** The browser gets the strings its scripts need, in Vietnamese. */
class I18nModelAdviceTest {
    private final I18nModelAdvice advice = new I18nModelAdvice();

    @Test void pagesGetTheKeyedMessagesAndNoPhraseDictionary() {
        var messages = advice.jsMessages();
        assertFalse(messages.containsKey("Giỏ hàng"));
        assertEquals("Tên đăng nhập hoặc mật khẩu chưa chính xác.", messages.get("js.login.failed"));
    }
}
