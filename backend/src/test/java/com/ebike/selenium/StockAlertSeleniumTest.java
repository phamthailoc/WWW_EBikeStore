package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * V22: a colour is set to 0, the customer asks to be told, asking twice keeps one row, the manager restocks the colour,
 * and the customer finds a notification that opens the bike page.
 */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class StockAlertSeleniumTest extends StorefrontSeleniumTest {

    private static int alertsForVariant(long variantId) throws Exception {
        int count = 0;
        for (JsonNode row : listOf(call(api, "GET", "/api/v1/stock-alerts", null))) {
            if (row.path("variantId").asLong() == variantId) {
                count++;
            }
        }
        return count;
    }

    private static void setStock(HttpClient manager, long productId, long variantId, int quantity) throws Exception {
        call(manager, "PATCH", "/api/v1/manager/products/" + productId + "/variants/" + variantId + "/stock",
            Map.of("stockQuantity", quantity));
    }

    @Test
    void customerIsToldWhenASoldOutColourComesBack() throws Exception {
        HttpClient manager = managerApi(); // skips without a manager account: needed to change the stock
        JsonNode product = call(api, "GET", "/api/v1/products/" + productSlug, null);
        long productId = product.path("id").asLong();
        JsonNode colour = product.path("variants").get(0);
        long variantId = colour.path("id").asLong();
        int original = colour.path("stockQuantity").asInt();

        setStock(manager, productId, variantId, 0);
        try {
            // 1. the sold-out colour offers the button; the customer registers
            driver.get(baseUrl + "/product/" + productSlug);
            click(By.cssSelector(".variant-option[data-variant-id='" + variantId + "']"));
            By notify = By.id("notifyStock");
            wait.until(ExpectedConditions.visibilityOfElementLocated(notify));
            click(notify);
            wait.until(d -> alertsOrZero(variantId) == 1);

            // 2. asking a second time (button and API) does not create a duplicate
            click(notify);
            call(api, "POST", "/api/v1/stock-alerts", Map.of("variantId", variantId));
            assertEquals(1, alertsForVariant(variantId), "one waiting row per customer and colour");

            // 3. the waiting list is shown with the favourites and can be seen before the colour is back
            driver.get(baseUrl + "/favorites");
            WebElement waiting = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#stock-alerts .stock-alert-list li")));
            assertTrue(waiting.getText().contains(colour.path("colorName").asText()), waiting.getText());

            // 4. the manager adds stock: sold out -> in stock
            setStock(manager, productId, variantId, 3);

            // 5. the customer gets a notification that links to the bike page
            driver.get(baseUrl + "/customer/notifications");
            WebElement link = wait.until(d -> {
                return d.findElements(By.cssSelector("#data-table a.table-action[href='/product/" + productSlug + "']")).stream()
                    .findFirst().orElse(null);
            });
            assertTrue(link.isDisplayed(), "the notification row links to the bike page");
            click(link);
            wait.until(d -> d.getCurrentUrl().endsWith("/product/" + productSlug));

            // the row was used: the favourites list now shows it as back in stock instead of waiting
            driver.get(baseUrl + "/favorites");
            WebElement used = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#stock-alerts .stock-alert-list li")));
            assertTrue(used.findElement(By.cssSelector(".stock-tag")).getAttribute("class").contains("ok"), used.getText());

            // a plain top-up of a colour already in stock tells nobody again
            int notificationsBefore = countStockAlertNotifications();
            setStock(manager, productId, variantId, 9);
            assertEquals(notificationsBefore, countStockAlertNotifications(), "a top-up of an in-stock colour sends nothing");
        } finally {
            setStock(manager, productId, variantId, original);
            try {
                call(api, "DELETE", "/api/v1/stock-alerts/" + variantId, null);
            } catch (IllegalStateException ignored) {
                // already removed
            }
        }
    }

    private static int alertsOrZero(long variantId) {
        try {
            return alertsForVariant(variantId);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int countStockAlertNotifications() throws Exception {
        int count = 0;
        for (JsonNode row : listOf(call(api, "GET", "/api/v1/notifications", null))) {
            if ("STOCK_ALERT".equals(row.path("type").asText())) {
                count++;
            }
        }
        return count;
    }
}
