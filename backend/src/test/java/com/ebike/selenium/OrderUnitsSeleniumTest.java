package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

/**
 * V18: staff record the frame and engine number of every vehicle; a bike cannot leave the store (Đang giao, Đã giao,
 * Đã nhận) without them. Numbers are unique, searchable from the Manager order list and shown to the customer.
 * Needs {@code -Dselenium.managerUser} and {@code -Dselenium.managerPassword}.
 */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class OrderUnitsSeleniumTest extends StorefrontSeleniumTest {

    private static final String REQUIRED = "Hãy nhập đủ số khung và số máy";

    @Test
    void homeDeliveryShipsOnlyOnceTheNumbersAreSavedAndTheCustomerSeesThem() throws Exception {
        HttpClient manager = managerApi();
        String orderNumber = placePayLaterOrder();
        long orderId = findOrder(orderNumber).path("id").asLong();
        status(manager, orderId, "CONFIRMED");
        status(manager, orderId, "PROCESSING");
        assertThrows(IllegalStateException.class,
            () -> call(manager, "PATCH", "/api/v1/manager/orders/" + orderId + "/shipment", Map.of("shipmentStatus", "IN_TRANSIT")),
            "the shipment form cannot send the bike out without numbers either");

        openAsManager(orderId);
        WebElement panel = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("orderUnits")));
        assertEquals(1, panel.findElements(By.cssSelector(".unit-row")).size(), "one row per bike");
        new Select(driver.findElement(By.cssSelector("#orderStatusForm select"))).selectByValue("SHIPPED");
        click(By.cssSelector("#orderStatusForm button"));
        String error = notice();
        assertTrue(error.startsWith(REQUIRED), "shipping without numbers is refused, with the message only (no raw JSON): " + error);
        assertEquals("PROCESSING", findOrder(orderNumber).path("status").asText());
        clearNotice();

        String stamp = Long.toString(System.nanoTime(), 36).toUpperCase();
        WebElement frame = driver.findElement(By.cssSelector("#unitsForm [name=frame]"));
        frame.sendKeys(" rlh-" + stamp.toLowerCase() + " x ");
        driver.findElement(By.cssSelector("#unitsForm [name=engine]")).sendKeys("6d12-" + stamp);
        click(By.cssSelector("#unitsForm button"));
        assertEquals("Đã lưu số khung, số máy.", notice());
        String savedFrame = "RLH-" + stamp + "X";
        JsonNode unit = findOrder(orderNumber).path("items").get(0).path("units").get(0);
        assertEquals(savedFrame, unit.path("frameNumber").asText(), "upper-cased, spaces removed");
        assertEquals("6D12-" + stamp, unit.path("engineNumber").asText());

        wait.until(d -> d.findElement(By.cssSelector("#unitsForm [name=frame]")).getAttribute("value").equals(savedFrame));
        clearNotice();
        new Select(driver.findElement(By.cssSelector("#orderStatusForm select"))).selectByValue("SHIPPED");
        click(By.cssSelector("#orderStatusForm button"));
        assertEquals("Trạng thái đơn hàng đã cập nhật.", notice());
        assertEquals("SHIPPED", findOrder(orderNumber).path("status").asText());

        // Search the Manager order list by part of the frame number, ignoring case and the hyphen.
        driver.get(baseUrl + "/manager/orders");
        WebElement search = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".filter-bar input[name=search], input[name=search]")));
        search.sendKeys("rlh" + stamp.toLowerCase().substring(0, Math.min(6, stamp.length())));
        wait.until(d -> {
            List<WebElement> rows = d.findElements(By.cssSelector("#data-table tbody tr"));
            return rows.size() == 1 && rows.get(0).getText().contains(orderNumber);
        });

        logIn(username, PASSWORD);
        driver.get(baseUrl + "/customer/orders/" + orderId);
        WebElement items = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("orderItems")));
        assertTrue(items.getText().contains("Số khung " + savedFrame) && items.getText().contains("Số máy 6D12-" + stamp), items.getText());
        assertTrue(driver.findElements(By.id("unitsForm")).isEmpty(), "customers cannot edit the numbers");
    }

    @Test
    void pickupIsReceivedOnlyWithNumbers() throws Exception {
        HttpClient manager = managerApi();
        long orderId = placeRestOrder(2);
        status(manager, orderId, "CONFIRMED");
        status(manager, orderId, "PROCESSING");
        status(manager, orderId, "READY_FOR_PICKUP"); // waiting at the store needs no numbers yet
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> status(manager, orderId, "DELIVERED"));
        assertTrue(refused.getMessage().contains(REQUIRED), refused.getMessage());
        assertThrows(IllegalStateException.class,
            () -> call(manager, "PATCH", "/api/v1/manager/orders/" + orderId + "/shipment", Map.of("shipmentStatus", "DELIVERED")));
        // One of the two bikes numbered is still not enough.
        long itemId = order(orderId).path("items").get(0).path("id").asLong();
        String s = Long.toString(System.nanoTime(), 36).toUpperCase();
        save(manager, orderId, List.of(unit(itemId, "P1" + s, "Q1" + s)));
        assertThrows(IllegalStateException.class, () -> status(manager, orderId, "DELIVERED"), "both bikes need numbers");
        enterUnits(manager, orderId);
        status(manager, orderId, "DELIVERED");
        assertEquals("DELIVERED", order(orderId).path("status").asText());
    }

    @Test
    void numbersAreCheckedAndAFrameBelongsToOneBike() throws Exception {
        HttpClient manager = managerApi();
        long orderId = placeRestOrder(2);
        long itemId = order(orderId).path("items").get(0).path("id").asLong();
        String s = Long.toString(System.nanoTime(), 36).toUpperCase();

        refused(manager, orderId, List.of(unit(itemId, "F1" + s, "E1" + s)), "đang xử lý"); // still PENDING
        status(manager, orderId, "CONFIRMED");
        status(manager, orderId, "PROCESSING");

        refused(manager, orderId, List.of(unit(itemId, "AB1", "E1" + s)), "5 đến 30 ký tự");
        refused(manager, orderId, List.of(unit(itemId, "F1" + s + "!", "E1" + s)), "5 đến 30 ký tự");
        refused(manager, orderId, List.of(unit(itemId, "F1" + s, "E1" + s), unit(itemId, "f1" + s, "E2" + s)), "bị nhập hai lần");
        refused(manager, orderId, List.of(unit(itemId, "F1" + s, "E1" + s), unit(itemId, "F2" + s, "E2" + s), unit(itemId, "F3" + s, "E3" + s)), "chỉ có 2 xe");
        refused(manager, orderId, List.of(unit(999_999_999L, "F1" + s, "E1" + s)), "không hợp lệ");

        save(manager, orderId, List.of(unit(itemId, "F1" + s, "E1" + s)));
        save(manager, orderId, List.of(unit(itemId, "F1" + s, "E1" + s), unit(itemId, "F2" + s, "E2" + s))); // re-saving the same frame is fine
        assertEquals(2, order(orderId).path("items").get(0).path("units").size());

        // Another order cannot take a frame number already recorded.
        long other = placeRestOrder(1);
        status(manager, other, "CONFIRMED");
        status(manager, other, "PROCESSING");
        long otherItem = order(other).path("items").get(0).path("id").asLong();
        refused(manager, other, List.of(unit(otherItem, "F1" + s, "E9" + s)), "đã được ghi cho một đơn hàng khác");

        // Cancelling frees the numbers, so the bike can be sold on another order.
        status(manager, orderId, "CANCELLED", "Selenium: khách đổi ý");
        assertEquals(0, order(orderId).path("items").get(0).path("units").size(), "numbers of a cancelled order are cleared");
        save(manager, other, List.of(unit(otherItem, "F1" + s, "E9" + s)));
        status(manager, other, "CANCELLED", "Selenium cleanup");
    }

    // ---- steps ---------------------------------------------------------------------------

    private long placeRestOrder(int quantity) throws Exception {
        JsonNode product = call(api, "GET", "/api/v1/products/" + productSlug, null);
        JsonNode color = null;
        for (JsonNode variant : listOf(product.path("variants"))) {
            if (color == null || variant.path("stockQuantity").asInt() > color.path("stockQuantity").asInt()) {
                color = variant;
            }
        }
        assumeTrue(color == null || color.path("stockQuantity").asInt() >= quantity, "not enough bikes of " + productSlug);
        long showroom = listOf(call(api, "GET", "/api/v1/showrooms", null)).iterator().next().path("id").asLong();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productId", product.path("id").asLong());
        item.put("quantity", quantity);
        item.put("productVariantId", color == null ? null : color.path("id").asLong());
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("customerName", "Selenium Tester");
        order.put("phoneNumber", "0901234567");
        order.put("customerEmail", username + "@example.com");
        order.put("deliveryMethod", "STORE_PICKUP");
        order.put("pickupShowroomId", showroom);
        order.put("paymentMethod", "PAY_LATER");
        order.put("items", List.of(item));
        long id = call(api, "POST", "/api/v1/orders", order).path("id").asLong();
        assertTrue(order(id).path("items").get(0).path("requiresUnitNumbers").asBoolean(), productSlug + " is a vehicle");
        return id;
    }

    private static Map<String, Object> unit(long itemId, String frame, String engine) {
        return Map.of("orderItemId", itemId, "frameNumber", frame, "engineNumber", engine);
    }

    private static void save(HttpClient manager, long orderId, List<Map<String, Object>> units) throws Exception {
        call(manager, "PUT", "/api/v1/manager/orders/" + orderId + "/units", Map.of("units", new ArrayList<>(units)));
    }

    private static void refused(HttpClient manager, long orderId, List<Map<String, Object>> units, String message) {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> save(manager, orderId, units));
        assertTrue(error.getMessage().contains("400") && error.getMessage().contains(message), error.getMessage());
    }

    private static JsonNode status(HttpClient manager, long orderId, String status) throws Exception {
        return call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", status));
    }

    private static JsonNode status(HttpClient manager, long orderId, String status, String reason) throws Exception {
        return call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", status, "reason", reason));
    }

    private static JsonNode order(long orderId) throws Exception {
        return call(api, "GET", "/api/v1/orders/" + orderId, null);
    }

    /** The page keeps the last message on screen; clear it so the next {@link #notice()} reads the new one. */
    private void clearNotice() {
        js("const n=document.getElementById('notice');n.textContent='';n.hidden=true");
    }

    private void openAsManager(long orderId) {
        logIn(System.getProperty("selenium.managerUser"), System.getProperty("selenium.managerPassword"));
        driver.get(baseUrl + "/manager/orders/" + orderId);
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("orderManage")));
    }
}
