package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

/** B6: pickup is free; home delivery is charged per bike and depends on the province. */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class DeliveryFeeSeleniumTest extends StorefrontSeleniumTest {

    private static final By SHIPPING = By.xpath("//div[contains(@class,'quote-line')][span[normalize-space()='Phí giao xe']]/strong");

    @Test
    void shippingFeeFollowsDeliveryMethodAndProvince() {
        openCheckoutWithOneBike();
        // Checkout opens on home delivery with no province chosen yet: ask for it instead of showing 0 ₫.
        assertEquals("Chọn tỉnh để tính phí", shippingText(), "no delivery fee is shown until a province is chosen");

        selectProvince("Hồ Chí Minh");
        wait.until(d -> "200000".equals(shippingDigits()));
        assertEquals("200000", shippingDigits(), "a province with a showroom uses the same-city fee");

        selectProvince("Hà Nội");
        wait.until(d -> !"200000".equals(shippingDigits()));
        assertEquals("800000", shippingDigits(), "a province without a showroom uses the other-province fee");

        js("arguments[0].click()", driver.findElement(By.cssSelector("input[name='deliveryMethod'][value='STORE_PICKUP']")));
        wait.until(d -> "0".equals(shippingDigits()));
        assertEquals("0", shippingDigits(), "back to pickup is free again");
    }

    private void selectProvince(String name) {
        new Select(driver.findElement(By.name("province"))).selectByVisibleText(name);
    }

    private String shippingText() {
        wait.until(ExpectedConditions.presenceOfElementLocated(SHIPPING));
        return driver.findElement(SHIPPING).getText().trim();
    }

    private String shippingDigits() {
        return shippingText().replaceAll("[^0-9]", "");
    }
}
