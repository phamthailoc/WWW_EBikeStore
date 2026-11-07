package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

/** V12 revenue report, V14 stock labels and filter, V7 image manager (opening and listing only: no S3 here). */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class ManagerConsoleSeleniumTest extends StorefrontSeleniumTest {

    private static final By ROWS = By.cssSelector("#data-table tbody tr");
    private static final List<String> STOCK_LABELS = List.of("Hết hàng", "Sắp hết", "Còn hàng");

    private HttpClient signInAsManager() throws Exception {
        HttpClient manager = managerApi(); // skips without a manager account
        logIn(System.getProperty("selenium.managerUser"), System.getProperty("selenium.managerPassword"));
        return manager;
    }

    private Select filter(String name) {
        return new Select(wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#filters select[name='" + name + "']"))));
    }

    @Test
    void revenueReportDrawsAChartForTheChosenPeriod() throws Exception {
        // Revenue counts by payment date, so collect one pay-later payment now; otherwise the period may be empty.
        HttpClient manager = managerApi();
        String orderNumber = placePayLaterOrder();
        long paymentId = -1;
        for (var payment : listOf(call(manager, "GET", "/api/v1/manager/payments?search=" + orderNumber, null))) {
            paymentId = payment.path("id").asLong();
        }
        assertTrue(paymentId > 0, "the new order has a payment");
        call(manager, "PATCH", "/api/v1/manager/payments/" + paymentId + "/confirm", java.util.Map.of());

        signInAsManager();
        driver.get(baseUrl + "/manager/reports");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("svg.revenue-chart")));

        filter("period").selectByValue("month");
        wait.until(d -> d.getCurrentUrl().contains("period=month") || !d.findElements(By.cssSelector("svg.revenue-chart")).isEmpty());
        WebElement chart = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("svg.revenue-chart")));
        assertTrue(chart.getAttribute("aria-label").contains("tổng"), "the chart says the total: " + chart.getAttribute("aria-label"));
    }

    @Test
    void everyProductHasAStockLabelAndTheFilterKeepsOnlyThatLabel() throws Exception {
        signInAsManager();
        driver.get(baseUrl + "/manager/inventory");
        wait.until(ExpectedConditions.presenceOfElementLocated(ROWS));
        for (WebElement row : driver.findElements(ROWS)) {
            assertTrue(STOCK_LABELS.stream().anyMatch(row.getText()::contains), "row has a stock label: " + row.getText());
        }

        filter("stockStatus").selectByValue("OK");
        wait.until(d -> d.findElements(ROWS).stream().allMatch(row -> row.getText().contains("Còn hàng")));
        filter("stockStatus").selectByValue("LOW");
        wait.until(d -> d.findElements(ROWS).stream().noneMatch(row -> row.getText().contains("Còn hàng")));
    }

    @Test
    void imageManagerListsTheProductsImages() throws Exception {
        HttpClient manager = signInAsManager();
        driver.get(baseUrl + "/manager/products");
        WebElement button = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("#data-table [data-action='images']")));
        String productId = button.getAttribute("data-id");
        int expected = 0;
        for (var ignored : listOf(call(manager, "GET", "/api/v1/admin/product-images/by-product/" + productId, null))) {
            expected++;
        }

        click(button);
        WebElement manage = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("dialog .image-manager, .image-manager")));
        if (expected == 0) {
            wait.until(d -> manage.getText().contains("chưa có ảnh"));
        } else {
            int count = expected;
            wait.until(d -> manage.findElements(By.cssSelector("[data-grid] figure")).size() == count);
        }
        assertEquals(1, manage.findElements(By.cssSelector("input[type=file][multiple]")).size(), "several images can be picked at once");
    }
}
