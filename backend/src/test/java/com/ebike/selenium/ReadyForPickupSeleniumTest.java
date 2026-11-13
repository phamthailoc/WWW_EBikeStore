package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

/**
 * V13: a store-pickup order waits in "Chờ lấy" (READY_FOR_PICKUP) instead of being shipped, the store keeps
 * the bike until a per-order deadline (only then may staff cancel), and the Manager finishes it with "Đã nhận".
 * Home deliveries keep the SHIPPED path. Needs {@code -Dselenium.managerUser} and {@code -Dselenium.managerPassword}.
 *
 * <pre>
 * mvn test -Dtest=ReadyForPickupSeleniumTest -Dselenium.baseUrl=http://localhost:8080 \
 *     -Dselenium.managerUser=... -Dselenium.managerPassword=...
 * </pre>
 */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class ReadyForPickupSeleniumTest extends StorefrontSeleniumTest {

    private static final By STATUS_SELECT = By.cssSelector("#orderStatusForm select");
    private static final DateTimeFormatter WIRE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Test
    void pickupOrderWaitsInChoLayAndIsFinishedWithDaNhan() throws Exception {
        HttpClient manager = managerApi();
        String orderNumber = placePickupOrderInTheBrowser();
        long orderId = findOrder(orderNumber).path("id").asLong();
        call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", "CONFIRMED"));
        call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", "PROCESSING"));

        openAsManager(orderId);
        assertEquals(List.of("READY_FOR_PICKUP", "CANCELLED"), statusChoices(), "pickup orders are not shipped");
        assertEquals("Chờ lấy", selectedText("READY_FOR_PICKUP"));
        List<String> shipmentChoices = new Select(driver.findElement(By.cssSelector("#shipmentForm select"))).getOptions()
            .stream().map(o -> o.getAttribute("value")).toList();
        assertFalse(shipmentChoices.contains("SHIPPED") || shipmentChoices.contains("IN_TRANSIT"),
            "the shipment form offers no carrier steps for pickup: " + shipmentChoices);

        LocalDateTime before = LocalDateTime.now();
        new Select(driver.findElement(STATUS_SELECT)).selectByValue("READY_FOR_PICKUP");
        click(By.cssSelector("#orderStatusForm button"));
        assertEquals("Trạng thái đơn hàng đã cập nhật.", notice());

        JsonNode waiting = findOrder(orderNumber);
        assertEquals("READY_FOR_PICKUP", waiting.path("status").asText());
        LocalDateTime deadline = deadlineOf(waiting);
        assertTrue(!deadline.isBefore(before.plusDays(7).minusMinutes(1)) && deadline.isBefore(before.plusDays(7).plusMinutes(1)),
            "default hold is 7 days from now: " + deadline);

        // During the hold the store may only hand the bike over, not cancel.
        wait.until(d -> statusChoices().equals(List.of("DELIVERED")));
        assertEquals("Đã nhận", selectedText("DELIVERED"));
        WebElement hold = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("pickupDeadline")));
        assertTrue(hold.getText().contains("Chỉ hủy được đơn sau thời điểm này"), hold.getText());

        // The customer is told, sees "Chờ lấy" as the current step, and cannot cancel it themselves.
        assertTrue(hasNotification(n -> n.path("message").asText().contains("chờ lấy xe tại cửa hàng")),
            "customer is notified when the bike is ready: " + call(api, "GET", "/api/v1/notifications", null));
        assertThrows(IllegalStateException.class, () -> call(api, "POST", "/api/v1/orders/" + orderId + "/cancellation-request",
            Map.of("reason", "Thử")));
        logIn(username, PASSWORD);
        driver.get(baseUrl + "/customer/orders/" + orderId);
        WebElement current = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#orderTimeline li[aria-current='step']")));
        assertTrue(current.getText().contains("Chờ lấy"), current.getText());
        assertTrue(driver.findElement(By.id("orderTimeline")).getText().contains("Đã nhận"), "last step reads Đã nhận for pickup");
        assertFalse(driver.findElement(By.id("orderTimeline")).getText().contains("Đang giao"), "no shipping step for pickup");
        WebElement customerHold = driver.findElement(By.id("pickupDeadline"));
        assertTrue(customerHold.getText().contains("vui lòng liên hệ cửa hàng"), customerHold.getText());
        assertTrue(driver.findElements(By.id("pickupDeadlineForm")).isEmpty(), "customers cannot move the deadline");

        // The customer comes in: "Đã nhận" delivers the order and records the hand-over time (numbers are recorded first).
        enterUnits(managerApi(), orderId);
        openAsManager(orderId);
        new Select(driver.findElement(STATUS_SELECT)).selectByValue("DELIVERED");
        click(By.cssSelector("#orderStatusForm button"));
        assertEquals("Trạng thái đơn hàng đã cập nhật.", notice());
        JsonNode delivered = findOrder(orderNumber);
        assertEquals("DELIVERED", delivered.path("status").asText());
        assertEquals("DELIVERED", delivered.path("shipment").path("shipmentStatus").asText());
        assertFalse(delivered.path("shipment").path("deliveredAt").isNull(), "deliveredAt is recorded: " + delivered.path("shipment"));
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("pickupDeadline")));
    }

    @Test
    void backendKeepsPickupAndHomeDeliveryOnTheirOwnPaths() throws Exception {
        HttpClient manager = managerApi();
        long pickup = placePickupOrderOverRest();
        toProcessing(manager, pickup);
        assertThrows(IllegalStateException.class, () -> status(manager, pickup, "SHIPPED"));
        assertThrows(IllegalStateException.class,
            () -> call(manager, "PATCH", "/api/v1/manager/orders/" + pickup + "/shipment", Map.of("shipmentStatus", "IN_TRANSIT")));
        assertThrows(IllegalStateException.class,
            () -> call(manager, "PATCH", "/api/v1/manager/orders/" + pickup + "/shipment", Map.of("shipmentStatus", "DELIVERED")),
            "a pickup order is received only after Chờ lấy");
        status(manager, pickup, "READY_FOR_PICKUP");
        assertThrows(IllegalStateException.class, () -> status(manager, pickup, "CANCELLED", "Khách không tới"),
            "no cancelling during the hold");
        // The shipment form may also finish it, once it is waiting.
        enterUnits(manager, pickup);
        call(manager, "PATCH", "/api/v1/manager/orders/" + pickup + "/shipment", Map.of("shipmentStatus", "DELIVERED"));
        assertEquals("DELIVERED", orderById(pickup).path("status").asText());

        String home = placePayLaterOrder();
        long homeId = findOrder(home).path("id").asLong();
        toProcessing(manager, homeId);
        assertThrows(IllegalStateException.class, () -> status(manager, homeId, "READY_FOR_PICKUP"));
        assertThrows(IllegalStateException.class,
            () -> call(manager, "PATCH", "/api/v1/manager/orders/" + homeId + "/pickup-deadline", Map.of("pickupDeadlineAt", wire(LocalDateTime.now().plusDays(3)))),
            "only waiting pickup orders have a deadline");

        logInAsManager();
        driver.get(baseUrl + "/manager/orders/" + homeId);
        wait.until(d -> statusChoices().equals(List.of("SHIPPED", "CANCELLED")));
        driver.get(baseUrl + "/manager/orders");
        Select row = new Select(orderRow(home).findElement(By.cssSelector("select[data-action='order-status']")));
        assertEquals(List.of("SHIPPED", "CANCELLED"), values(row));
    }

    @Test
    void managerMovesTheDeadlineAndTheCustomerIsTold() throws Exception {
        HttpClient manager = managerApi();
        long orderId = placePickupOrderOverRest();
        toProcessing(manager, orderId);
        status(manager, orderId, "READY_FOR_PICKUP");

        assertThrows(IllegalStateException.class,
            () -> call(manager, "PATCH", "/api/v1/manager/orders/" + orderId + "/pickup-deadline", Map.of("pickupDeadlineAt", wire(LocalDateTime.now().minusHours(1)))),
            "a deadline in the past is refused");

        openAsManager(orderId);
        LocalDateTime moved = LocalDateTime.now().plusDays(10).withHour(17).withMinute(30).withSecond(0).withNano(0);
        WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#pickupDeadlineForm input[name='pickupDeadlineAt']")));
        js("arguments[0].value=arguments[1]", input, moved.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")));
        click(By.cssSelector("#pickupDeadlineForm button"));
        assertEquals("Đã cập nhật hạn giữ xe.", notice());
        assertEquals(moved, deadlineOf(orderById(orderId)));

        String expected = moved.format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
        assertTrue(hasNotification(n -> "Đổi lịch lấy xe".equals(n.path("title").asText()) && n.path("message").asText().contains(expected)),
            "customer is told the new deadline " + expected + ": " + call(api, "GET", "/api/v1/notifications", null));
    }

    @Test
    void managerCancelsOnlyAfterTheHoldIsOver() throws Exception {
        HttpClient manager = managerApi();
        long orderId = placePickupOrderOverRest();
        toProcessing(manager, orderId);
        status(manager, orderId, "READY_FOR_PICKUP");
        int stockWhileHeld = productStock();

        // Shorten the hold to a few seconds, then let it run out.
        call(manager, "PATCH", "/api/v1/manager/orders/" + orderId + "/pickup-deadline",
            Map.of("pickupDeadlineAt", wire(LocalDateTime.now().plusSeconds(3))));
        Thread.sleep(5_000);

        openAsManager(orderId);
        wait.until(d -> statusChoices().equals(List.of("DELIVERED", "CANCELLED")));
        new Select(driver.findElement(STATUS_SELECT)).selectByValue("CANCELLED");
        click(By.cssSelector("#orderStatusForm button"));
        answerPrompt("Khách không tới lấy xe");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("cancellationDetails")));

        JsonNode cancelled = orderById(orderId);
        assertEquals("CANCELLED", cancelled.path("status").asText());
        assertEquals("Khách không tới lấy xe", cancelled.path("cancellationReason").asText());
        assertEquals(stockWhileHeld + 1, productStock(), "the bike goes back in stock");
    }

    /** Orders still waiting for pickup hold stock; let their hold run out and cancel them. */
    @AfterAll
    static void cancelWaitingPickupOrders() throws Exception {
        if (api == null || System.getProperty("selenium.managerUser", "").isBlank()) {
            return;
        }
        HttpClient manager = managerApi();
        for (JsonNode order : listOf(call(api, "GET", "/api/v1/orders", null))) {
            if ("READY_FOR_PICKUP".equals(order.path("status").asText())) {
                long id = order.path("id").asLong();
                call(manager, "PATCH", "/api/v1/manager/orders/" + id + "/pickup-deadline",
                    Map.of("pickupDeadlineAt", wire(LocalDateTime.now().plusSeconds(2))));
                Thread.sleep(3_000);
                call(manager, "PATCH", "/api/v1/orders/" + id + "/status", Map.of("status", "CANCELLED", "reason", "Selenium test cleanup"));
            }
        }
    }

    // ---- steps ---------------------------------------------------------------------------

    private String placePickupOrderInTheBrowser() {
        openCheckoutWithOneBike();
        fill("customerName", "Selenium Tester");
        fill("phoneNumber", "0901234567");
        fill("customerEmail", username + "@example.com");
        chooseStorePickup();
        WebElement store = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[name='pickupShowroomId']")));
        js("arguments[0].click()", store);
        new Select(driver.findElement(By.name("paymentMethod"))).selectByValue("PAY_LATER");
        wait.until(ExpectedConditions.elementToBeClickable(By.id("placeOrder")));
        click(By.id("placeOrder"));
        wait.until(ExpectedConditions.urlContains("/checkout/success"));
        return wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".order-number"))).getText();
    }

    private static long placePickupOrderOverRest() throws Exception {
        JsonNode product = call(api, "GET", "/api/v1/products/" + productSlug, null);
        JsonNode color = null;
        for (JsonNode variant : listOf(product.path("variants"))) {
            if (color == null || variant.path("stockQuantity").asInt() > color.path("stockQuantity").asInt()) {
                color = variant;
            }
        }
        assumeTrue(color == null || color.path("stockQuantity").asInt() > 0, "every colour of " + productSlug + " is sold out");
        long showroom = listOf(call(api, "GET", "/api/v1/showrooms", null)).iterator().next().path("id").asLong();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productId", product.path("id").asLong());
        item.put("quantity", 1);
        item.put("productVariantId", color == null ? null : color.path("id").asLong());
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("customerName", "Selenium Tester");
        order.put("phoneNumber", "0901234567");
        order.put("customerEmail", username + "@example.com");
        order.put("deliveryMethod", "STORE_PICKUP");
        order.put("pickupShowroomId", showroom);
        order.put("paymentMethod", "PAY_LATER");
        order.put("items", List.of(item));
        return call(api, "POST", "/api/v1/orders", order).path("id").asLong();
    }

    private static void toProcessing(HttpClient manager, long orderId) throws Exception {
        status(manager, orderId, "CONFIRMED");
        status(manager, orderId, "PROCESSING");
    }

    private static JsonNode status(HttpClient manager, long orderId, String status) throws Exception {
        return call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", status));
    }

    private static JsonNode status(HttpClient manager, long orderId, String status, String reason) throws Exception {
        return call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", status, "reason", reason));
    }

    private static JsonNode orderById(long orderId) throws Exception {
        return call(api, "GET", "/api/v1/orders/" + orderId, null);
    }

    private static LocalDateTime deadlineOf(JsonNode order) {
        String text = order.path("pickupDeadlineAt").asText();
        assertFalse(text.isBlank(), "pickupDeadlineAt is set: " + order);
        return LocalDateTime.parse(text).withNano(0);
    }

    private static String wire(LocalDateTime time) {
        return time.format(WIRE);
    }

    private static boolean hasNotification(java.util.function.Predicate<JsonNode> match) throws Exception {
        // Notifications are written after the order transaction commits; give the listener a moment.
        for (int attempt = 0; attempt < 10; attempt++) {
            for (JsonNode notification : listOf(call(api, "GET", "/api/v1/notifications", null))) {
                if (match.test(notification)) {
                    return true;
                }
            }
            Thread.sleep(500);
        }
        return false;
    }

    private static int productStock() throws Exception {
        return call(api, "GET", "/api/v1/products/" + productSlug, null).path("stockQuantity").asInt();
    }

    private void logInAsManager() {
        logIn(System.getProperty("selenium.managerUser"), System.getProperty("selenium.managerPassword"));
    }

    private void openAsManager(long orderId) {
        logInAsManager();
        driver.get(baseUrl + "/manager/orders/" + orderId);
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("orderManage")));
    }

    private List<String> statusChoices() {
        return values(new Select(driver.findElement(STATUS_SELECT)));
    }

    private static List<String> values(Select select) {
        return select.getOptions().stream().map(o -> o.getAttribute("value")).filter(v -> !v.isEmpty()).toList();
    }

    private String selectedText(String value) {
        return new Select(driver.findElement(STATUS_SELECT)).getOptions().stream()
            .filter(o -> value.equals(o.getAttribute("value"))).findFirst().orElseThrow().getText();
    }
}
