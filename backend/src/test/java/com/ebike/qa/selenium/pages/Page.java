package com.ebike.qa.selenium.pages;

import com.ebike.qa.selenium.Ui;
import org.openqa.selenium.By;

/** Base of the page objects: what every page of the site has (header, cart badge, theme button). */
public abstract class Page {
    protected final Ui ui;

    protected Page(Ui ui) { this.ui = ui; }

    public static final By HEADER = By.cssSelector("header.top");
    public static final By CART_BADGE = By.cssSelector("[data-cart-count]");
    public static final By THEME_BUTTON = By.cssSelector(".theme-toggle");

    public String cartBadge() { return ui.textOf(CART_BADGE); }

    public String theme() { return String.valueOf(ui.js("return document.documentElement.getAttribute('data-theme');")); }

    public void toggleTheme() { ui.click(THEME_BUTTON); }

    public static long money(String text) {
        String digits = text.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? 0 : Long.parseLong(digits);
    }
}
