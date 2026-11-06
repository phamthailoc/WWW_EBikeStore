package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

/** UC-17: "Xác nhận đã thu" is offered only for pay-later payments that are still waiting, on open orders. */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class ManagerPaymentsSeleniumTest extends StorefrontSeleniumTest {

    private static final By CONFIRM = By.cssSelector("[data-action='confirm-payment']");

    @Test
    void confirmButtonOnlyForPendingPayLaterPaymentsOfOpenOrders() throws Exception {
        managerApi(); // skips without a manager account
        String open = placePayLaterOrder();
        String cancelled = placePayLaterOrder();
        long cancelledId = findOrder(cancelled).path("id").asLong();
        call(api, "POST", "/api/v1/orders/" + cancelledId + "/cancellation-request", Map.of("reason", "Selenium: đổi ý"));

        logIn(System.getProperty("selenium.managerUser"), System.getProperty("selenium.managerPassword"));
        driver.get(baseUrl + "/manager/payments");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#data-table tbody tr")));

        assertFalse(orderRow(open).findElements(CONFIRM).isEmpty(), "a waiting pay-later payment must be confirmable");
        assertTrue(orderRow(cancelled).findElements(CONFIRM).isEmpty(), "a cancelled order's payment must not be confirmable");
    }
}
