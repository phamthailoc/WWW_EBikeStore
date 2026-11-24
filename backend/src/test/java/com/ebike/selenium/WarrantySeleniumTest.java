package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ebike.service.admin.AccountErasure;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
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
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("product-stock")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class WarrantySeleniumTest extends StorefrontSeleniumTest {

    private static final By DIALOG_TEXT = By.cssSelector("dialog.warranty-dialog textarea[name=text]");
    /** The page size of the staff queue (WARRANTY_PAGE_SIZE in workspace.js). */
    private static final int QUEUE_PAGE_SIZE = 25;

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
        String[] frameHolder = new String[1];
        try {
            walkThroughTheWarrantyBook(manager, frameHolder);
        } finally {
            closeOpenClaimsOf(manager, frameHolder[0]);
            restoreStock(manager, productSlug, stockBefore);
        }
    }

    private void walkThroughTheWarrantyBook(HttpClient manager, String[] frameHolder) throws Exception {
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
        frameHolder[0] = frame; // so a run that stops half way still closes its request and does not clog the staff queue
        JsonNode product = call(api, "GET", "/api/v1/products/" + productSlug, null);
        JsonNode monthsNode = product.path("specification").path("warrantyMonths");
        // The end date comes from the server (delivery time plus the pinned months): the pages must show exactly that. The date worked out here
        // from the order is only a sanity check, one day either way, because the delivery time and this machine may be in different time zones.
        String expectedEnd = null;
        for (JsonNode mine : listOf(call(api, "GET", "/api/v1/warranty/units", null))) {
            if (frame.equals(mine.path("frameNumber").asText()) && mine.path("warrantyEndsAt").isTextual()) {
                expectedEnd = mine.path("warrantyEndsAt").asText();
            }
        }
        assertEquals(monthsNode.isNumber(), expectedEnd != null, "a bike with a warranty length has an end date");
        if (expectedEnd != null) {
            String startText = order.path("shipment").path("deliveredAt").asText(order.path("updatedAt").asText());
            long drift = Math.abs(ChronoUnit.DAYS.between(LocalDate.parse(startText.substring(0, 10)).plusMonths(monthsNode.asInt()), LocalDate.parse(expectedEnd)));
            assertTrue(drift <= 1, "the end date " + expectedEnd + " is the delivery plus " + monthsNode.asInt() + " months");
        }

        // 2. the customer's warranty book lists the bike with the right end date and offers the request button
        driver.get(baseUrl + "/customer/warranty");
        WebElement row = rowWith(frame);
        if (expectedEnd != null) {
            assertEquals(expectedEnd, row.findElement(By.cssSelector("td[data-field='warrantyEndsAt']")).getAttribute("data-iso"), row.getText());
            assertEquals("ACTIVE", row.findElement(By.cssSelector("td[data-field='coverage']")).getAttribute("data-raw"), row.getText());
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
        assertEquals(Set.of("frameNumber", "productName", "colorName", "warrantyMonths", "warrantyStartsAt", "warrantyEndsAt", "coverage"),
            new HashSet<>(keys), "the public answer has the bike and its dates only, no owner data");
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
        assertFalse(notice().isBlank(), "the page confirms the request");
        assertFalse(driver.findElement(By.id("notice")).getAttribute("class").contains("error"), "the confirmation is not an error: " + notice());
        WebElement sent = wait.until(d -> d.findElements(By.cssSelector("#warrantyClaims tbody tr")).stream()
            .filter(r -> r.getText().contains(frame)).findFirst().orElse(null));
        assertEquals("NEW", sent.findElement(By.cssSelector("td[data-field='claimStatus']")).getAttribute("data-status"), sent.getText());
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
        try {
            assertTrue(assertThrows(IllegalStateException.class, () -> call(other, "POST", "/api/v1/warranty/claims",
                Map.of("unitId", unitId, "description", "Selenium: xe không phải của tôi"))).getMessage().contains("404"));
            assertFalse(listOf(call(other, "GET", "/api/v1/warranty/units", null)).iterator().hasNext(), "another customer's book is empty");
            assertTrue(assertThrows(IllegalStateException.class, () -> call(other, "GET", "/api/v1/manager/warranty-claims", null)).getMessage().contains("403"));
            assertTrue(assertThrows(IllegalStateException.class, () -> call(api, "PATCH", "/api/v1/manager/warranty-claims/1", Map.of("status", "RECEIVED"))).getMessage().contains("403"));
            assertTrue(assertThrows(IllegalStateException.class, () -> call(anonymous, "GET", "/api/v1/warranty/claims", null)).getMessage().contains("401"));
            assertTrue(assertThrows(IllegalStateException.class, () -> call(api, "POST", "/api/v1/warranty/claims",
                Map.of("unitId", unitId, "description", "ngắn"))).getMessage().contains("400"), "a description that is too short is refused by the server too");
        } finally {
            // the throwaway customer is marked deleted again (the user row stays, as AccountErasure keeps it)
            call(other, "DELETE", "/api/v1/auth/account", Map.of("password", PASSWORD, "confirmEmail", otherName + "@example.com"));
        }

        // 5. the manager receives it with a note
        int before = notificationsOfType("WARRANTY_CLAIM");
        logIn(System.getProperty("selenium.managerUser"), System.getProperty("selenium.managerPassword"));
        WebElement queued = queueRowOf(manager, frame);
        assertTrue(queued.getText().contains("Selenium: pin sạc không đầy"), queued.getText());
        click(queued.findElement(By.cssSelector("[data-action='warranty-status'][data-status='RECEIVED']")));
        submitDialog("Selenium: mời anh mang xe tới cửa hàng");
        wait.until(d -> "RECEIVED".equals(rowWith(frame).findElement(By.cssSelector("td[data-field='status']")).getAttribute("data-raw")));

        // 5b. "Edit note" keeps the step and changes only what the customer reads
        click(rowWith(frame).findElement(By.cssSelector("[data-action='warranty-status'][data-status='']")));
        submitDialog(" Selenium: ghi chú thêm");
        wait.until(d -> rowWith(frame).getText().contains("Selenium: ghi chú thêm"));
        assertEquals("RECEIVED", rowWith(frame).findElement(By.cssSelector("td[data-field='status']")).getAttribute("data-raw"), "editing the note keeps the step");

        // 5c. staff who open the customer's warranty book are sent to the queue they work in
        driver.get(baseUrl + "/customer/warranty");
        wait.until(d -> d.getCurrentUrl().endsWith("/manager/warranty"));

        // 6. later steps over the API (REPAIRING -> DONE); a finished request cannot move back
        long claimId = claimIdOf(manager, frame);
        call(manager, "PATCH", "/api/v1/manager/warranty-claims/" + claimId, Map.of("status", "REPAIRING"));
        call(manager, "PATCH", "/api/v1/manager/warranty-claims/" + claimId, Map.of("status", "DONE", "staffNote", "Đã thay bộ sạc"));
        assertThrows(IllegalStateException.class, () -> call(manager, "PATCH", "/api/v1/manager/warranty-claims/" + claimId, Map.of("status", "REPAIRING")));

        // 7. the customer was notified of each change (including the note on its own) and sees the staff note in the warranty book
        wait.until(d -> {
            try {
                return notificationsOfType("WARRANTY_CLAIM") >= before + 4; // received, a new note, repairing, done
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
            .filter(r -> r.getText().contains(frame) && !r.findElements(By.cssSelector("td[data-status='DONE']")).isEmpty()).findFirst().orElse(null));
        assertTrue(done.getText().contains("Đã thay bộ sạc"), done.getText());
        // finished: the bike offers the button again
        assertFalse(rowWith(frame).findElements(By.cssSelector("[data-action='warranty-claim']")).isEmpty());

        // 8. the staff queue is paged and filtered on the server
        assertTrue(listSize(call(manager, "GET", "/api/v1/manager/warranty-claims?size=1", null)) <= 1);
        assertTrue(claimsOf(manager, "DONE").stream().anyMatch(c -> frame.equals(c.path("frameNumber").asText())));
        assertTrue(claimsOf(manager, "").stream().noneMatch(c -> frame.equals(c.path("frameNumber").asText())), "the default queue holds open requests only");
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
        String english = body("/api/v1/warranty/lookup?frame=ZZZ-NO-SUCH-0002", "en", null);
        assertTrue(english.contains("No vehicle with this frame number was found."), english);
        String vietnamese = body("/api/v1/warranty/lookup?frame=ZZZ-NO-SUCH-0002", "vi", null);
        assertTrue(vietnamese.contains("Không tìm thấy xe có số khung này."), vietnamese);
        String tooShort = body("/api/v1/warranty/lookup?frame=AB", "en", "vi-VN");
        assertTrue(tooShort.contains("5 to 30 letters"), tooShort);
        // like the pages, the browser's Accept-Language is not used: nothing chosen on the device is Vietnamese, and so is a language the shop does not have
        assertTrue(body("/api/v1/warranty/lookup?frame=AB", null, "en-US,en;q=0.9").contains("5 đến 30 ký tự"));
        assertTrue(body("/api/v1/warranty/lookup?frame=AB", "fr", null).contains("5 đến 30 ký tự"));
    }

    @Test
    void theLookupPageAnswersInEnglishWhenTheLanguageIsEnglish() {
        // choosing English is saved on the signed-in account, so Vietnamese is chosen again at the end: other tests expect Vietnamese
        try {
            driver.get(baseUrl + "/lang/en?back=/warranty");
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("warrantyLookupForm")));
            fill("frame", "ZZZ-NO-SUCH-0003");
            click(By.cssSelector("#warrantyLookupForm button[type=submit]"));
            WebElement error = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#warrantyLookupResult .notice.error")));
            assertTrue(error.getText().contains("No vehicle with this frame number was found."), error.getText());
            assertTrue(driver.findElement(By.id("warrantyLookupForm")).getText().contains("Look up"), "the form is in English too");
        } finally {
            driver.get(baseUrl + "/lang/vi?back=/warranty");
        }
    }

    @Test
    void erasingTheAccountKeepsTheRepairHistoryButClearsTheCustomersWords() throws Exception {
        HttpClient manager = managerApi();
        Map<Long, Integer> stockBefore = stockOf(productSlug);
        HttpClient client = newClient();
        String name = "seleniumwdel" + System.nanoTime();
        String email = name + "@example.com";
        String frame = null;
        boolean erased = false;
        try {
            call(client, "POST", "/api/v1/auth/register", Map.of("username", name, "email", email, "password", PASSWORD, "firstName", "Selenium", "lastName", "Xoa"));
            call(client, "POST", "/api/v1/auth/login", Map.of("usernameOrEmail", name, "password", PASSWORD));
            long orderId = placePickupOrder(client, email);
            for (String status : new String[] {"CONFIRMED", "PROCESSING", "READY_FOR_PICKUP", "DELIVERED"}) {
                if (status.equals("DELIVERED")) enterUnits(manager, orderId);
                call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", status));
            }
            JsonNode unit = listOf(call(client, "GET", "/api/v1/warranty/units", null)).iterator().next();
            frame = unit.path("frameNumber").asText();
            call(client, "POST", "/api/v1/warranty/claims", Map.of("unitId", unit.path("id").asLong(), "description", "Selenium: nội dung riêng của khách"));
            long claimId = claimIdOf(manager, frame);
            // closed before the account goes, so no request of an erased account stays in the staff queue for good (the note stays on it)
            call(manager, "PATCH", "/api/v1/manager/warranty-claims/" + claimId, Map.of("status", "RECEIVED"));
            call(manager, "PATCH", "/api/v1/manager/warranty-claims/" + claimId, Map.of("status", "DONE", "staffNote", "Selenium: ghi chú của cửa hàng"));

            call(client, "DELETE", "/api/v1/auth/account", Map.of("password", PASSWORD, "confirmEmail", email));
            erased = true;

            JsonNode kept = null;
            for (JsonNode claim : claimsOf(manager, "ALL")) {
                if (claim.path("id").asLong() == claimId) {
                    kept = claim;
                }
            }
            assertTrue(kept != null, "the request stays for the shop's records");
            assertEquals(AccountErasure.ERASED_DESCRIPTION, kept.path("description").asText(), "the customer's own words are gone");
            assertTrue(kept.path("staffNote").asText().startsWith("Selenium: ghi chú của cửa hàng"), "the shop's own note stays (a note about the closing may follow it)");
            assertFalse(kept.toString().contains(email), "the erased account's email is no longer on the request: " + kept);
            assertEquals(frame, call(newClient(), "GET", "/api/v1/warranty/lookup?frame=" + frame, null).path("frameNumber").asText());
        } finally {
            // a step that failed half way must not leave an open request in the shared queue or a test account behind
            closeOpenClaimsOf(manager, frame);
            if (!erased) {
                try {
                    call(client, "DELETE", "/api/v1/auth/account", Map.of("password", PASSWORD, "confirmEmail", email));
                } catch (Exception ignored) {
                    // the account was never made, or is already gone
                }
            }
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
        assumeTrue(color == null || color.path("stockQuantity").asInt() > 0, "every colour of " + productSlug + " is sold out");
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

    /** The answer of an API call from a visitor who chose a language on the device (cookie, may be null) and whose browser sends an Accept-Language (may be null). */
    private static String body(String path, String languageCookie, String acceptLanguage) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).header("Accept", "application/json");
        if (languageCookie != null) {
            builder.header("Cookie", "ebike_lang=" + languageCookie);
        }
        if (acceptLanguage != null) {
            builder.header("Accept-Language", acceptLanguage);
        }
        return newClient().send(builder.GET().build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    private static int listSize(JsonNode list) {
        int count = 0;
        for (JsonNode ignored : listOf(list)) {
            count++;
        }
        return count;
    }

    private static long unitIdOf(String frame) throws Exception {
        for (JsonNode unit : listOf(call(api, "GET", "/api/v1/warranty/units", null))) {
            if (frame.equals(unit.path("frameNumber").asText())) {
                return unit.path("id").asLong();
            }
        }
        throw new AssertionError("frame " + frame + " not in the warranty book");
    }

    /** Every request of the staff queue with the given status ("ALL" for every status; none for the open ones), in the order the server gives: pages are followed until a short one. */
    private static List<JsonNode> claimsOf(HttpClient manager, String status) throws Exception {
        List<JsonNode> found = new ArrayList<>();
        for (int page = 0; page < 200; page++) {
            int before = found.size();
            listOf(call(manager, "GET", "/api/v1/manager/warranty-claims?status=" + status + "&size=100&page=" + page, null)).forEach(found::add);
            if (found.size() - before < 100) {
                return found;
            }
        }
        return found;
    }

    /**
     * The row of a request in the staff queue. The queue shows 25 open requests a page, oldest first, so the page is worked out from the
     * request's place among the open ones and reached with the pager buttons: the test does not break when older open requests pile up.
     */
    private WebElement queueRowOf(HttpClient manager, String frame) throws Exception {
        List<JsonNode> open = claimsOf(manager, ""); // the default queue: open requests, oldest first
        int index = -1;
        for (int i = 0; i < open.size(); i++) {
            if (frame.equals(open.get(i).path("frameNumber").asText())) {
                index = i;
            }
        }
        assertTrue(index >= 0, "the request for " + frame + " is in the open queue");
        driver.get(baseUrl + "/manager/warranty");
        for (int page = 1; page <= index / QUEUE_PAGE_SIZE; page++) {
            String number = String.valueOf(page + 1);
            click(By.cssSelector("#warrantyPager [data-page-step='1']"));
            wait.until(d -> d.findElements(By.cssSelector("#warrantyPager span")).stream().anyMatch(s -> s.getText().endsWith(" " + number)));
        }
        return rowWith(frame);
    }

    /** Closes whatever request of the bike is still open, so a run that failed half way does not leave one in the staff queue for good. */
    private static void closeOpenClaimsOf(HttpClient manager, String frame) {
        if (frame == null) {
            return;
        }
        try {
            for (JsonNode claim : claimsOf(manager, "")) {
                if (frame.equals(claim.path("frameNumber").asText())) {
                    call(manager, "PATCH", "/api/v1/manager/warranty-claims/" + claim.path("id").asLong(),
                        Map.of("status", "REJECTED", "staffNote", "Selenium: dọn dẹp sau khi chạy kiểm thử"));
                }
            }
        } catch (Exception e) {
            System.err.println("Could not close the warranty requests of " + frame + ": " + e.getMessage());
        }
    }

    private static long claimIdOf(HttpClient manager, String frame) throws Exception {
        for (JsonNode claim : claimsOf(manager, "ALL")) {
            if (frame.equals(claim.path("frameNumber").asText())) {
                return claim.path("id").asLong();
            }
        }
        throw new AssertionError("no warranty request for " + frame);
    }
}
