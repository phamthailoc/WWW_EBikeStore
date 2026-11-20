package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * V20: an order is delivered with frame numbers, the customer sees the warranty slip with the right end date, anyone can look
 * the frame number up (no owner data), the customer sends a warranty request, the manager moves it along and the customer is
 * notified. The daily maintenance reminder job has no trigger a browser can reach; it is covered by unit tests of its rules.
 */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class WarrantySeleniumTest extends StorefrontSeleniumTest {

    private static final By DIALOG_TEXT = By.cssSelector("dialog.warranty-dialog textarea[name=text]");

    private WebElement rowWith(String text) {
        return wait.until(d -> d.findElements(By.cssSelector("#data-table tbody tr")).stream()
            .filter(row -> row.getText().contains(text)).findFirst().orElse(null));
    }

    private void submitDialog(String text) {
        WebElement box = wait.until(ExpectedConditions.visibilityOfElementLocated(DIALOG_TEXT));
        box.sendKeys(text);
        click(By.cssSelector("dialog.warranty-dialog button[data-submit]"));
        wait.until(ExpectedConditions.invisibilityOfElementLocated(DIALOG_TEXT));
    }

    private static int notificationsOfType(String type) throws Exception {
        int count = 0;
        for (JsonNode row : listOf(call(api, "GET", "/api/v1/notifications", null))) {
            if (type.equals(row.path("type").asText())) {
                count++;
            }
        }
        return count;
    }

    @Test
    void warrantyBookLookupClaimAndStaffUpdate() throws Exception {
        HttpClient manager = managerApi(); // skips without a manager account: needed to deliver the order and handle the claim

        // 1. deliver a home-delivery order with frame numbers (the plan: warranty starts at the delivery)
        String orderNumber = placePayLaterOrder();
        long orderId = findOrder(orderNumber).path("id").asLong();
        for (String status : new String[] {"CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED"}) {
            if (status.equals("SHIPPED")) enterUnits(manager, orderId);
            call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", status));
        }
        JsonNode order = call(manager, "GET", "/api/v1/orders/" + orderId, null);
        String frame = order.path("items").get(0).path("units").get(0).path("frameNumber").asText();
        assertFalse(frame.isBlank(), "the delivered order has a frame number");
        JsonNode product = call(api, "GET", "/api/v1/products/" + productSlug, null);
        JsonNode monthsNode = product.path("specification").path("warrantyMonths");
        String startText = order.path("shipment").path("deliveredAt").asText(order.path("updatedAt").asText());
        String expectedEnd = monthsNode.isNumber()
            ? LocalDate.parse(startText.substring(0, 10)).plusMonths(monthsNode.asInt()).toString() : null;

        // 2. the customer's warranty book lists the bike with the right end date and offers the request button
        driver.get(baseUrl + "/customer/warranty");
        WebElement row = rowWith(frame);
        if (expectedEnd != null) {
            assertEquals(expectedEnd, row.findElement(By.cssSelector("td[data-field='warrantyEndsAt']")).getText(), row.getText());
            assertTrue(row.findElement(By.cssSelector("td[data-field='coverage']")).getText().contains("Còn bảo hành"), row.getText());
        }
        assertFalse(row.findElements(By.cssSelector("[data-action='warranty-claim']")).isEmpty(), "a bike with no open request offers the button");
        assertFalse(driver.findElements(By.cssSelector("#data-table th")).isEmpty());

        // 3. public lookup: no sign-in, any case and spaces, answers with the model and dates only
        HttpClient anonymous = newClient();
        JsonNode lookup = call(anonymous, "GET", "/api/v1/warranty/lookup?frame=" + frame, null);
        assertEquals(frame, lookup.path("frameNumber").asText());
        if (expectedEnd != null) {
            assertEquals("ACTIVE", lookup.path("coverage").asText());
        }
        List<String> keys = new ArrayList<>();
        lookup.fieldNames().forEachRemaining(keys::add);
        assertEquals(java.util.Set.of("frameNumber", "productName", "colorName", "warrantyMonths", "warrantyStartsAt", "warrantyEndsAt", "coverage"),
            new java.util.HashSet<>(keys), "the public answer has the bike and its dates only, no owner data");
        assertThrows(IllegalStateException.class, () -> call(anonymous, "GET", "/api/v1/warranty/lookup?frame=ZZZ-NO-SUCH-0001", null));
        assertThrows(IllegalStateException.class, () -> call(anonymous, "GET", "/api/v1/warranty/units", null)); // the book itself needs sign-in

        driver.get(baseUrl + "/warranty");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("warrantyLookupForm")));
        fill("frame", " " + frame.toLowerCase() + " ");
        click(By.cssSelector("#warrantyLookupForm button[type=submit]"));
        WebElement found = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#warrantyLookupResult [data-field='frameNumber']")));
        assertEquals(frame, found.getText());
        if (expectedEnd != null) {
            assertEquals(expectedEnd, driver.findElement(By.cssSelector("#warrantyLookupResult [data-field='warrantyEndsAt']")).getAttribute("data-iso"));
        }
        fill("frame", "ZZZ-NO-SUCH-0001");
        click(By.cssSelector("#warrantyLookupForm button[type=submit]"));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#warrantyLookupResult .notice.error")));

        // 4. the customer sends a request: too short is refused in the form, then it is accepted
        driver.get(baseUrl + "/customer/warranty");
        click(rowWith(frame).findElement(By.cssSelector("[data-action='warranty-claim']")));
        wait.until(ExpectedConditions.visibilityOfElementLocated(DIALOG_TEXT)).sendKeys("lỗi");
        click(By.cssSelector("dialog.warranty-dialog button[data-submit]"));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("dialog.warranty-dialog .support-message")));
        driver.findElement(DIALOG_TEXT).clear();
        submitDialog("Selenium: pin sạc không đầy, đèn báo nhấp nháy");
        assertTrue(notice().contains("Đã gửi yêu cầu bảo hành"), notice());
        WebElement sent = wait.until(d -> d.findElements(By.cssSelector("#warrantyClaims tbody tr")).stream()
            .filter(r -> r.getText().contains(frame)).findFirst().orElse(null));
        assertTrue(sent.getText().contains("Mới gửi"), sent.getText());
        wait.until(d -> rowWith(frame).findElements(By.cssSelector("[data-action='warranty-claim']")).isEmpty());
        // a second open request for the same bike is refused by the server
        long unitId = unitIdOf(frame);
        assertThrows(IllegalStateException.class, () -> call(api, "POST", "/api/v1/warranty/claims",
            Map.of("unitId", unitId, "description", "Selenium: yêu cầu thứ hai khi yêu cầu đầu chưa xong")));

        // 5. the manager receives it with a note
        int before = notificationsOfType("WARRANTY_CLAIM");
        logIn(System.getProperty("selenium.managerUser"), System.getProperty("selenium.managerPassword"));
        driver.get(baseUrl + "/manager/warranty");
        WebElement queued = rowWith(frame);
        assertTrue(queued.getText().contains("Selenium: pin sạc không đầy"), queued.getText());
        click(queued.findElement(By.cssSelector("[data-action='warranty-status'][data-status='RECEIVED']")));
        submitDialog("Selenium: mời anh mang xe tới cửa hàng");
        wait.until(d -> rowWith(frame).getText().contains("Đã tiếp nhận"));

        // 6. later steps over the API (REPAIRING -> DONE); a finished request cannot move back
        long claimId = claimIdOf(manager, frame);
        call(manager, "PATCH", "/api/v1/manager/warranty-claims/" + claimId, Map.of("status", "REPAIRING"));
        call(manager, "PATCH", "/api/v1/manager/warranty-claims/" + claimId, Map.of("status", "DONE", "staffNote", "Đã thay bộ sạc"));
        assertThrows(IllegalStateException.class, () -> call(manager, "PATCH", "/api/v1/manager/warranty-claims/" + claimId, Map.of("status", "REPAIRING")));

        // 7. the customer was notified of each change and sees the staff note in the warranty book
        wait.until(d -> {
            try {
                return notificationsOfType("WARRANTY_CLAIM") >= before + 3;
            } catch (Exception e) {
                return false;
            }
        });
        logIn(username, PASSWORD);
        driver.get(baseUrl + "/customer/notifications");
        WebElement link = wait.until(d -> d.findElements(By.cssSelector("#data-table a.table-action[href='/customer/warranty']")).stream().findFirst().orElse(null));
        click(link);
        wait.until(d -> d.getCurrentUrl().endsWith("/customer/warranty"));
        WebElement done = wait.until(d -> d.findElements(By.cssSelector("#warrantyClaims tbody tr")).stream()
            .filter(r -> r.getText().contains(frame) && r.getText().contains("Đã hoàn tất")).findFirst().orElse(null));
        assertTrue(done.getText().contains("Đã thay bộ sạc"), done.getText());
        // finished: the bike offers the button again
        assertFalse(rowWith(frame).findElements(By.cssSelector("[data-action='warranty-claim']")).isEmpty());
    }

    private static long unitIdOf(String frame) throws Exception {
        for (JsonNode unit : listOf(call(api, "GET", "/api/v1/warranty/units", null))) {
            if (frame.equals(unit.path("frameNumber").asText())) {
                return unit.path("id").asLong();
            }
        }
        throw new AssertionError("frame " + frame + " not in the warranty book");
    }

    private static long claimIdOf(HttpClient manager, String frame) throws Exception {
        for (JsonNode claim : listOf(call(manager, "GET", "/api/v1/manager/warranty-claims", null))) {
            if (frame.equals(claim.path("frameNumber").asText())) {
                return claim.path("id").asLong();
            }
        }
        throw new AssertionError("no warranty request for " + frame);
    }
}
