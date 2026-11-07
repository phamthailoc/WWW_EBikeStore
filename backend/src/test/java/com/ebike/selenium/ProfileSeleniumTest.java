package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

/** V4: change password, address book, notification switch, and the address book showing up at checkout. */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class ProfileSeleniumTest extends StorefrontSeleniumTest {

    private static final String STREET = "123 Đường Kiểm Thử, Phường 1";

    private void openProfile() {
        driver.get(baseUrl + "/customer/profile");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("addressForm")));
        // The address book loads after the page; wait until the empty/list state is rendered.
        wait.until(d -> !d.findElement(By.id("addressList")).getText().isBlank());
    }

    private void changePassword(String current, String next, String confirm) {
        fill("currentPassword", current);
        fill("newPassword", next);
        fill("confirmPassword", confirm);
        click(By.cssSelector("#passwordForm button[type='submit']"));
    }

    @Test
    void passwordCanBeChangedOnlyWithTheRightCurrentOne() {
        openProfile();
        changePassword("sai-mat-khau", "MatKhau@456", "MatKhau@456");
        assertTrue(notice().contains("không đúng"), "wrong current password is refused: " + notice());

        driver.navigate().refresh();
        openProfile();
        changePassword(PASSWORD, PASSWORD, PASSWORD);
        assertTrue(notice().contains("khác"), "an unchanged password is refused: " + notice());

        driver.navigate().refresh();
        openProfile();
        changePassword(PASSWORD, "MatKhau@456", "MatKhau@456");
        wait.until(d -> notice().contains("Đã đổi mật khẩu"));
        // Put it back so the other tests of this run can still sign in.
        driver.navigate().refresh();
        openProfile();
        changePassword("MatKhau@456", PASSWORD, PASSWORD);
        wait.until(d -> notice().contains("Đã đổi mật khẩu"));
    }

    @Test
    void savedAddressIsFilledInAtCheckoutAndCanBeRemoved() {
        openProfile();
        assertTrue(driver.findElement(By.id("addressList")).getText().contains("chưa lưu"));

        new Select(driver.findElement(By.cssSelector("#addressForm select[name='city']"))).selectByVisibleText("Hồ Chí Minh");
        fill("street", STREET);
        click(By.cssSelector("#addressForm input[name='isDefault']"));
        click(By.id("addressSubmit"));
        wait.until(d -> d.findElement(By.id("addressList")).getText().contains(STREET));
        assertTrue(driver.findElement(By.id("addressList")).getText().contains("Mặc định"));

        openCheckoutWithOneBike();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("savedAddress")));
        wait.until(d -> STREET.equals(d.findElement(By.name("detailedAddress")).getAttribute("value")));
        assertEquals("Hồ Chí Minh", new Select(driver.findElement(By.name("province"))).getFirstSelectedOption().getText());

        openProfile();
        click(By.cssSelector("#addressList [data-do='delete']"));
        driver.switchTo().alert().accept();
        wait.until(d -> d.findElement(By.id("addressList")).getText().contains("chưa lưu"));
    }

    @Test
    void shortAddressIsRefusedWithAReadableMessage() {
        openProfile();
        new Select(driver.findElement(By.cssSelector("#addressForm select[name='city']"))).selectByVisibleText("Hà Nội");
        // The browser's own minlength check would stop a short value, so send it past the form.
        js("document.querySelector('#addressForm input[name=street]').removeAttribute('minlength')");
        fill("street", "Số 1");
        click(By.id("addressSubmit"));
        assertTrue(notice().contains("quá ngắn"), notice());
        assertTrue(driver.findElement(By.id("addressList")).getText().contains("chưa lưu"));
    }

    @Test
    void notificationSwitchIsRemembered() {
        openProfile();
        By toggle = By.id("notificationsEnabled");
        wait.until(d -> d.findElement(toggle).isSelected());
        click(toggle);
        wait.until(d -> notice().contains("Đã tắt thông báo"));
        driver.navigate().refresh();
        openProfile();
        wait.until(d -> !d.findElement(toggle).isSelected());
        assertFalse(driver.findElement(toggle).isSelected());
        click(toggle);
        wait.until(d -> notice().contains("Đã bật thông báo"));
    }
}
