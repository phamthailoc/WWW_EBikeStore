package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * V22: a colour on sale is set to 0, the customer asks to be told, asking twice keeps one row, the manager restocks the
 * colour, and the customer finds a notification that opens the bike page. The test picks its colour by id and ignores
 * rows or notifications the customer account already had from earlier runs.
 */
// The two parallel annotations below only matter if JUnit parallel execution is switched on; the project does not enable it,
// so today every test already runs one after another and these lines are a guard for the day it is.
@Tag("selenium")
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
@org.junit.jupiter.api.parallel.ResourceLock("product-stock")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class StockAlertSeleniumTest extends StorefrontSeleniumTest {

    /**
     * V22 uses the last colour on sale, not the first: the other Selenium tests move the stock of the first colour
     * (ColourStockSeleniumTest), and putting the stock back here is a real restock that tells every row still waiting.
     * A product with one colour has to share it.
     */
    private static JsonNode ownColour(JsonNode product) {
        JsonNode variants = product.path("variants");
        return variants.get(variants.size() - 1);
    }

    private static int alertsForVariant(long variantId) throws Exception {
        int count = 0;
        for (JsonNode row : listOf(call(api, "GET", "/api/v1/stock-alerts", null))) {
            if (row.path("variantId").asLong() == variantId) {
                count++;
            }
        }
        return count;
    }

    private static void setStock(HttpClient manager, long productId, long variantId, int quantity) throws Exception {
        call(manager, "PATCH", "/api/v1/manager/products/" + productId + "/variants/" + variantId + "/stock",
            Map.of("stockQuantity", quantity));
    }

    /** Back-in-stock notifications that open this bike's page. */
    private static int stockNotificationsFor(String slug) throws Exception {
        int count = 0;
        for (JsonNode row : listOf(call(api, "GET", "/api/v1/notifications", null))) {
            if ("STOCK_ALERT".equals(row.path("type").asText()) && ("/product/" + slug).equals(row.path("targetUrl").asText())) {
                count++;
            }
        }
        return count;
    }

    /** Notifications titled "Back in stock" (the English title) that open this bike's page. */
    private static int englishNotificationsFor(String slug) {
        try {
            int count = 0;
            for (JsonNode row : listOf(call(api, "GET", "/api/v1/notifications", null))) {
                if ("STOCK_ALERT".equals(row.path("type").asText()) && "Back in stock".equals(row.path("title").asText())
                    && ("/product/" + slug).equals(row.path("targetUrl").asText())) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            return -1;
        }
    }

    private static int alertsOrZero(long variantId) {
        try {
            return alertsForVariant(variantId);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int stockNotificationsOrZero(String slug) {
        try {
            return stockNotificationsFor(slug);
        } catch (Exception e) {
            return -1;
        }
    }

    private static void forgetAlert(long variantId) {
        try {
            call(api, "DELETE", "/api/v1/stock-alerts/" + variantId, null);
        } catch (Exception ignored) {
            // no row to remove
        }
    }

    @Test
    void customerIsToldWhenASoldOutColourComesBack() throws Exception {
        HttpClient manager = managerApi(); // skips without a manager account: needed to change the stock
        JsonNode product = call(api, "GET", "/api/v1/products/" + productSlug, null);
        long productId = product.path("id").asLong();
        // the API lists only colours that are on sale
        JsonNode colour = ownColour(product);
        long variantId = colour.path("id").asLong();
        String colourName = colour.path("colorName").asText();
        int original = colour.path("stockQuantity").asInt();

        turnNotificationsOn(); // another test may have left the switch off
        forgetAlert(variantId); // start without a row left by an earlier run
        int notificationsBefore = stockNotificationsFor(productSlug);
        setStock(manager, productId, variantId, 0);
        try {
            // 1. the sold-out colour offers the button; the customer registers
            driver.get(baseUrl + "/product/" + productSlug);
            click(By.cssSelector(".variant-option[data-variant-id='" + variantId + "']"));
            By notify = By.id("notifyStock");
            wait.until(ExpectedConditions.visibilityOfElementLocated(notify));
            click(notify);
            wait.until(d -> alertsOrZero(variantId) == 1);

            // 2. the button now offers to cancel; asking again through the API (twice) does not create a duplicate
            wait.until(d -> d.findElement(notify).getText().contains("Đã đăng ký"));
            call(api, "POST", "/api/v1/stock-alerts", Map.of("variantId", variantId));
            call(api, "POST", "/api/v1/stock-alerts", Map.of("variantId", variantId));
            assertEquals(1, alertsForVariant(variantId), "one waiting row per customer and colour");

            // 3. the waiting list is shown with the favourites, as waiting, before the colour is back
            driver.get(baseUrl + "/favorites");
            By row = By.cssSelector("#stock-alerts li[data-variant-id='" + variantId + "']");
            WebElement waiting = wait.until(ExpectedConditions.visibilityOfElementLocated(row));
            assertTrue(waiting.getText().contains(colourName), waiting.getText());
            assertTrue(waiting.findElement(By.cssSelector(".stock-tag")).getAttribute("class").contains("low"), waiting.getText());

            // 4. the manager adds stock: sold out -> in stock
            setStock(manager, productId, variantId, 3);

            // 5. exactly one new notification arrives, and its row links to the bike page
            wait.until(d -> stockNotificationsOrZero(productSlug) == notificationsBefore + 1);
            int expectedLinks = stockNotificationsFor(productSlug);
            By links = By.cssSelector("#data-table a.table-action[href='/product/" + productSlug + "']");
            driver.get(baseUrl + "/customer/notifications");
            wait.until(d -> d.findElements(links).size() == expectedLinks);
            WebElement link = driver.findElements(links).get(0);
            assertTrue(link.isDisplayed(), "the notification row links to the bike page");
            click(link);
            wait.until(d -> d.getCurrentUrl().endsWith("/product/" + productSlug));

            // the favourites list now shows that colour as back in stock
            driver.get(baseUrl + "/favorites");
            WebElement used = wait.until(ExpectedConditions.visibilityOfElementLocated(row));
            assertTrue(used.findElement(By.cssSelector(".stock-tag")).getAttribute("class").contains("ok"), used.getText());

            // "a top-up of a colour already in stock tells nobody" is covered by ManagerStockTest, not here: by now the
            // customer's row is already used and the notification is created asynchronously, so a check here proves nothing.

            // sold out again after the notice: the row says so and offers to wait once more
            setStock(manager, productId, variantId, 0);
            driver.get(baseUrl + "/favorites");
            WebElement again = wait.until(ExpectedConditions.visibilityOfElementLocated(row));
            wait.until(d -> d.findElement(row).findElement(By.cssSelector(".stock-tag")).getAttribute("class").contains("out"));
            assertTrue(driver.findElement(row).getText().contains("Đã hết hàng lại"), again.getText());
            click(By.cssSelector("#stock-alerts li[data-variant-id='" + variantId + "'] [data-renew-alert]"));
            wait.until(d -> alertsOrZero(variantId) == 1);
            wait.until(d -> d.findElement(row).findElement(By.cssSelector(".stock-tag")).getAttribute("class").contains("low"));

            // cancelling removes the row from the list
            click(By.cssSelector("#stock-alerts li[data-variant-id='" + variantId + "'] [data-cancel-alert]"));
            wait.until(d -> alertsOrZero(variantId) == 0);
        } finally {
            // forget the row first: putting the stock back is a real restock that would tell the waiting customer
            forgetAlert(variantId);
            setStock(manager, productId, variantId, original);
        }
    }

    /** Another test may have switched notifications off for this customer; alerts would then never arrive. */
    private static void turnNotificationsOn() throws Exception {
        long userId = call(api, "GET", "/api/v1/auth/profile", null).path("userId").asLong();
        call(api, "PUT", "/api/v1/users/" + userId + "/preferences", Map.of("notificationsEnabled", true));
    }

    private static void assertForbidden(HttpClient client, String method, String path, Object body) throws Exception {
        try {
            call(client, method, path, body);
            throw new AssertionError(method + " " + path + " must be refused for a staff account");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("403"), expected.getMessage());
        }
    }

    private static HttpClient adminApi() throws Exception {
        String user = System.getProperty("selenium.adminUser", "");
        String password = System.getProperty("selenium.adminPassword", "");
        org.junit.jupiter.api.Assumptions.assumeTrue(!user.isBlank() && !password.isBlank(), "selenium.adminUser/selenium.adminPassword not set");
        HttpClient admin = newClient();
        call(admin, "POST", "/api/v1/auth/login", Map.of("usernameOrEmail", user, "password", password));
        return admin;
    }

    @Test
    void managerCannotUseStockAlerts() throws Exception {
        HttpClient manager = managerApi();
        long variantId = ownColour(call(api, "GET", "/api/v1/products/" + productSlug, null)).path("id").asLong();
        assertForbidden(manager, "POST", "/api/v1/stock-alerts", Map.of("variantId", variantId));
        assertForbidden(manager, "GET", "/api/v1/stock-alerts", null);
        assertForbidden(manager, "DELETE", "/api/v1/stock-alerts/" + variantId, null);
    }

    @Test
    void adminCannotUseStockAlerts() throws Exception {
        HttpClient admin = adminApi();
        long variantId = ownColour(call(api, "GET", "/api/v1/products/" + productSlug, null)).path("id").asLong();
        assertForbidden(admin, "POST", "/api/v1/stock-alerts", Map.of("variantId", variantId));
        assertForbidden(admin, "GET", "/api/v1/stock-alerts", null);
        assertForbidden(admin, "DELETE", "/api/v1/stock-alerts/" + variantId, null);
    }

    @Test
    void staffDoNotSeeTheNotifyButtonEvenOnASoldOutColour() throws Exception {
        HttpClient manager = managerApi();
        assertNoNotifyButtonFor(manager, System.getProperty("selenium.managerUser"), System.getProperty("selenium.managerPassword"), true);
    }

    @Test
    void adminDoesNotSeeTheNotifyButtonEither() throws Exception {
        HttpClient manager = managerApi(); // changes the stock
        adminApi(); // skips without an admin account
        assertNoNotifyButtonFor(manager, System.getProperty("selenium.adminUser"), System.getProperty("selenium.adminPassword"), false);
    }

    private void assertNoNotifyButtonFor(HttpClient manager, String user, String password, boolean checkReturnMessage) throws Exception {
        JsonNode product = call(api, "GET", "/api/v1/products/" + productSlug, null);
        long productId = product.path("id").asLong();
        long variantId = ownColour(product).path("id").asLong();
        int original = ownColour(product).path("stockQuantity").asInt();
        setStock(manager, productId, variantId, 0);
        try {
            logIn(user, password);
            driver.get(baseUrl + "/product/" + productSlug);
            click(By.cssSelector(".variant-option[data-variant-id='" + variantId + "']"));
            wait.until(d -> d.findElement(By.id("stockHint")).getText().length() > 0);
            assertTrue(driver.findElements(By.id("notifyStock")).isEmpty(), "no notify button for " + user);
            if (checkReturnMessage) {
                // a guest who pressed the button and then signed in with a staff account is told why nothing was set
                driver.get(baseUrl + "/product/" + productSlug + "?notify=" + variantId);
                wait.until(d -> d.findElement(By.id("productNotice")).getText().contains("Chỉ tài khoản khách hàng"));
            }
            // the favourites page tells a staff account why the alert list is empty (it reads the 403 status, not the words of the body)
            driver.get(baseUrl + "/favorites");
            wait.until(d -> d.findElement(By.cssSelector("#stock-alerts")).getText().contains("Chỉ tài khoản khách hàng"));
        } finally {
            setStock(manager, productId, variantId, original);
        }
    }

    @Test
    void guestIsSentToLoginAndTheAlertIsSetOnReturn() throws Exception {
        HttpClient manager = managerApi();
        JsonNode product = call(api, "GET", "/api/v1/products/" + productSlug, null);
        long productId = product.path("id").asLong();
        long variantId = ownColour(product).path("id").asLong();
        int original = ownColour(product).path("stockQuantity").asInt();
        turnNotificationsOn();
        forgetAlert(variantId);
        setStock(manager, productId, variantId, 0);
        try {
            driver.manage().deleteAllCookies();
            driver.get(baseUrl + "/product/" + productSlug);
            click(By.cssSelector(".variant-option[data-variant-id='" + variantId + "']"));
            By notify = By.id("notifyStock");
            wait.until(ExpectedConditions.visibilityOfElementLocated(notify));
            click(notify);
            wait.until(ExpectedConditions.urlContains("/login?returnTo="));
            assertEquals(0, alertsForVariant(variantId), "a guest has no waiting row");

            fill("usernameOrEmail", username);
            fill("password", PASSWORD);
            click(By.cssSelector("#loginForm button"));
            // back on the bike page, the request the guest made is completed without another click
            wait.until(d -> d.getCurrentUrl().contains("/product/" + productSlug));
            wait.until(d -> alertsOrZero(variantId) == 1);
            wait.until(d -> d.findElement(notify).getText().contains("Đã đăng ký"));

            // pressing the button again turns the alert off
            click(notify);
            wait.until(d -> alertsOrZero(variantId) == 0);
        } finally {
            forgetAlert(variantId);
            setStock(manager, productId, variantId, original);
        }
    }

}
