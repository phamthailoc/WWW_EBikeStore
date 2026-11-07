package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

/** UC-24: the registration service fee on the checkout page follows the fee the admin sets. */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class AdminPricingSeleniumTest extends StorefrontSeleniumTest {

    private static final By FEE_LINE = By.xpath("//div[contains(@class,'quote-line')][span[normalize-space()='Phí hồ sơ']]/strong");

    @Test
    void checkoutShowsRegistrationFeeSetByAdmin() throws Exception {
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
        String originalAmount = rule.path("amount").decimalValue().toPlainString();
        String originalStatus = rule.path("status").asText();
        try {
            call(admin, "PATCH", "/api/v1/admin/pricing-rules/" + id, Map.of("amount", 653000, "status", "ACTIVE"));
            assertEquals("653000", feeShownAtCheckout(), "checkout must show the new fee");

            call(admin, "PATCH", "/api/v1/admin/pricing-rules/" + id, Map.of("amount", 0, "status", "ACTIVE"));
            assertEquals("0", feeShownAtCheckout(), "an admin may set the fee to zero");
        } finally {
            call(admin, "PATCH", "/api/v1/admin/pricing-rules/" + id, Map.of("amount", new java.math.BigDecimal(originalAmount), "status", originalStatus));
        }
    }

    /** Ticks the registration service at checkout and returns the digits of the "Phí hồ sơ" line. */
    private String feeShownAtCheckout() {
        openCheckoutWithOneBike();
        By service = By.name("includeRegistrationService");
        if (!driver.findElement(service).isSelected()) {
            click(service);
        }
        wait.until(ExpectedConditions.presenceOfElementLocated(FEE_LINE));
        // The quote reloads after the checkbox changes; wait until the digits settle.
        wait.until(d -> !d.findElement(FEE_LINE).getText().replaceAll("[^0-9]", "").isBlank());
        return driver.findElement(FEE_LINE).getText().replaceAll("[^0-9]", "");
    }
}
