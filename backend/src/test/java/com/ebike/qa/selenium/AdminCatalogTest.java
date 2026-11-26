package com.ebike.qa.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.qa.selenium.pages.Page;
import com.ebike.qa.selenium.pages.WorkspacePage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

@EnabledIfSystemProperty(named = "qa.baseUrl", matches = ".+")
class AdminCatalogTest extends QaBase {
    private static final By DIALOG = By.id("catalogDialog");

    private WorkspacePage catalog() {
        loginStaff("admin");
        return new WorkspacePage(ui).open("/admin/catalog");
    }

    private void tab(String name) {
        ui.click(By.cssSelector("[data-tab=" + name + "]"));
        ui.until(d -> "true".equals(ui.attr(By.cssSelector("[data-tab=" + name + "]"), "aria-pressed")));
    }

    private WebElement rowOf(String text) {
        return ui.until(20, d -> {
            try { return ui.all(By.cssSelector("#data-table tbody tr")).stream().filter(r -> r.getText().contains(text)).findFirst().orElse(null); }
            catch (org.openqa.selenium.StaleElementReferenceException e) { return null; }
        });
    }

    /** Clicks an action of the row, looked up afresh because the list redraws itself. */
    private void act(String rowText, String action) {
        ui.until(20, d -> {
            try { ui.click(rowOf(rowText).findElement(By.cssSelector("[data-do=" + action + "]"))); return true; }
            catch (RuntimeException e) { return false; }
        });
    }

    private void filter(String text) {
        ui.type(By.cssSelector("#catalogFilter [name=search]"), text);
        ui.click(By.cssSelector("#catalogFilter button[type=submit]"));
    }

    @Test
    void categoryCreateEditDeleteWithConfirm() {
        WorkspacePage w = catalog();
        tab("categories");
        String name = "QA loại " + System.nanoTime() % 1_000_000;
        ui.click(By.id("catalogAdd"));
        ui.visible(DIALOG);
        ui.type(By.cssSelector("#catalogDialog [name=name]"), name);
        ui.click(By.cssSelector("#catalogForm button[type=submit]"));
        w.waitNotice("Đã thêm mới.");
        WebElement row = rowOf(name);
        assertTrue(row.getText().contains("qa-loai"), "slug is made from the name: " + row.getText());
        act(name, "edit");
        ui.until(d -> ui.textOf(By.id("catalogDialogTitle")).startsWith("Sửa"));
        ui.type(By.cssSelector("#catalogDialog [name=description]"), "Mô tả QA");
        ui.click(By.cssSelector("#catalogForm button[type=submit]"));
        w.waitNotice("Đã lưu thay đổi.");
        act(name, "delete");
        assertTrue(ui.dismissAlert().contains(name), "the confirm names the category");
        assertTrue(rowOf(name) != null, "dismissing keeps it");
        act(name, "delete");
        ui.acceptAlert();
        w.waitNotice("Đã xóa.");
        ui.until(20, d -> ui.all(By.cssSelector("#data-table tbody tr")).stream().noneMatch(r -> r.getText().contains(name)));
    }

    @Test
    void categoryWithBikesCannotBeDeleted() {
        catalog();
        tab("categories");
        ui.until(20, d -> ui.exists(By.cssSelector("#data-table tbody tr")));
        assertTrue(ui.exists(By.cssSelector("#data-table button[disabled]")), "a category that still has bikes has a disabled delete button");
    }

    @Test
    void bikeCreateShowsOnTheStorefrontEditChangesPriceThenDelete() {
        WorkspacePage w = catalog();
        String name = "QA Xe " + System.nanoTime() % 1_000_000_000L;
        ui.click(By.id("catalogAdd"));
        ui.visible(DIALOG);
        ui.type(By.cssSelector("#catalogDialog [name=name]"), name);
        new Select(ui.present(By.cssSelector("#catalogDialog [name=categoryId]"))).selectByIndex(1);
        ui.type(By.cssSelector("#catalogDialog [name=price]"), "12345000");
        ui.type(By.cssSelector("#catalogDialog [name=description]"), "Xe thử nghiệm của QA");
        ui.click(By.cssSelector("#catalogForm button[type=submit]"));
        w.waitNotice("Đã thêm mới.");
        filter(name);
        WebElement row = rowOf(name);
        assertEquals("12.345.000 ₫", w.cell(row, "price"));
        String slug = row.findElement(By.cssSelector("small")).getText();
        assertEquals(200, Api.statusOf("/product/" + slug), "an active bike is on sale at once");
        // edit the price
        act(name, "edit");
        ui.until(d -> ui.textOf(By.id("catalogDialogTitle")).startsWith("Sửa"));
        ui.type(By.cssSelector("#catalogDialog [name=price]"), "11111000");
        ui.click(By.cssSelector("#catalogForm button[type=submit]"));
        w.waitNotice("Đã lưu thay đổi.");
        filter(name);
        ui.until(20, d -> "11.111.000 ₫".equals(w.cell(rowOf(name), "price")));
        assertEquals(11_111_000L, Page.money(ui.text(By.xpath("//tr[contains(.,'" + name + "')]//td[@data-field='price']"))));
        // delete it again
        act(name, "delete");
        assertTrue(ui.acceptAlert().startsWith("Xóa \"" + name + "\""));
        w.waitNotice("Đã xóa.");
        assertEquals(404, Api.statusOf("/api/v1/products/" + slug));
    }

    @Test
    void bikeFormNeedsNameCategoryAndPrice() {
        catalog();
        ui.click(By.id("catalogAdd"));
        ui.visible(DIALOG);
        for (String f : new String[] {"name", "categoryId", "price"}) {
            assertEquals(Boolean.FALSE, ui.js("return arguments[0].checkValidity();", ui.present(By.cssSelector("#catalogDialog [name=" + f + "]"))), f);
        }
        ui.type(By.cssSelector("#catalogDialog [name=slug]"), "Bad Slug!");
        assertEquals(Boolean.TRUE, ui.js("return arguments[0].validity.patternMismatch;", ui.present(By.cssSelector("#catalogDialog [name=slug]"))));
        ui.click(By.id("catalogCancel"));
    }

    @Test
    void bikeListFiltersByNameAndCategory() {
        catalog();
        filter("amy");
        ui.until(20, d -> { try { var rows = ui.all(By.cssSelector("#data-table tbody tr")); return rows.size() >= 1 && rows.stream().allMatch(r -> r.getText().toLowerCase().contains("amy")); } catch (RuntimeException e) { return false; } });
        filter("zzzz-none-" + System.nanoTime());
        ui.until(20, d -> ui.shown(By.id("empty")));
        assertEquals("Không có kết quả phù hợp.", ui.text(By.id("empty")));
    }

    @Test
    void managerCannotOpenTheCatalogue() {
        assertEquals(403, Api.staff("manager").get("/admin/catalog").status());
        assertEquals(403, Api.staff("manager").get("/api/v1/admin/catalog/products").status());
    }
}
