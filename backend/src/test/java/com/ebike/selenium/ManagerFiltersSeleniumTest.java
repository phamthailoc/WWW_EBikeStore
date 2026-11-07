package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

/** V5: the manager narrows the payment list by status, method or text, and the customer list by text. */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class ManagerFiltersSeleniumTest extends StorefrontSeleniumTest {

    private static final By ROWS = By.cssSelector("#data-table tbody tr");

    private boolean listed(String text) {
        return driver.findElements(ROWS).stream().anyMatch(row -> row.getText().contains(text));
    }

    private void openAsManager(String path) {
        logIn(System.getProperty("selenium.managerUser"), System.getProperty("selenium.managerPassword"));
        driver.get(baseUrl + path);
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("filters")));
        wait.until(ExpectedConditions.presenceOfElementLocated(ROWS));
    }

    private void choose(String name, String value) {
        new Select(driver.findElement(By.cssSelector("#filters select[name='" + name + "']"))).selectByValue(value);
    }

    @Test
    void paymentsCanBeFilteredByStatusMethodAndSearch() throws Exception {
        managerApi(); // skips without a manager account
        String order = placePayLaterOrder();
        openAsManager("/manager/payments");
        wait.until(d -> listed(order));

        choose("paymentStatus", "PAID");
        wait.until(d -> !listed(order));
        assertFalse(listed(order), "a waiting payment is not in the paid list");

        choose("paymentStatus", "PENDING");
        choose("paymentMethod", "PAY_LATER");
        wait.until(d -> listed(order));

        choose("paymentMethod", "VNPAY");
        wait.until(d -> !listed(order));

        choose("paymentMethod", "");
        choose("paymentStatus", "");
        fill("search", order);
        // The unfiltered list already shows this order, and the search waits 300 ms after typing, so wait for one row.
        wait.until(d -> d.findElements(ROWS).size() == 1 && listed(order));
        assertTrue(driver.findElements(ROWS).size() == 1, "searching by order number leaves that order only");

        click(By.id("clearFilters"));
        assertTrue(driver.findElement(By.cssSelector("#filters input[name='search']")).getAttribute("value").isEmpty(), "clearing empties the search box");
    }

    @Test
    void customersCanBeSearched() throws Exception {
        managerApi();
        openAsManager("/manager/customers");
        fill("search", username);
        wait.until(d -> listed(username) && driver.findElements(ROWS).size() == 1);

        fill("search", "khong-co-khach-nao-ten-nhu-vay-" + System.currentTimeMillis());
        wait.until(d -> !listed(username));
        assertFalse(listed(username));
    }
}
