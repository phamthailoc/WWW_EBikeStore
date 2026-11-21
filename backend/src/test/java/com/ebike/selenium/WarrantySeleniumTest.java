package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.service.admin.AccountErasure;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 *
 * Every test puts the stock of the bike back as it was and uses its own throwaway customer where it must erase one; the shared
 * test customer is not erased (the base class still reads its orders after the tests).
 */
@Tag("selenium")
@org.junit.jupiter.api.parallel.Execution(org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
@org.junit.jupiter.api.parallel.ResourceLock("product-stock")
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
        Map<Long, Integer> stockBefore = stockOf(productSlug);
        try {
            walkThroughTheWarrantyBook(manager);
        } finally {
            restoreStock(manager, productSlug, stockBefore);
        }
    }

    private void walkThroughTheWarrantyBook(HttpClient manager) throws Exception {
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

        // 4b. the frame number of a vehicle with a warranty request is its identity: changing it is refused, a new engine number is not
        long itemId = order.path("items").get(0).path("id").asLong();
        String engine = order.path("items").get(0).path("units").get(0).path("engineNumber").asText();
        IllegalStateException changed = assertThrows(IllegalStateException.class, () -> call(manager, "PUT", "/api/v1/manager/orders/" + orderId + "/units",
            Map.of("units", List.of(Map.of("orderItemId", itemId, "frameNumber", frame + "X", "engineNumber", engine)))));
        assertTrue(changed.getMessage().contains("409"), changed.getMessage());
        assertEquals(frame, call(manager, "GET", "/api/v1/orders/" + orderId, null).path("items").get(0).path("units").get(0).path("frameNumber").asText());
        call(manager, "PUT", "/api/v1/manager/orders/" + orderId + "/units",
            Map.of("units", List.of(Map.of("orderItemId", itemId, "frameNumber", frame, "engineNumber", engine))));
        assertEquals(unitId, unitIdOf(frame), "saving the numbers again keeps the vehicle row, and the request with it");
        // 4c. who may do what: another customer cannot claim this bike or see it, a customer cannot open the staff queue, a visitor has no book
        HttpClient other = newClient();
        String otherName = "seleniumwar" + System.nanoTime();
        call(other, "POST", "/api/v1/auth/register", Map.of("username", otherName, "email", otherName + "@example.com",
            "password", PASSWORD, "firstName", "Selenium", "lastName", "Other"));
        call(other, "POST", "/api/v1/auth/login", Map.of("usernameOrEmail", otherName, "password", PASSWORD));
        assertTrue(assertThrows(IllegalStateException.class, () -> call(other, "POST", "/api/v1/warranty/claims",
            Map.of("unitId", unitId, "description", "Selenium: xe không phải của tôi"))).getMessage().contains("404"));
        assertFalse(listOf(call(other, "GET", "/api/v1/warranty/units", null)).iterator().hasNext(), "another customer's book is empty");
        assertTrue(assertThrows(IllegalStateException.class, () -> call(other, "GET", "/api/v1/manager/warranty-claims", null)).getMessage().contains("403"));
        assertTrue(assertThrows(IllegalStateException.class, () -> call(api, "PATCH", "/api/v1/manager/warranty-claims/1", Map.of("status", "RECEIVED"))).getMessage().contains("403"));
        assertTrue(assertThrows(IllegalStateException.class, () -> call(anonymous, "GET", "/api/v1/warranty/claims", null)).getMessage().contains("401"));
        assertTrue(assertThrows(IllegalStateException.class, () -> call(api, "POST", "/api/v1/warranty/claims",
            Map.of("unitId", unitId, "description", "ngắn"))).getMessage().contains("400"), "a description that is too short is refused by the server too");

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

        // 8. the staff queue is paged and filtered on the server
        assertTrue(listSize(call(manager, "GET", "/api/v1/manager/warranty-claims?size=1", null)) <= 1);
        assertTrue(mentions(call(manager, "GET", "/api/v1/manager/warranty-claims?status=DONE&size=100", null), frame));
        assertTrue(assertThrows(IllegalStateException.class, () -> call(manager, "GET", "/api/v1/manager/warranty-claims?status=BOGUS", null))
            .getMessage().contains("400"));
        // 9. a new request on the same bike is possible once the last one is closed; rejecting needs a reason
        call(api, "POST", "/api/v1/warranty/claims", Map.of("unitId", unitId, "description", "Selenium: lỗi mới sau khi sửa xong"));
        long second = claimIdOf(manager, frame);
        assertTrue(second != claimId, "the newest request comes first");
        assertTrue(assertThrows(IllegalStateException.class, () -> call(manager, "PATCH", "/api/v1/manager/warranty-claims/" + second,
            Map.of("status", "REJECTED"))).getMessage().contains("400"), "rejecting without a reason is refused");
        call(manager, "PATCH", "/api/v1/manager/warranty-claims/" + second, Map.of("status", "REJECTED", "staffNote", "Selenium: hư do va đập"));
    }

    @Test
    void serverMessagesFollowTheLanguageOfTheRequest() throws Exception {
        String english = body("/api/v1/warranty/lookup?frame=ZZZ-NO-SUCH-0002", "en-US,en;q=0.9");
        assertTrue(english.contains("No vehicle with this frame number was found."), english);
        String vietnamese = body("/api/v1/warranty/lookup?frame=ZZZ-NO-SUCH-0002", "vi-VN,vi;q=0.9");
        assertTrue(vietnamese.contains("Không tìm thấy xe có số khung này."), vietnamese);
        String tooShort = body("/api/v1/warranty/lookup?frame=AB", "en");
        assertTrue(tooShort.contains("5 to 30 letters"), tooShort);
        // a language this shop does not have falls back to Vietnamese
        assertTrue(body("/api/v1/warranty/lookup?frame=AB", "fr").contains("5 đến 30 ký tự"));
    }

    @Test
    void erasingTheAccountKeepsTheRepairHistoryButClearsTheCustomersWords() throws Exception {
        HttpClient manager = managerApi();
        Map<Long, Integer> stockBefore = stockOf(productSlug);
        try {
            HttpClient client = newClient();
            String name = "seleniumwdel" + System.nanoTime();
            String email = name + "@example.com";
            call(client, "POST", "/api/v1/auth/register", Map.of("username", name, "email", email, "password", PASSWORD, "firstName", "Selenium", "lastName", "Xoa"));
            call(client, "POST", "/api/v1/auth/login", Map.of("usernameOrEmail", name, "password", PASSWORD));
            long orderId = placePickupOrder(client, email);
            for (String status : new String[] {"CONFIRMED", "PROCESSING", "READY_FOR_PICKUP", "DELIVERED"}) {
                if (status.equals("DELIVERED")) enterUnits(manager, orderId);
                call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", status));
            }
            JsonNode unit = listOf(call(client, "GET", "/api/v1/warranty/units", null)).iterator().next();
            String frame = unit.path("frameNumber").asText();
            call(client, "POST", "/api/v1/warranty/claims", Map.of("unitId", unit.path("id").asLong(), "description", "Selenium: nội dung riêng của khách"));
            long claimId = claimIdOf(manager, frame);
            call(manager, "PATCH", "/api/v1/manager/warranty-claims/" + claimId, Map.of("status", "RECEIVED", "staffNote", "Selenium: ghi chú của cửa hàng"));

            call(client, "DELETE", "/api/v1/auth/account", Map.of("password", PASSWORD, "confirmEmail", email));

            JsonNode kept = null;
            for (JsonNode claim : listOf(call(manager, "GET", "/api/v1/manager/warranty-claims?size=100", null))) {
                if (claim.path("id").asLong() == claimId) {
                    kept = claim;
                }
            }
            assertTrue(kept != null, "the request stays for the shop's records");
            assertEquals(AccountErasure.ERASED_DESCRIPTION, kept.path("description").asText(), "the customer's own words are gone");
            assertEquals("Selenium: ghi chú của cửa hàng", kept.path("staffNote").asText(), "the shop's own note stays");
            assertFalse(kept.toString().contains(email), "the erased account's email is no longer on the request: " + kept);
            assertEquals(frame, call(newClient(), "GET", "/api/v1/warranty/lookup?frame=" + frame, null).path("frameNumber").asText());
        } finally {
            restoreStock(manager, productSlug, stockBefore);
        }
    }

    // ---- steps ---------------------------------------------------------------------------

    /** The stock of each colour on sale, by variant id. */
    private static Map<Long, Integer> stockOf(String slug) throws Exception {
        Map<Long, Integer> stock = new LinkedHashMap<>();
        for (JsonNode variant : listOf(call(api, "GET", "/api/v1/products/" + slug, null).path("variants"))) {
            stock.put(variant.path("id").asLong(), variant.path("stockQuantity").asInt());
        }
        return stock;
    }

    /** Puts back the stock a test's order used up, so the next test finds the bike as it was. */
    private static void restoreStock(HttpClient manager, String slug, Map<Long, Integer> before) {
        try {
            long productId = call(api, "GET", "/api/v1/products/" + slug, null).path("id").asLong();
            Map<Long, Integer> now = stockOf(slug);
            for (Map.Entry<Long, Integer> entry : before.entrySet()) {
                Integer current = now.get(entry.getKey());
                if (current != null && !current.equals(entry.getValue())) {
                    call(manager, "PATCH", "/api/v1/manager/products/" + productId + "/variants/" + entry.getKey() + "/stock",
                        Map.of("stockQuantity", entry.getValue()));
                }
            }
        } catch (Exception e) {
            System.err.println("Could not put the stock of " + slug + " back: " + e.getMessage());
        }
    }

    private static long placePickupOrder(HttpClient client, String email) throws Exception {
        JsonNode product = call(api, "GET", "/api/v1/products/" + productSlug, null);
        JsonNode color = null;
        for (JsonNode variant : listOf(product.path("variants"))) {
            if (color == null || variant.path("stockQuantity").asInt() > color.path("stockQuantity").asInt()) {
                color = variant;
            }
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(color == null || color.path("stockQuantity").asInt() > 0, "every colour of " + productSlug + " is sold out");
        long showroom = listOf(call(api, "GET", "/api/v1/showrooms", null)).iterator().next().path("id").asLong();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productId", product.path("id").asLong());
        item.put("quantity", 1);
        item.put("productVariantId", color == null ? null : color.path("id").asLong());
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("customerName", "Selenium Xoa");
        order.put("phoneNumber", "0901234567");
        order.put("customerEmail", email);
        order.put("deliveryMethod", "STORE_PICKUP");
        order.put("pickupShowroomId", showroom);
        order.put("paymentMethod", "PAY_LATER");
        order.put("items", List.of(item));
        return call(client, "POST", "/api/v1/orders", order).path("id").asLong();
    }

    /** The raw answer of a GET with the given Accept-Language, error or not. */
    private static String body(String path, String acceptLanguage) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).header("Accept", "application/json")
            .header("Accept-Language", acceptLanguage).GET().build();
        return newClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private static int listSize(JsonNode list) {
        int count = 0;
        for (JsonNode ignored : listOf(list)) {
            count++;
        }
        return count;
    }

    private static boolean mentions(JsonNode list, String text) {
        for (JsonNode row : listOf(list)) {
            if (row.toString().contains(text)) {
                return true;
            }
        }
        return false;
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
        for (JsonNode claim : listOf(call(manager, "GET", "/api/v1/manager/warranty-claims?size=100", null))) {
            if (frame.equals(claim.path("frameNumber").asText())) {
                return claim.path("id").asLong();
            }
        }
        throw new AssertionError("no warranty request for " + frame);
    }
}
