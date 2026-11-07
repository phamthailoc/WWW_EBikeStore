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

/** B5: registration is the owner's job; the store only sells a paperwork service, and state fees stay out of the total. */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class RegistrationServiceSeleniumTest extends StorefrontSeleniumTest {

    private static final By TOTAL = By.xpath("//div[contains(@class,'quote-total')]/strong");
    private static final By FEE = By.xpath("//div[contains(@class,'quote-line')][span[normalize-space()='Phí hồ sơ']]/strong");
    private static final By NOTE = By.id("registrationNote");
    private static final By OPTION = By.cssSelector(".registration-option");

    @Test
    void homeDeliveryExplainsSelfRegistrationAndOnlyPickupOffersHelp() {
        openCheckoutWithOneBike();
        // Checkout opens on home delivery: the owner registers on their own VNeID account, so no option to tick.
        wait.until(d -> d.findElement(NOTE).getText().contains("Giao tận nhà"));
        assertFalse(driver.findElement(OPTION).isDisplayed(), "no registration help for home delivery");
        String home = driver.findElement(NOTE).getText();
        assertTrue(home.contains("VNeID") && home.contains("phiếu kiểm tra chất lượng") && home.contains("nhận xe tại cửa hàng"),
            "home delivery: tells the owner to register on VNeID and where help is offered: " + home);
        assertTrue(home.contains("Nhà nước") && home.contains("không tính vào đơn"),
            "state fees are explained and left out of the order: " + home);
        assertEquals(0, digits(FEE));

        chooseStorePickup();
        wait.until(ExpectedConditions.visibilityOfElementLocated(OPTION));
        assertTrue(driver.findElement(OPTION).getText().contains("Hỗ trợ đăng ký biển số tại cửa hàng"));
        wait.until(d -> d.findElement(NOTE).getText().startsWith("Nếu không chọn"));
        long totalBefore = digits(TOTAL);

        click(By.name("includeRegistrationService"));
        wait.until(d -> digits(FEE) > 0);
        assertEquals(digits(FEE), digits(TOTAL) - totalBefore, "ticking the service adds only the store's service fee");
        String helped = driver.findElement(NOTE).getText();
        assertTrue(helped.contains("có mặt") && helped.contains("VNeID mức 2") && helped.contains("Khoản cộng thêm ở trên là phí dịch vụ của cửa hàng"),
            "pickup with help: the owner still declares in person: " + helped);

        // Back to home delivery: the option goes away, unticked, and the fee with it.
        js("arguments[0].click()", driver.findElement(By.cssSelector("input[name='deliveryMethod'][value='HOME_DELIVERY']")));
        wait.until(ExpectedConditions.invisibilityOfElementLocated(OPTION));
        assertFalse(driver.findElement(By.name("includeRegistrationService")).isSelected());
        wait.until(d -> digits(FEE) == 0);
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
            chooseStorePickup();
            wait.until(ExpectedConditions.visibilityOfElementLocated(OPTION));
            click(By.name("includeRegistrationService"));
            wait.until(d -> digits(FEE) > 0);

            call(admin, "PATCH", "/api/v1/admin/pricing-rules/" + id, Map.of("amount", amount, "status", "INACTIVE"));
            // Any change that requotes, e.g. applying the (empty) promo code.
            click(By.id("applyPromo"));
            wait.until(ExpectedConditions.invisibilityOfElementLocated(OPTION));
            assertFalse(driver.findElement(By.name("includeRegistrationService")).isSelected(), "the hidden option is unticked");
            assertEquals(0, digits(FEE), "no service fee once the service is off");
            assertTrue(driver.findElements(By.cssSelector("#checkoutQuote .commerce-error")).isEmpty(), "the quote shows no error");
            assertTrue(driver.findElement(NOTE).getText().startsWith("Xe cần đăng ký biển số"), "the self-registration note stays: " + driver.findElement(NOTE).getText());
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
