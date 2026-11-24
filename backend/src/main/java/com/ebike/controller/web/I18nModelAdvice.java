package com.ebike.controller.web;

import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Gives every page the messages its scripts need as {@code jsMessages} (keys starting with {@code js.} in
 * messages*.properties), already in the page language, so scripts call {@code t('js.key')} with no extra request.
 *
 * <p>Scripts wrap their Vietnamese text as {@code t('Vietnamese text')}; {@code t} returns the phrase itself (and fills {n}-style values).
 */
@ControllerAdvice(annotations = Controller.class)
public class I18nModelAdvice {
    // Vietnamese is the base file: without this the JVM would fall back to its own default language (maybe English) first.
    private static final ResourceBundle.Control NO_FALLBACK = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();

    @ModelAttribute("jsMessages")
    public Map<String, String> jsMessages() {
        Locale locale = Locale.forLanguageTag("vi"); // the shop speaks Vietnamese only
        return cache.computeIfAbsent(locale.getLanguage(), language -> {
            ResourceBundle bundle = ResourceBundle.getBundle("messages", Locale.forLanguageTag(language), NO_FALLBACK);
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
