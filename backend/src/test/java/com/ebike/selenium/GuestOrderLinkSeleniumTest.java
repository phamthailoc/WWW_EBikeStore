package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * Guest orders must not be handed to whoever signs up with the same email: the email is unverified
 * at sign-up, so customers attach guest orders later through an OTP check.
 *
 * <pre>
 * mvn test -Dtest=GuestOrderLinkSeleniumTest -Dselenium.baseUrl=http://localhost:8080 \
 *     -Dselenium.managerUser=... -Dselenium.managerPassword=...
 * </pre>
 * The manager account is used to cancel the guest order afterwards so its stock is returned.
 */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class GuestOrderLinkSeleniumTest extends StorefrontSeleniumTest {

    @Test
    void signingUpWithAGuestsEmailDoesNotRevealTheirOrders() throws Exception {
        HttpClient manager = managerApi();
        String victimEmail = "guest" + System.currentTimeMillis() + "@example.com";

        // A shopper orders without an account.
        driver.manage().deleteAllCookies();
        placeOrder("PAY_LATER", victimEmail);
        wait.until(ExpectedConditions.urlContains("/checkout/success"));
        String orderNumber = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".order-number"))).getText();

        try {
            // Someone else registers with that email address and signs in.
            String intruder = "intruder" + System.currentTimeMillis();
            HttpClient intruderApi = newClient();
            call(intruderApi, "POST", "/api/v1/auth/register", Map.of(
                "username", intruder, "email", victimEmail, "password", PASSWORD,
                "firstName", "Không", "lastName", "Phải chủ đơn"));
            call(intruderApi, "POST", "/api/v1/auth/login", Map.of("usernameOrEmail", intruder, "password", PASSWORD));

            for (JsonNode order : listOf(call(intruderApi, "GET", "/api/v1/orders", null))) {
                assertFalse(orderNumber.equals(order.path("orderNumber").asText()), "the guest order leaked through the API");
            }

            logIn(intruder, PASSWORD);
            driver.get(baseUrl + "/customer/orders");
            wait.until(d -> d.findElement(By.id("loading")).getAttribute("hidden") != null);
            assertFalse(driver.findElement(By.tagName("main")).getText().contains(orderNumber), "the guest order leaked into My orders");
        } finally {
            cancelAsManager(manager, orderNumber);
        }
    }

    @Test
    void myOrdersOffersToLinkGuestOrdersAndExplainsWhenEmailCannotBeSent() {
        driver.get(baseUrl + "/customer/orders");
        WebElement link = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("link-guest-orders")));
        click(link);
        wait.until(ExpectedConditions.alertIsPresent()).accept();

        // Without SMTP the OTP cannot be sent; the page must say so instead of linking anything.
        // With SMTP configured a code prompt appears instead, which this test cannot read.
        String message = wait.until(d -> {
            if (ExpectedConditions.alertIsPresent().apply(d) != null) {
                d.switchTo().alert().dismiss();
                return "otp-prompt";
            }
            WebElement notice = d.findElement(By.id("notice"));
            return notice.isDisplayed() && !notice.getText().isBlank() ? notice.getText() : null;
        });
        assertTrue(message.equals("otp-prompt") || message.contains("Chưa gửi được mã xác minh"), "unexpected result: " + message);
    }

    private void cancelAsManager(HttpClient manager, String orderNumber) throws Exception {
        for (JsonNode order : listOf(call(manager, "GET", "/api/v1/orders", null))) {
            if (orderNumber.equals(order.path("orderNumber").asText())) {
                call(manager, "PATCH", "/api/v1/orders/" + order.path("id").asLong() + "/status",
                    Map.of("status", "CANCELLED", "reason", "Selenium test cleanup"));
                return;
            }
        }
    }
}
