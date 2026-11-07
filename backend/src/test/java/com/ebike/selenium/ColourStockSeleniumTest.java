package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/** V17: the manager restocks each colour; the product total follows, and a typed total is refused. */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class ColourStockSeleniumTest extends StorefrontSeleniumTest {

    private JsonNode product() throws Exception {
        return call(api, "GET", "/api/v1/products/" + productSlug, null);
    }

    private int stockOf(JsonNode product, long variantId) {
        for (JsonNode variant : listOf(product.path("variants"))) {
            if (variant.path("id").asLong() == variantId) {
                return variant.path("stockQuantity").asInt();
            }
        }
        throw new AssertionError("colour " + variantId + " not found");
    }

    @Test
    void managerRestocksOneColourAndTheTotalFollows() throws Exception {
        HttpClient manager = managerApi();
        JsonNode before = product();
        JsonNode colour = before.path("variants").get(0);
        long variantId = colour.path("id").asLong();
        int original = colour.path("stockQuantity").asInt();
        int target = original + 7;

        logIn(System.getProperty("selenium.managerUser"), System.getProperty("selenium.managerPassword"));
        driver.get(baseUrl + "/manager/inventory");
        By edit = By.cssSelector("[data-action='stock'][data-slug='" + productSlug + "']");
        wait.until(ExpectedConditions.presenceOfElementLocated(edit));
        click(edit);
        By dialogRow = By.cssSelector("dialog.stock-dialog tr[data-variant='" + variantId + "']");
        WebElement row = wait.until(ExpectedConditions.visibilityOfElementLocated(dialogRow));
        WebElement input = row.findElement(By.cssSelector("input"));
        input.clear();
        input.sendKeys(String.valueOf(target));
        row.findElement(By.cssSelector("[data-save]")).click();
        try {
            wait.until(d -> {
                try {
                    return stockOf(product(), variantId) == target;
                } catch (Exception e) {
                    return false;
                }
            });
            JsonNode after = product();
            int sum = 0;
            for (JsonNode variant : listOf(after.path("variants"))) {
                sum += variant.path("stockQuantity").asInt();
            }
            assertEquals(sum, after.path("stockQuantity").asInt(), "the product total is the sum of its colours");

            long productId = after.path("id").asLong();
            IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> call(manager, "PATCH", "/api/v1/manager/products/" + productId + "/stock", Map.of("stockQuantity", 99)));
            assertEquals(true, refused.getMessage().contains("400"), refused.getMessage());
        } finally {
            call(manager, "PATCH", "/api/v1/manager/products/" + before.path("id").asLong() + "/variants/" + variantId + "/stock",
                Map.of("stockQuantity", original));
        }
    }
}
