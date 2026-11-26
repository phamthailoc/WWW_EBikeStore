package com.ebike.qa.selenium.pages;

import com.ebike.qa.selenium.Ui;
import java.util.ArrayList;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class CatalogPage extends Page {
    public static final By CARDS = By.cssSelector(".catalog-grid article.product");
    public static final By NAMES = By.cssSelector(".catalog-grid article.product h3");
    public static final By PRICES = By.cssSelector(".catalog-grid article.product .price");
    public static final By COUNTS = By.cssSelector(".catalog-count b");
    public static final By EMPTY = By.cssSelector("p.empty");
    public static final By PAGINATION = By.cssSelector("nav.pagination a");
    public static final By APPLY = By.cssSelector("button.filter-submit");
    public static final By FILTER_Q = By.cssSelector(".filter-form input[name=q]");

    public CatalogPage(Ui ui) { super(ui); }

    public CatalogPage open() { return open(""); }

    public CatalogPage open(String query) {
        ui.open("/products" + (query.isEmpty() ? "" : "?" + query));
        ui.visible(By.cssSelector("main.catalog-page"));
        return this;
    }

    public int productCount() { return Integer.parseInt(ui.all(COUNTS).get(0).getText().trim()); }

    public int page() { return Integer.parseInt(ui.all(COUNTS).get(1).getText().trim()); }

    public int pageCount() { return Integer.parseInt(ui.all(COUNTS).get(2).getText().trim()); }

    public int cardsShown() { return ui.all(CARDS).size(); }

    public List<String> names() {
        List<String> out = new ArrayList<>();
        for (WebElement e : ui.all(NAMES)) out.add(e.getText().trim());
        return out;
    }

    public List<Long> prices() {
        List<Long> out = new ArrayList<>();
        for (WebElement e : ui.all(PRICES)) out.add(money(e.getText()));
        return out;
    }

    public void setFilter(String name, String value) { ui.selectValue(By.cssSelector(".filter-form select[name=" + name + "]"), value); }

    public void apply() { ui.click(APPLY); }
}
