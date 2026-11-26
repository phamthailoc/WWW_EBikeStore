package com.ebike.qa.selenium.pages;

import com.ebike.qa.selenium.Ui;
import org.openqa.selenium.By;

public class HomePage extends Page {
    public static final By HERO = By.cssSelector("section.hero");
    public static final By HERO_IMAGE = By.cssSelector(".hero-image");
    public static final By FEATURED = By.cssSelector("section.featured#featured .product");
    public static final By CATALOG_CARDS = By.cssSelector("section.featured.catalog .product");
    public static final By CATALOG_COUNT = By.cssSelector("section.featured.catalog .count");
    public static final By PRINCIPLES = By.cssSelector("#technology .principle-grid article");
    public static final By NEWSLETTER_EMAIL = By.id("newsletterEmail");
    public static final By NEWSLETTER_MESSAGE = By.id("newsletterMessage");
    public static final By SEARCH = By.cssSelector("header.top .actions form input[name=q]");
    public static final By NAV = By.cssSelector("header.top nav a");

    public HomePage(Ui ui) { super(ui); }

    public HomePage open() { ui.open("/"); ui.visible(HERO); return this; }

    public void search(String q) {
        ui.type(SEARCH, q);
        ui.visible(SEARCH).submit();
    }
}
