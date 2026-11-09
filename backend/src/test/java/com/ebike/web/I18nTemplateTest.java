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

/** V19: the storefront templates render in both languages, and English never leaves the Vietnamese copy behind. */
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

    @Test void loginIsEnglishWhenAsked() {
        String page = render("login", Locale.ENGLISH, Map.of());
        assertTrue(page.contains("lang=\"en\""), "html lang follows the locale");
        assertTrue(page.contains("Sign in."), page);
        assertTrue(page.contains("Email or username"));
        assertFalse(page.contains("Tiếp tục hành trình"), "no Vietnamese sentence is left in the English login page");
        assertTrue(page.contains("\"js.login.failed\":\"Incorrect username or password.\""), "scripts get their strings");
    }

    @Test void loginStaysVietnameseByDefault() {
        String page = render("login", Locale.forLanguageTag("vi"), Map.of());
        assertTrue(page.contains("lang=\"vi\""));
        assertTrue(page.contains("Tiếp tục hành trình cùng KINETIC."));
        assertFalse(page.contains("Continue your journey"));
    }

    @Test void signupAndSupportShellRenderInEnglish() {
        String signup = render("signup", Locale.ENGLISH, Map.of());
        assertTrue(signup.contains("Create your account."));
        assertTrue(signup.contains("Already have an account?"));
        String flow = render("flow", Locale.ENGLISH, Map.of("title", "Customer support", "api", "", "flow", "support",
            "vnpayEnabled", false, "emailOtpRequired", false));
        assertTrue(flow.contains("CUSTOMER SERVICE"), flow);
        assertFalse(flow.contains("DỊCH VỤ KHÁCH HÀNG"));
    }

    @Test void catalogueAndHomeRenderInEnglish() {
        String home = render("home", Locale.ENGLISH, Map.of("featured", List.of(), "catalogProducts", List.of(), "products", List.of(), "query", ""));
        assertTrue(home.contains("Explore featured bikes"), home);
        assertTrue(home.contains("Subscribe"));
        assertFalse(home.contains("Khám phá xe nổi bật"));
    }
}
