package com.ebike.qa.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.qa.selenium.pages.ProductPage;
import com.ebike.qa.selenium.pages.WorkspacePage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

@EnabledIfSystemProperty(named = "qa.baseUrl", matches = ".+")
class TestRideTest extends QaBase {
    private static final String SLUG = Shop.SHOW;
    private static final By DIALOG = By.cssSelector("dialog.testride-dialog");
    private static final By FREE_SLOT = By.cssSelector("dialog.testride-dialog .testride-slot:not([disabled])");

    private String phone() { return "09" + String.format("%08d", System.nanoTime() % 100_000_000L); }

    private void openDialog() {
        new ProductPage(ui).open(SLUG);
        ui.click(ProductPage.TEST_RIDE);
        ui.visible(DIALOG);
        ui.until(d -> ui.present(By.cssSelector("dialog.testride-dialog [name=showroom]")).isEnabled());
    }

    /** Looks through the coming days for one with a free slot and picks the first. */
    private void pickFreeSlot() {
        Select day = new Select(ui.present(By.cssSelector("dialog.testride-dialog [name=date]")));
        for (int i = 1; i < Math.min(day.getOptions().size(), 8); i++) {
            day.selectByIndex(i);
            ui.until(d -> ui.exists(By.cssSelector("dialog.testride-dialog .testride-slot")) || ui.exists(By.cssSelector("dialog.testride-dialog .testride-slots p")));
            if (ui.exists(FREE_SLOT)) {
                ui.click(ui.all(FREE_SLOT).get(0));
                return;
            }
        }
        throw new AssertionError("no free test-ride slot in the next week");
    }

    @Test
    void guestIsSentToLoginAndBack() {
        new ProductPage(ui).open(SLUG);
        ui.click(ProductPage.TEST_RIDE);
        ui.until(d -> d.getCurrentUrl().contains("/login"));
        assertTrue(ui.url().contains("testRide%3D1") || ui.url().contains("testRide=1"), ui.url());
    }

    @Test
    void bookingNeedsASlotAndAValidPhone() {
        Customer c = newCustomer();
        loginCustomer(c);
        openDialog();
        assertFalse(ui.present(By.cssSelector("dialog.testride-dialog [data-submit]")).isEnabled(), "no slot chosen yet");
        pickFreeSlot();
        ui.type(By.cssSelector("dialog.testride-dialog [name=phone]"), "123");
        ui.click(By.cssSelector("dialog.testride-dialog [data-submit]"));
        ui.waitText(By.cssSelector("dialog.testride-dialog .support-message"), "Số điện thoại chưa hợp lệ.");
    }

    @Test
    void customerBooksManagerConfirmsCustomerSeesItAndCancelsLater() {
        Customer c = newCustomer();
        loginCustomer(c);
        openDialog();
        pickFreeSlot();
        String phone = phone();
        ui.type(By.cssSelector("dialog.testride-dialog [name=phone]"), phone);
        ui.type(By.cssSelector("dialog.testride-dialog [name=note]"), "Muốn thử xe buổi sáng");
        ui.click(By.cssSelector("dialog.testride-dialog [data-submit]"));
        ui.waitText(By.cssSelector("dialog.testride-dialog [data-field=done]"), Shop.SHOW_NAME);
        ui.click(By.cssSelector("dialog.testride-dialog [data-close]"));

        WorkspacePage mine = new WorkspacePage(ui).open("/customer/test-rides");
        WebElement ride = mine.waitRow(Shop.SHOW_NAME);
        assertEquals("Chờ xác nhận", mine.cell(ride, "status"));

        // the manager confirms with a note
        ui.d.manage().deleteAllCookies();
        loginStaff("manager");
        WorkspacePage queue = new WorkspacePage(ui).open("/manager/test-rides");
        WebElement row = queue.waitRow(phone);
        assertEquals("Chờ xác nhận", queue.cell(row, "status"));
        assertEquals("Muốn thử xe buổi sáng", queue.cell(row, "customerNote"));
        ui.click(row.findElement(By.cssSelector("[data-action=ride-step][data-status=CONFIRMED]")));
        ui.dialogAnswer("Hẹn gặp bạn tại quầy lễ tân");
        queue.waitNotice("Đã cập nhật lịch lái thử.");
        ui.until(20, d -> queue.rowWith(phone) != null && "Đã xác nhận".equals(queue.cellOf(phone, "status")));

        // the customer sees the confirmation and the note, then cancels
        ui.d.manage().deleteAllCookies();
        loginCustomer(c);
        mine.open("/customer/test-rides");
        WebElement confirmed = mine.waitRow(Shop.SHOW_NAME);
        assertEquals("Đã xác nhận", mine.cell(confirmed, "status"));
        assertEquals("Hẹn gặp bạn tại quầy lễ tân", mine.cell(confirmed, "staffNote"));
        ui.click(mine.button(confirmed, "ride-cancel"));
        ui.dialogAnswer(null);
        mine.waitNotice("Đã hủy lịch lái thử.");
        ui.until(20, d -> mine.cellOf(Shop.SHOW_NAME, "status").startsWith("Bạn đã hủy"));
    }

    @Test
    void managerCancellationNeedsAReason() {
        Customer c = newCustomer();
        loginCustomer(c);
        openDialog();
        pickFreeSlot();
        String phone = phone();
        ui.type(By.cssSelector("dialog.testride-dialog [name=phone]"), phone);
        ui.click(By.cssSelector("dialog.testride-dialog [data-submit]"));
        ui.waitText(By.cssSelector("dialog.testride-dialog [data-field=done]"), Shop.SHOW_NAME);
        ui.d.manage().deleteAllCookies();
        loginStaff("manager");
        WorkspacePage queue = new WorkspacePage(ui).open("/manager/test-rides");
        WebElement row = queue.waitRow(phone);
        ui.click(row.findElement(By.cssSelector("[data-action=ride-step][data-status=CANCELLED]")));
        ui.dialogAnswer("");
        ui.waitText(By.cssSelector("dialog[open] .support-message"), "Vui lòng nhập nội dung.");
        ui.dialogAnswer("Showroom bảo trì xe");
        queue.waitNotice("Đã cập nhật lịch lái thử.");
    }

    @Test
    void managerQueueFiltersByStatus() {
        loginStaff("manager");
        WorkspacePage queue = new WorkspacePage(ui).open("/manager/test-rides");
        ui.selectValue(By.cssSelector("#filters [name=status]"), "ALL");
        ui.until(20, d -> !queue.rows().isEmpty() || ui.shown(WorkspacePage.EMPTY));
        ui.selectValue(By.cssSelector("#filters [name=status]"), "CANCELLED");
        ui.until(20, d -> queue.rows().stream().allMatch(r -> queue.cell(r, "status") != null && !queue.cell(r, "status").equals("Chờ xác nhận")));
    }
}
