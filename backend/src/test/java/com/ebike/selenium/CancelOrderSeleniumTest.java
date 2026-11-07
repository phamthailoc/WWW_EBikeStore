package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * UC-14: a customer cancels their own order with a reason, the order is cancelled immediately and
 * the reason is shown on the order detail page. Tests that move an order to PROCESSING or SHIPPED
 * need {@code -Dselenium.managerUser} and {@code -Dselenium.managerPassword}; they are skipped otherwise.
 *
 * <pre>
 * mvn test -Dtest=CancelOrderSeleniumTest -Dselenium.baseUrl=http://localhost:8080
 * </pre>
 */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class CancelOrderSeleniumTest extends StorefrontSeleniumTest {

    private static final By CANCEL_BUTTON = By.cssSelector("[data-action='cancel-order']");

    @Test
    void customerCancelsPendingOrderWithReasonAndStockIsRestored() throws Exception {
        String orderNumber = placePayLaterOrder();
        int stockAfterOrder = productStock();

        driver.get(baseUrl + "/customer/orders");
        WebElement cancel = orderRow(orderNumber).findElement(CANCEL_BUTTON);
        assertEquals("Hủy đơn", cancel.getText());
        click(cancel);
        answerPrompt("Đổi ý, muốn chọn màu khác");

        assertEquals("Đã hủy đơn hàng.", notice());
        wait.until(d -> orderRow(orderNumber).getText().contains("CANCELLED"));
        assertTrue(orderRow(orderNumber).findElements(CANCEL_BUTTON).isEmpty(), "a cancelled order must not offer cancel again");
        assertEquals(stockAfterOrder + 1, productStock(), "cancelling must put the bike back in stock");
    }

    @Test
    void orderDetailShowsCancelButtonAndThenTheReason() throws Exception {
        String orderNumber = placePayLaterOrder();
        long orderId = findOrder(orderNumber).path("id").asLong();

        driver.get(baseUrl + "/customer/orders/" + orderId);
        assertTrue(driver.findElements(By.id("cancellationDetails")).isEmpty(), "no cancellation panel before cancelling");
        click(CANCEL_BUTTON);
        answerPrompt("Muốn đổi sang giao tận nhà");

        WebElement panel = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("cancellationDetails")));
        assertTrue(panel.getText().contains("Thông tin hủy đơn"));
        assertTrue(panel.getText().contains("Muốn đổi sang giao tận nhà"));
        assertTrue(panel.getText().contains("Thời điểm hủy:"));
        assertFalse(panel.getText().contains("Invalid Date"), "cancellation time must be readable: " + panel.getText());
    }

    @Test
    void dismissingThePromptKeepsTheOrder() throws Exception {
        String orderNumber = placePayLaterOrder();

        driver.get(baseUrl + "/customer/orders");
        click(orderRow(orderNumber).findElement(CANCEL_BUTTON));
        wait.until(ExpectedConditions.alertIsPresent()).dismiss();

        assertEquals("PENDING", findOrder(orderNumber).path("status").asText());
    }

    @Test
    void blankReasonIsRejectedAndOrderStaysOpen() throws Exception {
        String orderNumber = placePayLaterOrder();

        driver.get(baseUrl + "/customer/orders");
        click(orderRow(orderNumber).findElement(CANCEL_BUTTON));
        answerPrompt("   ");

        assertEquals("Vui lòng nhập lý do hủy đơn hàng.", notice());
        assertTrue(driver.findElement(By.id("notice")).getAttribute("class").contains("error"));
        assertEquals("PENDING", findOrder(orderNumber).path("status").asText());
    }

    @Test
    void processingOrderCanStillBeCancelled() throws Exception {
        HttpClient manager = managerApi();
        String orderNumber = placePayLaterOrder();
        long orderId = findOrder(orderNumber).path("id").asLong();
        moveThrough(manager, orderId, "CONFIRMED", "PROCESSING");

        driver.get(baseUrl + "/customer/orders");
        click(orderRow(orderNumber).findElement(CANCEL_BUTTON));
        answerPrompt("Chờ lâu quá");

        assertEquals("Đã hủy đơn hàng.", notice());
        assertEquals("CANCELLED", findOrder(orderNumber).path("status").asText());
    }

    @Test
    void shippedOrderOffersNoCancelButton() throws Exception {
        HttpClient manager = managerApi();
        String orderNumber = placePayLaterOrder();
        long orderId = findOrder(orderNumber).path("id").asLong();
        moveThrough(manager, orderId, "CONFIRMED", "PROCESSING", "SHIPPED");

        driver.get(baseUrl + "/customer/orders");
        assertTrue(orderRow(orderNumber).findElements(CANCEL_BUTTON).isEmpty());
    }

    @Test
    void processingOrderWhoseBikeIsAlreadyOnTheWayOffersNoCancelButton() throws Exception {
        HttpClient manager = managerApi();
        String orderNumber = placePayLaterOrder();
        long orderId = findOrder(orderNumber).path("id").asLong();
        moveThrough(manager, orderId, "CONFIRMED", "PROCESSING");
        call(manager, "PATCH", "/api/v1/manager/orders/" + orderId + "/shipment", Map.of("shipmentStatus", "IN_TRANSIT"));

        driver.get(baseUrl + "/customer/orders");
        // The server refuses to cancel once the bike is on the way. Moving the shipment to IN_TRANSIT also
        // moves the order to SHIPPED, so the status-based button rule must hide the button here.
        assertTrue(orderRow(orderNumber).findElements(CANCEL_BUTTON).isEmpty(),
            "cancel is offered although the server will reject it");
    }

    @Test
    void managerSeesTheCancellationReason() throws Exception {
        String user = System.getProperty("selenium.managerUser", "");
        String password = System.getProperty("selenium.managerPassword", "");
        managerApi(); // skips when no manager account is configured
        String orderNumber = placePayLaterOrder();
        long orderId = findOrder(orderNumber).path("id").asLong();
        call(api, "POST", "/api/v1/orders/" + orderId + "/cancellation-request", Map.of("reason", "Khách gọi điện xin hủy"));

        logIn(user, password);
        driver.get(baseUrl + "/manager/orders/" + orderId);

        WebElement panel = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("cancellationDetails")));
        assertTrue(panel.getText().contains("Khách gọi điện xin hủy"));
        assertTrue(driver.findElements(CANCEL_BUTTON).isEmpty(), "managers cancel through the status menu, not the customer button");
    }

    // ---- steps ---------------------------------------------------------------------------

    /** The server only allows one forward step at a time, so walk the order through each status in turn. */
    private static void moveThrough(HttpClient manager, long orderId, String... statuses) throws Exception {
        for (String status : statuses) {
            call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", status));
        }
    }

    /** Stock of the test product as the storefront API reports it. */
    private static int productStock() throws Exception {
        JsonNode product = call(api, "GET", "/api/v1/products/" + productSlug, null);
        return product.path("stockQuantity").asInt();
    }
}
