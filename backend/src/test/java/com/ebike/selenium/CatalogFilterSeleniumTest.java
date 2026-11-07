package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

/** V10: the catalog filters by vehicle type and range, and keeps every filter when moving to the next page. */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class CatalogFilterSeleniumTest extends StorefrontSeleniumTest {

    private int productCount() {
        return Integer.parseInt(driver.findElement(By.cssSelector(".catalog-count b")).getText().trim());
    }

    @Test
    void vehicleTypeAndRangeNarrowTheCatalogAndSurvivePaging() {
        driver.get(baseUrl + "/products");
        int all = productCount();

        WebElement type = driver.findElement(By.name("vehicleType"));
        new Select(type).selectByValue("E_SCOOTER");
        js("arguments[0].form.submit()", type);
        wait.until(ExpectedConditions.urlContains("vehicleType=E_SCOOTER"));
        int scooters = productCount();
        assertTrue(scooters > 0 && scooters < all, "filtering by type narrows the list: " + scooters + " of " + all);
        assertEquals("E_SCOOTER", new Select(driver.findElement(By.name("vehicleType"))).getFirstSelectedOption().getAttribute("value"));

        List<WebElement> next = driver.findElements(By.xpath("//nav[contains(@class,'pagination')]//a[normalize-space()='2']"));
        assumeTrue(!next.isEmpty(), "only one page of scooters; paging cannot be checked");
        click(next.get(0));
        wait.until(ExpectedConditions.urlContains("page=2"));
        assertTrue(driver.getCurrentUrl().contains("vehicleType=E_SCOOTER"), "page 2 keeps the filter: " + driver.getCurrentUrl());
        assertEquals(scooters, productCount(), "page 2 counts the same filtered list");
        assertEquals("E_SCOOTER", new Select(driver.findElement(By.name("vehicleType"))).getFirstSelectedOption().getAttribute("value"));

        driver.get(baseUrl + "/products?vehicleType=E_SCOOTER&minRangeKm=80");
        assertTrue(productCount() <= scooters, "a minimum range can only narrow the list further");
        assertEquals("80", driver.findElement(By.name("minRangeKm")).getAttribute("value"), "the range stays in the form");
    }
}
