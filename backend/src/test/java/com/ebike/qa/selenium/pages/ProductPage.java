package com.ebike.qa.selenium.pages;

import com.ebike.qa.selenium.Ui;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class ProductPage extends Page {
    public static final By ROOT = By.id("productPage");
    public static final By VARIANTS = By.cssSelector(".variant-option");
    public static final By SELECTED_COLOR = By.id("selectedColor");
    public static final By PRICE = By.id("productPrice");
    public static final By QTY = By.id("productQuantity");
    public static final By MINUS = By.id("quantityMinus");
    public static final By PLUS = By.id("quantityPlus");
    public static final By ADD = By.id("addToCart");
    public static final By BUY_NOW = By.id("buyNow");
    public static final By STOCK_HINT = By.id("stockHint");
    public static final By NOTICE = By.id("productNotice");
    public static final By FAVOURITE = By.id("addFavorite");
    public static final By COMPARE = By.id("addCompare");
    public static final By TEST_RIDE = By.id("bookTestRide");
    public static final By MAIN_IMAGE = By.id("productImage");
    public static final By CAPTION = By.id("photoCaption");
    public static final By RELATED = By.cssSelector("section.related article.product");

    public ProductPage(Ui ui) { super(ui); }

    public ProductPage open(String slug) {
        ui.open("/product/" + slug);
        ui.visible(ROOT);
        ui.until(d -> !ui.text(STOCK_HINT).isEmpty());
        return this;
    }

    public List<WebElement> variants() { return ui.all(VARIANTS); }

    public void pickVariant(int index) {
        ui.click(variants().get(index));
        ui.until(d -> variants().get(index).getDomAttribute("class").contains("selected"));
    }

    public String quantity() { return ui.prop(QTY, "value"); }

    public void setQuantity(String value) {
        WebElement q = ui.visible(QTY);
        ui.scrollTo(q);
        q.sendKeys(org.openqa.selenium.Keys.chord(org.openqa.selenium.Keys.CONTROL, "a"), value);
        q.sendKeys(org.openqa.selenium.Keys.TAB);
    }

    public void addToCart() { ui.click(ADD); }

    public String notice() { ui.until(d -> !ui.textOf(NOTICE).isEmpty()); return ui.textOf(NOTICE); }
}
