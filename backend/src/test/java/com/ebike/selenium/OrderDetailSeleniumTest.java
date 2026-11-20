package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

/**
 * UC-13 and UC-16: the order detail page lists the bikes and the price breakdown, and the Manager can
 * only take valid next steps, fill in the shipment and cancel with a reason (stock goes back).
 * Needs {@code -Dselenium.managerUser} and {@code -Dselenium.managerPassword}; skipped otherwise.
 *
 * <pre>
 * mvn test -Dtest=OrderDetailSeleniumTest -Dselenium.baseUrl=http://localhost:8080 \
 *     -Dselenium.managerUser=... -Dselenium.managerPassword=...
 * </pre>
 */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class OrderDetailSeleniumTest extends StorefrontSeleniumTest {

    private static final By STATUS_SELECT = By.cssSelector("#orderStatusForm select");

    @Test
    void customerSeesBikesPriceBreakdownAndDeliveryOnTheDetailPage() throws Exception {
        String orderNumber = placePayLaterOrder();
        JsonNode order = findOrder(orderNumber);
        driver.get(baseUrl + "/customer/orders/" + order.path("id").asLong());

        WebElement items = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("orderItems")));
        JsonNode item = order.path("items").get(0);
        assertTrue(items.getText().contains(item.path("productName").asText()), items.getText());
        assertTrue(items.getText().contains(money(item.path("unitPrice").asLong())), items.getText());

        WebElement pricing = driver.findElement(By.id("orderPricing"));
        assertTrue(pricing.getText().contains("Tạm tính"), pricing.getText());
        assertTrue(pricing.getText().contains("Tổng cộng"), pricing.getText());
        assertTrue(pricing.getText().contains(money(order.path("totalAmount").asLong())), pricing.getText());
        assertTrue(pricing.getText().contains("PAY_LATER"), "payment method: " + pricing.getText());

        assertTrue(driver.findElement(By.id("shipmentDetails")).getText().contains("Thông tin nhận hàng"));
        assertTrue(driver.findElements(By.id("orderManage")).isEmpty(), "customers get no staff controls");
    }

    @Test
    void managerIsOfferedOnlyTheValidNextStatuses() throws Exception {
        managerApi();
        String orderNumber = placePayLaterOrder();
        long orderId = findOrder(orderNumber).path("id").asLong();

        openAsManager(orderId);
        assertEquals(List.of("CONFIRMED", "CANCELLED"), statusChoices());

        new Select(driver.findElement(STATUS_SELECT)).selectByValue("CONFIRMED");
        click(By.cssSelector("#orderStatusForm button"));
        assertEquals("Trạng thái đơn hàng đã cập nhật.", notice());
        wait.until(d -> statusChoices().equals(List.of("PROCESSING", "CANCELLED")));
        assertEquals("CONFIRMED", findOrder(orderNumber).path("status").asText());
    }

    @Test
    void managerOrderListDropdownAlsoHidesInvalidSteps() throws Exception {
        managerApi();
        String orderNumber = placePayLaterOrder();

        logInAsManager();
        driver.get(baseUrl + "/manager/orders");
        Select dropdown = new Select(orderRow(orderNumber).findElement(By.cssSelector("select[data-action='order-status']")));
        List<String> values = dropdown.getOptions().stream().map(o -> o.getAttribute("value")).filter(v -> !v.isEmpty()).toList();
        assertEquals(List.of("CONFIRMED", "CANCELLED"), values);
    }

    @Test
    void managerCancelsFromTheDetailPageWithAReasonAndStockComesBack() throws Exception {
        managerApi();
        String orderNumber = placePayLaterOrder();
        long orderId = findOrder(orderNumber).path("id").asLong();
        int stockAfterOrder = productStock();

        openAsManager(orderId);
        new Select(driver.findElement(STATUS_SELECT)).selectByValue("CANCELLED");
        click(By.cssSelector("#orderStatusForm button"));
        answerPrompt("Hết hàng màu đã chọn");

        WebElement panel = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("cancellationDetails")));
        assertTrue(panel.getText().contains("Hết hàng màu đã chọn"), panel.getText());
        assertEquals("CANCELLED", findOrder(orderNumber).path("status").asText());
        assertEquals(stockAfterOrder + 1, productStock(), "the bike must go back in stock");
        assertTrue(driver.findElements(By.id("orderManage")).isEmpty(), "a cancelled order offers no more staff steps");
    }

    @Test
    void managerCannotCancelWithoutAReason() throws Exception {
        HttpClient manager = managerApi();
        String orderNumber = placePayLaterOrder();
        long orderId = findOrder(orderNumber).path("id").asLong();

        assertThrows(IllegalStateException.class,
            () -> call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", "CANCELLED")));
        assertEquals("PENDING", findOrder(orderNumber).path("status").asText());
    }

    @Test
    void anOrderCannotSkipStepsOrLeaveCancelled() throws Exception {
        HttpClient manager = managerApi();
        String orderNumber = placePayLaterOrder();
        long orderId = findOrder(orderNumber).path("id").asLong();

        assertThrows(IllegalStateException.class,
            () -> call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", "DELIVERED")));
        assertThrows(IllegalStateException.class,
            () -> call(manager, "PATCH", "/api/v1/manager/orders/" + orderId + "/shipment", Map.of("shipmentStatus", "DELIVERED")));
        call(api, "POST", "/api/v1/orders/" + orderId + "/cancellation-request", Map.of("reason", "Thử"));
        assertThrows(IllegalStateException.class,
            () -> call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", "CONFIRMED")));
    }

    @Test
    void managerFillsInTheShipmentAndTheCustomerSeesIt() throws Exception {
        HttpClient manager = managerApi();
        String orderNumber = placePayLaterOrder();
        long orderId = findOrder(orderNumber).path("id").asLong();
        call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", "CONFIRMED"));
        call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", "PROCESSING"));
        enterUnits(manager, orderId);

        openAsManager(orderId);
        new Select(driver.findElement(By.cssSelector("#shipmentForm select"))).selectByValue("IN_TRANSIT");
        WebElement tracking = driver.findElement(By.cssSelector("#shipmentForm input[name='trackingNumber']"));
        tracking.clear();
        tracking.sendKeys("VN-TEST-123");
        click(By.cssSelector("#shipmentForm button"));

        assertEquals("Đã cập nhật thông tin giao nhận.", notice());
        wait.until(d -> d.findElement(By.id("shipmentDetails")).getText().contains("VN-TEST-123"));
        assertEquals("SHIPPED", findOrder(orderNumber).path("status").asText());
        wait.until(d -> statusChoices().equals(List.of("DELIVERED")));

        logIn(username, PASSWORD);
        driver.get(baseUrl + "/customer/orders/" + orderId);
        WebElement delivery = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("shipmentDetails")));
        assertTrue(delivery.getText().contains("VN-TEST-123"), delivery.getText());
        assertTrue(delivery.getText().contains("Bàn giao vận chuyển"), delivery.getText());
    }

    // ---- steps ---------------------------------------------------------------------------

    private void logInAsManager() {
        logIn(System.getProperty("selenium.managerUser"), System.getProperty("selenium.managerPassword"));
    }

    private void openAsManager(long orderId) {
        logInAsManager();
        driver.get(baseUrl + "/manager/orders/" + orderId);
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("orderManage")));
    }

    private List<String> statusChoices() {
        return new Select(driver.findElement(STATUS_SELECT)).getOptions().stream().map(o -> o.getAttribute("value")).toList();
    }

    /** Same grouping as the page's {@code Intl.NumberFormat('vi-VN')}: 20.300.000 ₫. */
    private static String money(long amount) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(amount);
    }

    private static int productStock() throws Exception {
        return call(api, "GET", "/api/v1/products/" + productSlug, null).path("stockQuantity").asInt();
    }
}
