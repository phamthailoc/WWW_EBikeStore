package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

/** V13 order timeline, V11 unread badge and "mark all as read", V14 customer payment filter. */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class CustomerAreaSeleniumTest extends StorefrontSeleniumTest {

    private static final By TIMELINE = By.cssSelector("#orderTimeline ol.timeline > li");
    private static final By ROWS = By.cssSelector("#data-table tbody tr");

    private long placeAndConfirm(HttpClient manager) throws Exception {
        String orderNumber = placePayLaterOrder();
        long id = findOrder(orderNumber).path("id").asLong();
        call(manager, "PATCH", "/api/v1/orders/" + id + "/status", Map.of("status", "CONFIRMED"));
        return id;
    }

    @Test
    void timelineMarksTheCurrentStepAndEndsInTheCancellation() throws Exception {
        HttpClient manager = managerApi();
        long id = placeAndConfirm(manager);

        driver.get(baseUrl + "/customer/orders/" + id);
        wait.until(ExpectedConditions.numberOfElementsToBe(TIMELINE, 5));
        List<WebElement> steps = driver.findElements(TIMELINE);
        assertEquals("done", steps.get(0).getAttribute("class"), "the order was placed");
        assertTrue(steps.get(0).getText().contains("Chờ xử lý"), steps.get(0).getText());
        assertEquals("current", steps.get(1).getAttribute("class"), "confirmed is the current step");
        assertTrue(steps.get(1).getText().contains("Đã xác nhận"), steps.get(1).getText());
        assertEquals("todo", steps.get(4).getAttribute("class"), "not delivered yet");

        call(api, "POST", "/api/v1/orders/" + id + "/cancellation-request", Map.of("reason", "Selenium kiểm tra tiến trình"));
        driver.navigate().refresh();
        WebElement cancelled = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#orderTimeline li.cancelled")));
        assertTrue(cancelled.getText().contains("Đã hủy") && cancelled.getText().contains("Lý do: Selenium kiểm tra tiến trình"), cancelled.getText());
        assertEquals("done", driver.findElements(TIMELINE).get(1).getAttribute("class"), "the steps reached before cancelling stay done");
    }

    @Test
    void unreadBadgeCountsNotificationsAndMarkAllClearsIt() throws Exception {
        placeAndConfirm(managerApi());

        driver.get(baseUrl + "/customer");
        WebElement badge = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("notifBadge")));
        assertTrue(badge.getText().matches("\\d+|99\\+"), "the badge shows a count: " + badge.getText());

        driver.get(baseUrl + "/customer/notifications");
        click(wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[normalize-space()='Đánh dấu tất cả đã đọc']"))));
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("notifBadge")));
        assertEquals(0, call(api, "GET", "/api/v1/notifications/unread-count", null).path("unreadCount").asInt(), "nothing left unread");
    }

    @Test
    void paymentHistoryCanBeFilteredByStatus() {
        String orderNumber = placePayLaterOrder();
        driver.get(baseUrl + "/customer/payments");
        wait.until(d -> d.findElements(ROWS).stream().anyMatch(row -> row.getText().contains(orderNumber)));

        Select status = new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#filters select[name='paymentStatus']"))));
        status.selectByValue("PAID");
        wait.until(d -> d.findElements(ROWS).stream().noneMatch(row -> row.getText().contains(orderNumber)));
        status.selectByValue("PENDING");
        wait.until(d -> d.findElements(ROWS).stream().anyMatch(row -> row.getText().contains(orderNumber)));
    }
}
