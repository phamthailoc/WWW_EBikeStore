package com.ebike.controller.web;

import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Gives every page the messages its scripts need as {@code jsMessages} (keys starting with {@code js.} in
 * messages*.properties), already in the page language, so scripts call {@code t('js.key')} with no extra request.
 */
@ControllerAdvice(annotations = Controller.class)
public class I18nModelAdvice {
    private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();

    @ModelAttribute("jsMessages")
    public Map<String, String> jsMessages() {
        Locale locale = LocaleContextHolder.getLocale();
        return cache.computeIfAbsent(locale.getLanguage(), language -> {
            ResourceBundle bundle = ResourceBundle.getBundle("messages", Locale.forLanguageTag(language));
            Map<String, String> out = new TreeMap<>();
            for (Enumeration<String> keys = bundle.getKeys(); keys.hasMoreElements();) {
                String key = keys.nextElement();
                if (key.startsWith("js.")) {
                    out.put(key, bundle.getString(key));
                }
            }
            return out;
        });
    }
}
