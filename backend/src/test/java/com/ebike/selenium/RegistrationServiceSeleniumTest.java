package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

/** B5: registration is the owner's job; the store only sells a paperwork service, and state fees stay out of the total. */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class RegistrationServiceSeleniumTest extends StorefrontSeleniumTest {

    private static final By TOTAL = By.xpath("//div[contains(@class,'quote-total')]/strong");
    private static final By FEE = By.xpath("//div[contains(@class,'quote-line')][span[normalize-space()='Phí hồ sơ']]/strong");
    private static final By NOTE = By.id("registrationNote");
    private static final By OPTION = By.cssSelector(".registration-option");

    @Test
    void optionIsAServiceAndStateFeesAreOnlyANote() {
        openCheckoutWithOneBike();
        wait.until(ExpectedConditions.visibilityOfElementLocated(OPTION));
        assertTrue(driver.findElement(OPTION).getText().contains("Nhờ cửa hàng làm hộ thủ tục đăng ký xe"));

        wait.until(d -> !d.findElement(NOTE).getText().isBlank());
        String withoutService = driver.findElement(NOTE).getText();
        assertTrue(withoutService.contains("tự đăng ký") && withoutService.contains("phiếu kiểm tra chất lượng"),
            "unchecked: tells the buyer how to register on their own: " + withoutService);
        assertTrue(withoutService.contains("Nhà nước") && withoutService.contains("không tính vào đơn"),
            "state fees are explained and left out of the order: " + withoutService);
        long totalBefore = digits(TOTAL);
        assertEquals(0, digits(FEE));

        click(By.name("includeRegistrationService"));
        wait.until(d -> digits(FEE) > 0);
        assertEquals(digits(FEE), digits(TOTAL) - totalBefore, "ticking the service adds only the store's service fee");
        assertTrue(driver.findElement(NOTE).getText().contains("phí dịch vụ của cửa hàng"));
    }

    private long digits(By locator) {
        wait.until(ExpectedConditions.presenceOfElementLocated(locator));
        return Long.parseLong(driver.findElement(locator).getText().replaceAll("[^0-9]", ""));
    }
}
