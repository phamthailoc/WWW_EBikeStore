package com.ebike.qa.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.qa.selenium.pages.CheckoutPage;
import com.ebike.qa.selenium.pages.HomePage;
import com.ebike.qa.selenium.pages.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;

@EnabledIfSystemProperty(named = "qa.baseUrl", matches = ".+")
class ThemeResponsiveTest extends QaBase {

    private String background() { return String.valueOf(ui.js("return getComputedStyle(document.body).backgroundColor;")); }

    @Test
    void themeButtonCyclesSystemLightDarkAndRemembersItForAVisitor() {
        HomePage home = new HomePage(ui).open();
        assertEquals("system", home.theme());
        home.toggleTheme();
        assertEquals("light", home.theme());
        String lightBackground = background();
        home.toggleTheme();
        assertEquals("dark", home.theme());
        assertNotEquals(lightBackground, background(), "dark must look different from light");
        assertEquals("dark", ui.js("return localStorage.getItem('ebike-theme');"));
        ui.open("/products");
        ui.visible(By.cssSelector("main.catalog-page"));
        assertEquals("dark", new HomePage(ui).theme(), "the choice follows the visitor to other pages");
        ui.d.navigate().refresh();
        ui.visible(By.cssSelector("main.catalog-page"));
        assertEquals("dark", new HomePage(ui).theme());
        new HomePage(ui).toggleTheme();
        assertEquals("system", new HomePage(ui).theme());
    }

    @Test
    void darkThemeAppliesToTheWorkspaceToo() {
        Customer c = newCustomer();
        loginCustomer(c);
        ui.click(Page.THEME_BUTTON);
        ui.click(Page.THEME_BUTTON);
        assertEquals("dark", ui.js("return document.documentElement.getAttribute('data-theme');"));
        assertTrue(Boolean.TRUE.equals(ui.js("return getComputedStyle(document.body).color !== getComputedStyle(document.body).backgroundColor;")));
    }

    private void phone() {
        // a desktop Chrome window cannot be made 375px wide, so the viewport is set the way the browser's device mode does it
        ((org.openqa.selenium.chrome.ChromeDriver) ui.d).executeCdpCommand("Emulation.setDeviceMetricsOverride",
            java.util.Map.of("width", 375, "height", 812, "deviceScaleFactor", 1, "mobile", false));
    }

    private void assertFits(String path, By ready) {
        ui.open(path);
        ui.visible(ready);
        assertEquals(375L, Long.parseLong(String.valueOf(ui.js("return window.innerWidth;"))), "viewport width");
        assertTrue(ui.noHorizontalScroll(), path + " scrolls sideways at 375px: scrollWidth=" + ui.js("return document.documentElement.scrollWidth;"));
    }

    @Test
    void homeFitsAPhone() {
        phone();
        assertFits("/", By.cssSelector("section.hero"));
    }

    @Test
    void catalogueFitsAPhone() {
        phone();
        assertFits("/products", By.cssSelector("main.catalog-page"));
    }

    @Test
    void productPageFitsAPhone() {
        phone();
        assertFits("/product/kinetic-creer-e", By.id("productPage"));
    }

    @Test
    void checkoutFitsAPhone() {
        phone();
        fillCart(Shop.SHOW, 1);
        ui.open("/checkout");
        new CheckoutPage(ui).waitQuote();
        assertEquals(375L, Long.parseLong(String.valueOf(ui.js("return window.innerWidth;"))));
        assertTrue(ui.noHorizontalScroll(), "checkout scrolls sideways at 375px: scrollWidth=" + ui.js("return document.documentElement.scrollWidth;"));
    }

    @Test
    void cartAndLoginFitAPhone() {
        phone();
        fillCart(Shop.SHOW, 1);
        ui.open("/cart");
        ui.visible(By.cssSelector(".cart-item"));
        assertTrue(ui.noHorizontalScroll(), "cart");
        assertFits("/login", By.id("loginForm"));
    }

    @Test
    void managerOrderTableScrollsInsideItsOwnBoxOnAPhone() {
        phone();
        loginStaff("manager");
        ui.open("/manager/orders");
        ui.visible(By.cssSelector(".table-scroll"));
        assertTrue(ui.noHorizontalScroll(), "the page itself must not scroll sideways: the table has its own scroll box");
    }
}
