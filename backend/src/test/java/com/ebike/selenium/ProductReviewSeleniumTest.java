package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/** V8: the product page shows related bikes and reviews; only a buyer who received the bike can review it. */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class ProductReviewSeleniumTest extends StorefrontSeleniumTest {

    private static final By FORM_AREA = By.cssSelector("#reviews [data-form-area]");

    private void openProductPage() {
        driver.get(baseUrl + "/product/" + productSlug);
        wait.until(d -> !d.findElement(FORM_AREA).getText().isBlank());
    }

    @Test
    void onlyABuyerWhoReceivedTheBikeCanReviewIt() throws Exception {
        HttpClient manager = managerApi(); // skips without a manager account: needed to deliver the order
        openProductPage();
        var related = driver.findElements(By.cssSelector(".related-grid article.product a[href*='/product/']"));
        assertTrue(!related.isEmpty() && related.stream().noneMatch(a -> a.getAttribute("href").endsWith("/" + productSlug)),
            "related bikes are listed and do not repeat this bike");
        assertTrue(driver.findElements(By.cssSelector("#reviews form.review-form")).isEmpty(),
            "no review form before the bike is received: " + driver.findElement(FORM_AREA).getText());

        String orderNumber = placePayLaterOrder();
        long id = findOrder(orderNumber).path("id").asLong();
        for (String status : new String[] {"CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED"}) {
            if (status.equals("DELIVERED")) enterUnits(manager, id);
            call(manager, "PATCH", "/api/v1/orders/" + id + "/status", Map.of("status", status));
        }

        openProductPage();
        WebElement form = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#reviews form.review-form")));
        click(form.findElement(By.cssSelector("label[for='star4']")));
        String comment = "Selenium: xe chạy êm, pin ổn " + System.currentTimeMillis();
        form.findElement(By.name("comment")).sendKeys(comment);
        click(form.findElement(By.cssSelector("button[type=submit]")));

        WebElement mine = wait.until(d -> d.findElements(By.cssSelector("#reviews li[data-id]")).stream()
            .filter(li -> li.getText().contains(comment)).findFirst().orElse(null));
        assertTrue(mine.getText().contains("Đã mua hàng"), "a verified buyer's review is labelled: " + mine.getText());

        // Clean up: remove the review so the product page does not keep test data.
        click(mine.findElement(By.cssSelector("[data-do='delete']")));
        wait.until(ExpectedConditions.alertIsPresent()).accept();
        wait.until(d -> d.findElements(By.cssSelector("#reviews li[data-id]")).stream().noneMatch(li -> li.getText().contains(comment)));
    }
}
