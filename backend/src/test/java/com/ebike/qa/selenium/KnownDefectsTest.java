package com.ebike.qa.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.qa.selenium.pages.CheckoutPage;
import com.ebike.qa.selenium.pages.HomePage;
import com.ebike.qa.selenium.pages.WorkspacePage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;

/**
 * Defects found by hand, written as tests of the behaviour the code intends. They FAIL while the defect exists and pass once it is fixed.
 * Run only these with -Dgroups=bug.
 */
@EnabledIfSystemProperty(named = "qa.baseUrl", matches = ".+")
class KnownDefectsTest extends QaBase {

    @Test
    void regression_homeHeroBackgroundImageIsMissing() {
        new HomePage(ui).open();
        String css = String.valueOf(ui.js("return getComputedStyle(document.querySelector('.hero-image')).backgroundImage;"));
        // Expected: the hero picture named by taste.css loads (HTTP 200). It once answered 404 because the file was deleted but still referenced.
        assertTrue(css.contains("url("), "the hero has a background image: " + css);
        String path = css.replaceAll(".*url\\([\"']?([^\"')]+)[\"']?\\).*", "$1").replace(QaConfig.baseUrl(), "");
        assertEquals(200, Api.statusOf(path), "hero image " + path);
    }

    @Test
    void regression_tasteCssImportsNeverLoadIconsAndFonts() {
        new HomePage(ui).open();
        // taste.css declares two @import rules (Phosphor icons, Google fonts); they must come before every rule, or browsers ignore them.
        Object imports = ui.js("""
            const sheet=[...document.styleSheets].find(s=>s.href&&s.href.includes('taste.css'));
            return sheet?[...sheet.cssRules].filter(r=>r instanceof CSSImportRule).length:-1;""");
        assertEquals(2L, imports,"taste.css should carry its two @import rules, found " + imports);
        Object content = ui.js("return getComputedStyle(document.querySelector('i.ph'),'::before').content;");
        assertNotEquals("none", String.valueOf(content), "a Phosphor icon (i.ph) must draw a glyph; ::before content is " + content);
    }

    @Test
    @Tag("bug")
    void bug_unknownProductShowsRawJsonInsteadOfAPage() {
        ui.open("/product/no-such-bike-" + System.nanoTime());
        // Expected: a friendly HTML "not found" page. Actual: the browser shows the JSON error body.
        assertEquals("text/html", ui.js("return document.contentType;"), "content type of the answer");
        assertFalse(ui.textOf(By.tagName("body")).trim().startsWith("{"), "raw JSON is shown: " + ui.textOf(By.tagName("body")));
    }

    @Test
    @Tag("bug")
    void bug_unknownVehicleTypeShowsRawJsonInsteadOfAPage() {
        ui.open("/products?vehicleType=XYZ");
        // Expected: the catalogue page with an empty result (or the filter ignored). Actual: raw JSON error.
        assertEquals("text/html", ui.js("return document.contentType;"));
        assertFalse(ui.textOf(By.tagName("body")).trim().startsWith("{"), "raw JSON is shown: " + ui.textOf(By.tagName("body")));
        assertTrue(ui.exists(By.cssSelector("main.catalog-page")));
    }

    @Test
    @Tag("bug")
    void bug_invalidPromoCodeMakesThePriceBreakdownDisappear() {
        fillCart(Shop.SHOW, 1);
        CheckoutPage c = new CheckoutPage(ui).open().waitQuote();
        ui.selectValue(CheckoutPage.PROVINCE, "Hồ Chí Minh");
        ui.until(d -> c.quoteHas("Phí giao xe", "200.000"));
        c.applyPromo("KINETIC20");
        ui.until(d -> ui.exists(By.cssSelector("#checkoutQuote .commerce-error")));
        // Expected: the error is shown next to the code, the totals stay and "Đặt hàng" stays usable without the code.
        // Actual: the whole breakdown is replaced by the error and the order button is disabled.
        assertTrue(ui.exists(By.cssSelector("#checkoutQuote .quote-breakdown")), "the price breakdown must stay on screen");
        assertTrue(ui.visible(CheckoutPage.PLACE).isEnabled(), "the order button must not stay disabled");
    }

    @Test
    @Tag("bug")
    void bug_profileEmailFieldIsPlainTextAndAcceptsGarbage() {
        Customer c = newCustomer();
        loginCustomer(c);
        new WorkspacePage(ui).open("/customer/profile");
        By email = By.cssSelector("#profileForm [name=email]");
        ui.visible(email);
        // Expected: type=email, so the browser refuses "abc". Actual: type=text.
        assertEquals("email", ui.attr(email, "type"), "the e-mail input type");
        ui.type(email, "abc");
        assertEquals(Boolean.FALSE, ui.js("return arguments[0].checkValidity();", ui.present(email)), "'abc' must not be a valid e-mail");
    }

    @Test
    void regression_loginAnswersDifferentlyForUnknownUserAndWrongPassword() {
        // Expected: one answer (401) for both, so that nobody can find out which usernames exist. Actual: unknown user 404, wrong password 401.
        int wrongPassword = new Api().post("/api/v1/auth/login", java.util.Map.of("usernameOrEmail", QaConfig.get("staff.manager.username"), "password", "definitely-wrong")).status();
        int unknownUser = new Api().post("/api/v1/auth/login", java.util.Map.of("usernameOrEmail", "no.such.user." + System.nanoTime(), "password", "definitely-wrong")).status();
        assertEquals(wrongPassword, unknownUser, "wrong password -> " + wrongPassword + ", unknown user -> " + unknownUser);
    }

    @Test
    @Tag("bug")
    void bug_rolesPageDoesNotShowThePermissionsOfARole() {
        loginStaff("admin");
        WorkspacePage w = new WorkspacePage(ui).open("/admin/roles");
        // Expected: the page "what each role may do" lists the permissions (workspace.js asks for a permissions column). Actual: the column is filtered out.
        assertTrue(w.headers().contains("Quyền"), "columns shown: " + w.headers());
    }
}
