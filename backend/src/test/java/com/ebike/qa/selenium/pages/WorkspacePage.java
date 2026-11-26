package com.ebike.qa.selenium.pages;

import com.ebike.qa.selenium.Ui;
import java.util.ArrayList;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/** The signed-in workspace shared by customer, manager and admin pages (workspace.js renders a table, metrics, filters and notices). */
public class WorkspacePage extends Page {
    public static final By NOTICE = By.id("notice");
    public static final By METRICS = By.cssSelector("#metrics .metric");
    public static final By ROWS = By.cssSelector("#data-table tbody tr");
    public static final By HEADERS = By.cssSelector("#data-table thead th");
    public static final By LOADING = By.id("loading");
    public static final By EMPTY = By.id("empty");
    public static final By SIDEBAR = By.cssSelector(".work-sidebar a");
    public static final By TITLE = By.cssSelector(".work-heading h1");
    public static final By LOGOUT = By.id("logout");

    public WorkspacePage(Ui ui) { super(ui); }

    /** Opens a workspace path and waits until the data has been drawn (rows, empty text or an error). */
    public WorkspacePage open(String path) {
        ui.open(path);
        return waitLoaded();
    }

    public WorkspacePage waitLoaded() {
        ui.visible(TITLE);
        ui.until(20, d -> {
            WebElement loading = d.findElement(LOADING);
            return !loading.isDisplayed();
        });
        return this;
    }

    public void reload() { ui.click(By.id("refresh")); ui.until(d -> !d.findElement(LOADING).isDisplayed()); }

    public String title() { return ui.text(TITLE); }

    public String notice() { ui.until(d -> ui.shown(NOTICE)); return ui.text(NOTICE); }

    /** Waits for a notice containing the text. */
    public void waitNotice(String part) { ui.until(d -> ui.shown(NOTICE) && ui.textOf(NOTICE).contains(part)); }

    public List<WebElement> rows() {
        // a table with no result keeps its old rows in the DOM but is hidden: only a shown table has rows
        List<WebElement> table = ui.all(By.id("data-table"));
        if (table.isEmpty() || !table.get(0).isDisplayed()) return new ArrayList<>();
        return ui.all(ROWS);
    }

    /** Text of a cell in the first row containing rowText, read in one step (never stale); null when there is no such row or cell. */
    public String cellOf(String rowText, String field) {
        Object r = ui.js("const t=document.querySelector('#data-table');if(!t||t.hidden||t.offsetParent===null)return null;for(const tr of t.querySelectorAll('tbody tr')){if(tr.textContent.includes(arguments[0])){const td=tr.querySelector('td[data-field=\"'+arguments[1]+'\"]');return td?td.textContent.trim():null;}}return null;", rowText, field);
        return r == null ? null : r.toString();
    }

    public List<String> headers() {
        List<String> out = new ArrayList<>();
        for (WebElement e : ui.all(HEADERS)) out.add(e.getText().trim());
        return out;
    }

    /** First row whose text contains the given text (or null). */
    public WebElement rowWith(String text) {
        for (int i = 0; i < 3; i++) {
            try {
                for (WebElement r : rows()) if (r.getText().contains(text)) return r;
                return null;
            } catch (org.openqa.selenium.StaleElementReferenceException e) { Ui.pause(100); }
        }
        return null;
    }

    public WebElement waitRow(String text) {
        return ui.until(20, d -> rowWith(text));
    }

    public String cell(WebElement row, String field) {
        try {
            List<WebElement> c = row.findElements(By.cssSelector("td[data-field='" + field + "']"));
            return c.isEmpty() ? null : c.get(0).getText().trim();
        } catch (org.openqa.selenium.StaleElementReferenceException e) { return null; }
    }

    public WebElement button(WebElement row, String action) { return row.findElement(By.cssSelector("[data-action='" + action + "']")); }

    public List<String> metricTexts() {
        List<String> out = new ArrayList<>();
        for (WebElement e : ui.all(METRICS)) out.add(e.getText().replace("\n", " ").trim());
        return out;
    }

    public void search(String name, String text) {
        By by = By.cssSelector("#filters [name='" + name + "']");
        ui.type(by, text);
    }

    public void waitRowsGone(String text) { ui.until(20, d -> rowWith(text) == null); }

    /** Picks a next status in the order list row (select.table-action[data-action=order-status]). */
    public void chooseStatus(WebElement row, String status) {
        WebElement s = row.findElement(By.cssSelector("select[data-action='order-status']"));
        ui.scrollTo(s);
        new org.openqa.selenium.support.ui.Select(s).selectByValue(status);
    }

    public List<String> sidebar() {
        List<String> out = new ArrayList<>();
        for (WebElement e : ui.all(SIDEBAR)) out.add(e.getDomAttribute("href"));
        return out;
    }

    public void logout() {
        ui.click(LOGOUT);
        ui.until(d -> d.getCurrentUrl().replaceAll("/+$", "").equals(com.ebike.qa.selenium.QaConfig.baseUrl()) || d.getCurrentUrl().endsWith("/"));
    }
}
