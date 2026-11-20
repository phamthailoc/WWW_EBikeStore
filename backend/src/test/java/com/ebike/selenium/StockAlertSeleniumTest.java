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
 * V22: a colour on sale is set to 0, the customer asks to be told, asking twice keeps one row, the manager restocks the
 * colour, and the customer finds a notification that opens the bike page. The test picks its colour by id and ignores
 * rows or notifications the customer account already had from earlier runs.
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

    /** Back-in-stock notifications that open this bike's page. */
    private static int stockNotificationsFor(String slug) throws Exception {
        int count = 0;
        for (JsonNode row : listOf(call(api, "GET", "/api/v1/notifications", null))) {
            if ("STOCK_ALERT".equals(row.path("type").asText()) && ("/product/" + slug).equals(row.path("targetUrl").asText())) {
                count++;
            }
        }
        return count;
    }

    private static int alertsOrZero(long variantId) {
        try {
            return alertsForVariant(variantId);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int stockNotificationsOrZero(String slug) {
        try {
            return stockNotificationsFor(slug);
        } catch (Exception e) {
            return -1;
        }
    }

    private static void forgetAlert(long variantId) {
        try {
            call(api, "DELETE", "/api/v1/stock-alerts/" + variantId, null);
        } catch (Exception ignored) {
            // no row to remove
        }
    }

    @Test
    void customerIsToldWhenASoldOutColourComesBack() throws Exception {
        HttpClient manager = managerApi(); // skips without a manager account: needed to change the stock
        JsonNode product = call(api, "GET", "/api/v1/products/" + productSlug, null);
        long productId = product.path("id").asLong();
        // the API lists only colours that are on sale
        JsonNode colour = product.path("variants").get(0);
        long variantId = colour.path("id").asLong();
        String colourName = colour.path("colorName").asText();
        int original = colour.path("stockQuantity").asInt();

        forgetAlert(variantId); // start without a row left by an earlier run
        int notificationsBefore = stockNotificationsFor(productSlug);
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

            // 3. the waiting list is shown with the favourites, as waiting, before the colour is back
            driver.get(baseUrl + "/favorites");
            By row = By.cssSelector("#stock-alerts li[data-variant-id='" + variantId + "']");
            WebElement waiting = wait.until(ExpectedConditions.visibilityOfElementLocated(row));
            assertTrue(waiting.getText().contains(colourName), waiting.getText());
            assertTrue(waiting.findElement(By.cssSelector(".stock-tag")).getAttribute("class").contains("low"), waiting.getText());

            // 4. the manager adds stock: sold out -> in stock
            setStock(manager, productId, variantId, 3);

            // 5. exactly one new notification arrives, and its row links to the bike page
            wait.until(d -> stockNotificationsOrZero(productSlug) == notificationsBefore + 1);
            int expectedLinks = stockNotificationsFor(productSlug);
            By links = By.cssSelector("#data-table a.table-action[href='/product/" + productSlug + "']");
            driver.get(baseUrl + "/customer/notifications");
            wait.until(d -> d.findElements(links).size() == expectedLinks);
            WebElement link = driver.findElements(links).get(0);
            assertTrue(link.isDisplayed(), "the notification row links to the bike page");
            click(link);
            wait.until(d -> d.getCurrentUrl().endsWith("/product/" + productSlug));

            // the favourites list now shows that colour as back in stock
            driver.get(baseUrl + "/favorites");
            WebElement used = wait.until(ExpectedConditions.visibilityOfElementLocated(row));
            assertTrue(used.findElement(By.cssSelector(".stock-tag")).getAttribute("class").contains("ok"), used.getText());

            // a plain top-up of a colour already in stock tells nobody again
            int afterRestock = stockNotificationsFor(productSlug);
            setStock(manager, productId, variantId, 9);
            assertEquals(afterRestock, stockNotificationsFor(productSlug), "a top-up of an in-stock colour sends nothing");

            // cancelling removes the row from the list
            click(By.cssSelector("#stock-alerts li[data-variant-id='" + variantId + "'] [data-cancel-alert]"));
            wait.until(d -> alertsOrZero(variantId) == 0);
        } finally {
            setStock(manager, productId, variantId, original);
            forgetAlert(variantId);
        }
    }

    @Test
    void staffAccountsCannotAskToBeNotified() throws Exception {
        HttpClient manager = managerApi();
        JsonNode product = call(api, "GET", "/api/v1/products/" + productSlug, null);
        long variantId = product.path("variants").get(0).path("id").asLong();
        try {
            call(manager, "POST", "/api/v1/stock-alerts", Map.of("variantId", variantId));
            throw new AssertionError("a manager account must not be able to subscribe");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("403"), expected.getMessage());
        }
    }
}
