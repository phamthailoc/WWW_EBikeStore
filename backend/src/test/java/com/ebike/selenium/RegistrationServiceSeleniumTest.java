package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

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
        assertTrue(driver.findElement(NOTE).getText().contains("Khoản cộng thêm ở trên là phí dịch vụ của cửa hàng"), driver.findElement(NOTE).getText());
    }


    @Test
    void optionDisappearsWithoutAnErrorWhenTheAdminSwitchesItOffMidCheckout() throws Exception {
        String user = System.getProperty("selenium.adminUser", "");
        String password = System.getProperty("selenium.adminPassword", "");
        assumeTrue(!user.isBlank() && !password.isBlank(), "selenium.adminUser/selenium.adminPassword not set");
        HttpClient admin = newClient();
        call(admin, "POST", "/api/v1/auth/login", Map.of("usernameOrEmail", user, "password", password));
        JsonNode rule = null;
        for (JsonNode candidate : listOf(call(admin, "GET", "/api/v1/admin/pricing-rules", null))) {
            if ("REGISTRATION_SERVICE".equals(candidate.path("code").asText())) {
                rule = candidate;
            }
        }
        assumeTrue(rule != null, "database has no REGISTRATION_SERVICE rule; apply backend/database/update_pricing_rules.sql");
        long id = rule.path("id").asLong();
        BigDecimal amount = rule.path("amount").decimalValue();
        String status = rule.path("status").asText();
        try {
            openCheckoutWithOneBike();
            wait.until(ExpectedConditions.visibilityOfElementLocated(OPTION));
            click(By.name("includeRegistrationService"));
            wait.until(d -> digits(FEE) > 0);

            call(admin, "PATCH", "/api/v1/admin/pricing-rules/" + id, Map.of("amount", amount, "status", "INACTIVE"));
            // Any change that requotes, e.g. picking the province.
            new Select(driver.findElement(By.name("province"))).selectByVisibleText("Hồ Chí Minh");
            wait.until(ExpectedConditions.invisibilityOfElementLocated(OPTION));
            assertFalse(driver.findElement(By.name("includeRegistrationService")).isSelected(), "the hidden option is unticked");
            assertEquals(0, digits(FEE), "no service fee once the service is off");
            assertTrue(driver.findElements(By.cssSelector("#checkoutQuote .commerce-error")).isEmpty(), "the quote shows no error");
        } finally {
            call(admin, "PATCH", "/api/v1/admin/pricing-rules/" + id, Map.of("amount", amount, "status", status));
        }
    }
    @Test
    void productPageSaysWhenAVehicleNeedsAPlate() {
        // The test product is faster than 25 km/h, so it is a motorbike in law.
        driver.get(baseUrl + "/product/" + productSlug);
        String note = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".registration-required"))).getText();
        assertTrue(note.contains("đăng ký") && note.contains("biển số"), note);
    }

    private long digits(By locator) {
        wait.until(ExpectedConditions.presenceOfElementLocated(locator));
        return Long.parseLong(driver.findElement(locator).getText().replaceAll("[^0-9]", ""));
    }
}
