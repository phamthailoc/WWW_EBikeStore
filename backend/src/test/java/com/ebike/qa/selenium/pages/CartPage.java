package com.ebike.qa.selenium.pages;

import com.ebike.qa.selenium.Ui;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class CartPage extends Page {
    public static final By ITEMS = By.cssSelector(".cart-item");
    public static final By EMPTY = By.cssSelector(".commerce-empty");
    public static final By TOTAL = By.cssSelector(".cart-summary .grand-total strong");
    public static final By CHECKOUT = By.id("checkoutCart");
    public static final By ERROR = By.cssSelector(".cart-products .commerce-error");

    public CartPage(Ui ui) { super(ui); }

    public CartPage open() {
        ui.open("/cart");
        ui.until(d -> ui.exists(ITEMS) || ui.exists(EMPTY));
        return this;
    }

    public List<WebElement> items() { return ui.all(ITEMS); }

    public boolean isEmpty() { return ui.exists(EMPTY) && !ui.exists(ITEMS); }

    public String quantityOf(int row) { return items().get(row).findElement(By.cssSelector(".cart-quantity")).getDomProperty("value"); }

    public void plus(int row) { ui.click(items().get(row).findElement(By.cssSelector("[data-quantity=plus]"))); }

    public void minus(int row) { ui.click(items().get(row).findElement(By.cssSelector("[data-quantity=minus]"))); }

    public void remove(int row) { ui.click(items().get(row).findElement(By.cssSelector(".remove-cart-item"))); }

    public void setQuantity(int row, String value) {
        WebElement q = items().get(row).findElement(By.cssSelector(".cart-quantity"));
        ui.scrollTo(q);
        q.sendKeys(org.openqa.selenium.Keys.chord(org.openqa.selenium.Keys.CONTROL, "a"), value);
        q.sendKeys(org.openqa.selenium.Keys.TAB);
    }
}
