package com.ebike.qa.selenium.pages;

import com.ebike.qa.selenium.Ui;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class CheckoutPage extends Page {
    public static final By FORM = By.id("checkoutForm");
    public static final By NAME = By.name("customerName");
    public static final By PHONE = By.name("phoneNumber");
    public static final By EMAIL = By.name("customerEmail");
    public static final By PROVINCE = By.name("province");
    public static final By ADDRESS = By.name("detailedAddress");
    public static final By PAYMENT = By.name("paymentMethod");
    public static final By PROMO = By.id("discountCode");
    public static final By APPLY_PROMO = By.id("applyPromo");
    public static final By PLACE = By.id("placeOrder");
    public static final By ERROR = By.id("checkoutError");
    public static final By QUOTE = By.id("checkoutQuote");
    public static final By REGISTRATION = By.name("includeRegistrationService");

    public CheckoutPage(Ui ui) { super(ui); }

    public CheckoutPage open() {
        ui.open("/checkout");
        ui.until(d -> ui.exists(FORM) || ui.exists(By.cssSelector(".commerce-empty")));
        return this;
    }

    public CheckoutPage waitQuote() {
        ui.until(d -> ui.exists(By.cssSelector("#checkoutQuote .quote-breakdown")) || ui.exists(By.cssSelector("#checkoutQuote .commerce-error")));
        return this;
    }

    public CheckoutPage contact(String name, String phone, String email) {
        ui.type(NAME, name);
        ui.type(PHONE, phone);
        ui.type(EMAIL, email);
        return this;
    }

    public CheckoutPage home(String province, String address) {
        ui.selectValue(PROVINCE, province);
        ui.type(ADDRESS, address);
        return this;
    }

    public CheckoutPage pickup() {
        ui.jsClick(By.cssSelector("input[name=deliveryMethod][value=STORE_PICKUP]"));
        ui.visible(By.cssSelector(".showroom-option"));
        ui.click(By.cssSelector(".showroom-option"));
        return this;
    }

    public CheckoutPage chooseHomeDelivery() {
        ui.jsClick(By.cssSelector("input[name=deliveryMethod][value=HOME_DELIVERY]"));
        return this;
    }

    public void applyPromo(String code) {
        ui.type(PROMO, code);
        ui.click(APPLY_PROMO);
    }

    /** The money text of a quote line whose label contains the given words, e.g. "Phí giao xe". */
    public String quote(String label) {
        for (WebElement line : ui.all(By.cssSelector("#checkoutQuote .quote-line"))) {
            if (line.findElement(By.tagName("span")).getText().contains(label)) return line.findElement(By.tagName("strong")).getText().trim();
        }
        throw new AssertionError("quote line missing: " + label);
    }

    public boolean quoteHas(String label, String part) {
        try { return quote(label).contains(part); } catch (RuntimeException | AssertionError e) { return false; }
    }

    public boolean quoteIs(String label, long amount) {
        try { return quoteMoney(label) == amount; } catch (RuntimeException | AssertionError e) { return false; }
    }

    public long quoteMoney(String label) { return money(quote(label)); }

    public void waitPlaceEnabled() { ui.until(d -> ui.visible(PLACE).isEnabled()); }

    public void place() { waitPlaceEnabled(); ui.click(PLACE); }

    public String error() { ui.until(d -> ui.shown(ERROR)); return ui.text(ERROR); }

    /** Removes the browser-side limits so that the server's own messages can be seen. */
    public void dropClientValidation() {
        ui.js("document.querySelectorAll('#checkoutForm [pattern],#checkoutForm [minlength]').forEach(e=>{e.removeAttribute('pattern');e.removeAttribute('minlength')});");
    }
}
