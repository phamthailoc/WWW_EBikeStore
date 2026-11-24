package com.ebike.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

/** The storefront templates render in Vietnamese. */
class I18nTemplateTest {
    private final SpringTemplateEngine engine = new SpringTemplateEngine();

    I18nTemplateTest() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        engine.setTemplateResolver(resolver);
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        engine.setMessageSource(messages);
    }

    private String render(String template, Locale locale, Map<String, Object> model) {
        var application = JakartaServletWebApplication.buildApplication(new MockServletContext());
        var exchange = application.buildExchange(new MockHttpServletRequest(), new MockHttpServletResponse());
        WebContext context = new WebContext(exchange, locale);
        Map<String, String> js = new TreeMap<>();
        ResourceBundle bundle = ResourceBundle.getBundle("messages", locale, ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
        bundle.keySet().stream().filter(key -> key.startsWith("js.")).forEach(key -> js.put(key, bundle.getString(key)));
        context.setVariable("jsMessages", js);
        model.forEach(context::setVariable);
        return engine.process("store/" + template, context);
    }
    @Test void loginStaysVietnameseByDefault() {
        String page = render("login", Locale.forLanguageTag("vi"), Map.of());
        assertTrue(page.contains("lang=\"vi\""));
        assertTrue(page.contains("Tiếp tục hành trình cùng KINETIC."));
        assertFalse(page.contains("Continue your journey"));
    }

}
