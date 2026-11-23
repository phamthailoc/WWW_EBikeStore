package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.openqa.selenium.support.ui.Select;

/**
 * V26: placing and then cancelling an order leaves two offsetting lines in the stock history, the totals of the
 * lines match the stock on hand, and a stock receipt made on /manager/stock-receipts adds a RECEIPT line.
 * Needs {@code -Dselenium.managerUser} and {@code -Dselenium.managerPassword}.
 *
 * <pre>
 * mvn test -Dtest=StockReceiptSeleniumTest -Dselenium.baseUrl=http://localhost:8080 \
 *     -Dselenium.managerUser=... -Dselenium.managerPassword=...
 * </pre>
 */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class StockReceiptSeleniumTest extends StorefrontSeleniumTest {

    private void logInAsManager() {
        logIn(System.getProperty("selenium.managerUser"), System.getProperty("selenium.managerPassword"));
    }

    private static JsonNode history(HttpClient manager, long productId) throws Exception {
        return call(manager, "GET", "/api/v1/manager/products/" + productId + "/stock-history?limit=100", null);
    }

    /** The ledger of every colour adds up to the stock on hand, colour by colour. */
    private static void assertBalanced(JsonNode history) {
        for (JsonNode balance : history.path("balances")) {
            assertTrue(balance.path("matches").asBoolean(), "ledger and stock agree: " + balance);
        }
    }

    @Test
    void anOrderThenItsCancellationLeaveTwoOffsettingLines() throws Exception {
        HttpClient manager = managerApi();
        long productId = call(api, "GET", "/api/v1/products/" + productSlug, null).path("id").asLong();
        String orderNumber = placePayLaterOrder();
        long orderId = findOrder(orderNumber).path("id").asLong();

        call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", "CANCELLED", "reason", "Selenium V26"));

        JsonNode after = history(manager, productId);
        int sale = 0;
        int restore = 0;
        for (JsonNode line : after.path("items")) {
            if (orderNumber.equals(line.path("orderNumber").asText())) {
                if ("SALE".equals(line.path("reason").asText())) {
                    sale += line.path("change").asInt();
                } else if ("CANCEL_RESTORE".equals(line.path("reason").asText())) {
                    restore += line.path("change").asInt();
                }
            }
        }
        assertTrue(sale < 0, "the order took bikes off the shelf: " + after.path("items"));
        assertEquals(-sale, restore, "the cancellation put the same bikes back");
        assertBalanced(after);
    }

    @Test
    void aStockReceiptAddsStockAndALedgerLine() throws Exception {
        HttpClient manager = managerApi();
        JsonNode product = call(api, "GET", "/api/v1/products/" + productSlug, null);
        long productId = product.path("id").asLong();
        JsonNode colour = product.path("variants").path(0);
        long colourId = colour.path("id").asLong();
        int before = colour.path("stockQuantity").asInt();
        String supplier = "Selenium NCC " + System.currentTimeMillis();

        logInAsManager();
        driver.get(baseUrl + "/manager/stock-receipts");
        click(By.id("newReceipt"));
        WebElement supplierInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("dialog input[name='supplier']")));
        supplierInput.sendKeys(supplier);
        WebElement bike = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("dialog select[data-product]")));
        new Select(bike).selectByValue(String.valueOf(productId));
        if (colourId > 0) {
            new Select(driver.findElement(By.cssSelector("dialog select[data-colour]"))).selectByValue(String.valueOf(colourId));
        }
        driver.findElement(By.cssSelector("dialog input[data-qty]")).sendKeys("2");
        click(By.cssSelector("dialog button[data-submit]"));

        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("data-table"), supplier));
        JsonNode updated = call(api, "GET", "/api/v1/products/" + productSlug, null);
        assertEquals(before + 2, updated.path("variants").path(0).path("stockQuantity").asInt());

        JsonNode after = history(manager, productId);
        assertEquals("RECEIPT", after.path("items").path(0).path("reason").asText());
        assertEquals(2, after.path("items").path(0).path("change").asInt());
        assertBalanced(after);
    }

    @Test
    void aCustomerIsRefusedTheReceiptsApi() {
        assertThrows(IllegalStateException.class, () -> call(api, "GET", "/api/v1/manager/stock-receipts", null),
            "a customer cannot read receipts");
        assertThrows(IllegalStateException.class,
            () -> call(api, "POST", "/api/v1/manager/stock-receipts", Map.of("supplierName", "x", "lines", java.util.List.of())),
            "a customer cannot create receipts");
    }
}
