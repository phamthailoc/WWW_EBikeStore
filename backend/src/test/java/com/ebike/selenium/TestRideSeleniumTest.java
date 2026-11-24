package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

/**
 * V23: a customer books a test ride in the dialog on the bike page and finds it in "Test rides"; the last place of a slot is
 * taken and a further customer is refused; the rules of the booking (duplicate time, outside opening hours, wrong phone) answer 4xx; a
 * visitor and the staff are refused; the manager confirms it from the schedule and the customer is notified; the customer cancels and
 * the place is free again; the manager cancels another booking with a reason and that customer is notified; the pages speak English.
 * The reminder and the closing of unconfirmed requests run on a timer a browser cannot reach; they are covered by unit tests.
 *
 * Every booking made here is cancelled at the end, so the slot is free for the next run, and the extra customers it registers are
 * erased (their accounts stay as DELETED rows, like any erased account). The manager account comes from the
 * selenium.managerUser and selenium.managerPassword properties; without it the test is skipped.
 *
 * <p>One run sends about 13 POST /api/v1/test-rides from one address, more than the rule test-ride-book allows (10 per 10 minutes): start the
 * app with APP_RATE_LIMIT_ENABLED=false. A 429 skips the test with that hint instead of failing it. The bike is the first one whose vehicle
 * type can be ridden, not simply the first one in stock.
 */
@Tag("selenium")
@Execution(ExecutionMode.SAME_THREAD)
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class TestRideSeleniumTest extends StorefrontSeleniumTest {

    /** The showroom's zone (the server's app.time-zone; -Dselenium.timeZone when it was changed): the days the server accepts are counted in it, not in the zone of the machine that runs this test. */
    private static final ZoneId SHOWROOM_ZONE = ZoneId.of(System.getProperty("selenium.timeZone", "Asia/Ho_Chi_Minh"));

    private static LocalDate today() {
        return LocalDate.now(SHOWROOM_ZONE);
    }

    private static final By DIALOG = By.cssSelector("dialog.testride-dialog");
    private static final By NOTE_BOX = By.cssSelector("dialog.warranty-dialog textarea[name=text]");

    /** A phone number nobody else uses in this run, so the row of this booking can be found in the staff table. */
    private static String phone(int offset) {
        long tail = (System.currentTimeMillis() / 10 + offset * 7919L) % 100_000_000L;
        return String.format("09%08d", tail);
    }

    private static int notificationsOfType(HttpClient client, String type) throws Exception {
        int count = 0;
        for (JsonNode row : listOf(call(client, "GET", "/api/v1/notifications", null))) {
            if (type.equals(row.path("type").asText())) {
                count++;
            }
        }
        return count;
    }

    /** TEST_RIDE notifications of the customer, or -1 when they cannot be read right now (the wait tries again). */
    private static int ridesNow(HttpClient client) {
        try {
            return notificationsOfType(client, "TEST_RIDE");
        } catch (Exception e) {
            return -1;
        }
    }

    /** An extra customer of this run: the signed-in client and the address needed to erase the account again. */
    private record Customer(HttpClient client, String email) {
    }

    private Customer newCustomer(String tag) throws Exception {
        HttpClient client = newClient();
        String name = "ride" + tag + System.currentTimeMillis();
        callOrSkip(client, "POST", "/api/v1/auth/register", Map.of(
            "username", name, "email", name + "@example.com", "password", PASSWORD, "firstName", "Ride", "lastName", tag));
        callOrSkip(client, "POST", "/api/v1/auth/login", Map.of("usernameOrEmail", name, "password", PASSWORD));
        return new Customer(client, name + "@example.com");
    }

    /** Cancels what the extra customer still holds and erases the account, so a run leaves no live test accounts behind. */
    private static void cleanUp(Customer customer) {
        cancelAllOpen(customer.client());
        try {
            call(customer.client(), "DELETE", "/api/v1/auth/account", Map.of("password", PASSWORD, "confirmEmail", customer.email()));
        } catch (Exception ignored) {
            // cleanup only: an account that cannot be erased stays as a harmless test customer
        }
    }

    /** {@link #call}, but a 429 (a rate limit of the running app) skips the test with the hint instead of failing it. */
    private static JsonNode callOrSkip(HttpClient client, String method, String path, Object body) throws Exception {
        try {
            return call(client, method, path, body);
        } catch (IllegalStateException e) {
            assumeTrue(!e.getMessage().contains("-> 429"), "rate limit reached: start the app with APP_RATE_LIMIT_ENABLED=false (" + e.getMessage() + ")");
            throw e;
        }
    }

    private static String rideSlugCache;

    /** The first product whose vehicle type can be ridden (a battery or an accessory has no "Book a test ride" button); skips the test when there is none. */
    private String rideSlug() throws Exception {
        if (rideSlugCache == null) {
            for (JsonNode product : listOf(call(api, "GET", "/api/v1/products", null))) {
                String slug = product.path("slug").asText();
                String type = call(api, "GET", "/api/v1/products/" + slug, null).path("specification").path("vehicleType").asText("");
                if (!type.isEmpty() && !type.equals("BATTERY") && !type.equals("ACCESSORY")) {
                    rideSlugCache = slug;
                    break;
                }
            }
        }
        assumeTrue(rideSlugCache != null, "no bike that can be test-ridden");
        return rideSlugCache;
    }

    private static void cancelAllOpen(HttpClient client) {
        try {
            for (JsonNode row : listOf(call(client, "GET", "/api/v1/test-rides", null))) {
                if (row.path("canCancel").asBoolean()) {
                    call(client, "PATCH", "/api/v1/test-rides/" + row.path("id").asLong() + "/cancel", null);
                }
            }
        } catch (Exception ignored) {
            // cleanup only: a booking that cannot be cancelled any more is closed by the staff or the timer
        }
    }

    private static JsonNode slotsOn(HttpClient client, long showroomId, LocalDate day) throws Exception {
        return call(client, "GET", "/api/v1/test-rides/slots?showroomId=" + showroomId + "&date=" + day, null);
    }

    private static int status(HttpClient client, String method, String path, Object body) throws Exception {
        try {
            call(client, method, path, body);
            return 200;
        } catch (IllegalStateException e) {
            // call() formats failures as "METHOD path -> CODE: body"
            String message = e.getMessage();
            int arrow = message.indexOf("-> ");
            int code = Integer.parseInt(message.substring(arrow + 3, arrow + 6));
            assumeTrue(code != 429, "rate limit reached: start the app with APP_RATE_LIMIT_ENABLED=false (" + message + ")");
            return code;
        }
    }

    /** Sends the request, expects it to fail with {@code code} and returns the whole failure text (the server's message is in it). */
    private static String failureOf(int code, HttpClient client, String method, String path, Object body) throws Exception {
        try {
            call(client, method, path, body);
        } catch (IllegalStateException e) {
            assumeTrue(!e.getMessage().contains("-> 429"), "rate limit reached: start the app with APP_RATE_LIMIT_ENABLED=false (" + e.getMessage() + ")");
            assertTrue(e.getMessage().contains("-> " + code), "expected " + code + " but got: " + e.getMessage());
            return e.getMessage();
        }
        throw new AssertionError(method + " " + path + " should have failed with " + code);
    }

    /**
     * Cancels what an aborted earlier run left open (the bookings of this run's customer and of the extra "Ride ..." customers) and erases those extra
     * accounts, which a killed run never got to erase. Their emails are in the schedule rows; the password is the one of every test customer.
     */
    private static void cancelLeftovers(HttpClient manager) {
        cancelAllOpen(api);
        java.util.Set<String> leftoverEmails = new java.util.LinkedHashSet<>();
        try {
            for (JsonNode row : listOf(call(manager, "GET", "/api/v1/manager/test-rides?status=ALL", null))) {
                if (!row.path("customerName").asText().startsWith("Ride ")) {
                    continue;
                }
                leftoverEmails.add(row.path("customerEmail").asText());
                if (row.path("nextSteps").toString().contains("CANCELLED")) {
                    call(manager, "PATCH", "/api/v1/manager/test-rides/" + row.path("id").asLong(), Map.of("status", "CANCELLED", "staffNote", "leftover of an aborted test run"));
                }
            }
        } catch (Exception ignored) {
            // cleanup only
        }
        for (String email : leftoverEmails) {
            try {
                HttpClient leftover = newClient();
                call(leftover, "POST", "/api/v1/auth/login", Map.of("usernameOrEmail", email, "password", PASSWORD));
                call(leftover, "DELETE", "/api/v1/auth/account", Map.of("password", PASSWORD, "confirmEmail", email));
            } catch (Exception ignored) {
                // cleanup only: an account that cannot be erased stays as a harmless test customer
            }
        }
    }

    /** Narrows the manager schedule to one day, so the row a step looks for is never lost among the 200 rows of the default list of a busy database. */
    private void filterDay(LocalDate day) {
        wait.until(d -> !d.findElements(By.cssSelector("#filters [name=date]")).isEmpty());
        js("const i=document.querySelector('#filters [name=date]');i.value=arguments[0];i.dispatchEvent(new Event('change',{bubbles:true}))", day.toString());
    }

    private void filterStatus(String status) {
        wait.until(d -> !d.findElements(By.cssSelector("#filters [name=status]")).isEmpty());
        js("const i=document.querySelector('#filters [name=status]');i.value=arguments[0];i.dispatchEvent(new Event('change',{bubbles:true}))", status);
    }

    private WebElement rowWith(String text) {
        return wait.until(d -> d.findElements(By.cssSelector("#data-table tbody tr")).stream()
            .filter(row -> row.getText().contains(text)).findFirst().orElse(null));
    }

    private void submitNote(String text) {
        WebElement box = wait.until(ExpectedConditions.visibilityOfElementLocated(NOTE_BOX));
        if (!text.isEmpty()) {
            box.sendKeys(text);
        }
        click(By.cssSelector("dialog.warranty-dialog button[data-submit]"));
    }

    private void openBookingDialog() throws Exception {
        driver.get(baseUrl + "/product/" + rideSlug());
        click(By.id("bookTestRide"));
        wait.until(ExpectedConditions.visibilityOfElementLocated(DIALOG));
        // the showroom list arrives with a second request
        wait.until(d -> !"true".equals(d.findElement(By.cssSelector("dialog.testride-dialog select[name=showroom]")).getAttribute("disabled")));
    }

    /** This class registers a customer of its own (in the base class) and erases it when the class is done, like the extra customers it makes. */
    @org.junit.jupiter.api.BeforeAll
    static void eraseTheCustomerWhenDone() {
        eraseCustomerWhenDone = true;
    }

    @Test
    void bookConfirmCancelAndTheRules() throws Exception {
        String managerUser = System.getProperty("selenium.managerUser", "");
        String managerPassword = System.getProperty("selenium.managerPassword", "");
        HttpClient manager = managerApi(); // skips without a manager account: the schedule is the manager's
        List<Customer> throwaways = new ArrayList<>();
        try {
            walkThroughTheBooking(manager, managerUser, managerPassword, throwaways);
        } finally {
            cancelAllOpen(api);
            throwaways.forEach(TestRideSeleniumTest::cleanUp);
        }
    }

    private void walkThroughTheBooking(HttpClient manager, String managerUser, String managerPassword, List<Customer> throwaways) throws Exception {
        cancelLeftovers(manager); // filler accounts of a killed run would still hold places
        long productId = call(api, "GET", "/api/v1/products/" + rideSlug(), null).path("id").asLong();
        JsonNode showrooms = call(api, "GET", "/api/v1/showrooms", null);
        assumeTrue(showrooms.size() > 0, "no showroom to book at");
        long showroomId = showrooms.get(0).path("id").asLong();
        String showroomName = showrooms.get(0).path("name").asText();

        // 1. find a slot, in the next days, that is still completely free
        LocalDate day = null;
        String start = null;
        int capacity = 0;
        for (int ahead = 3; ahead <= 12 && start == null; ahead++) {
            JsonNode slots = slotsOn(api, showroomId, today().plusDays(ahead));
            for (JsonNode slot : slots) {
                if (slot.path("bookable").asBoolean() && slot.path("remaining").asInt() == slot.path("capacity").asInt()) {
                    day = today().plusDays(ahead);
                    start = slot.path("start").asText();
                    capacity = slot.path("capacity").asInt();
                    break;
                }
            }
        }
        assumeTrue(start != null, "no completely free slot in the next days");
        assertTrue(capacity >= 1);

        // 2. the other customers fill the slot but one place
        List<String> fillerPhones = new ArrayList<>();
        Customer filler = null;
        for (int i = 0; i < capacity - 1; i++) {
            filler = newCustomer("Filler" + i);
            throwaways.add(filler);
            String fillerPhone = phone(i + 1);
            fillerPhones.add(fillerPhone);
            callOrSkip(filler.client(), "POST", "/api/v1/test-rides", Map.of(
                "productId", productId, "showroomId", showroomId, "slotStart", start, "contactPhone", fillerPhone, "note", "filler"));
        }
        JsonNode before = slotsOn(api, showroomId, day);
        for (JsonNode slot : before) {
            if (start.equals(slot.path("start").asText())) {
                assertEquals(1, slot.path("remaining").asInt(), "one place is left");
            }
        }

        // 3. the customer books the last place in the dialog on the bike page
        String myPhone = phone(50);
        openBookingDialog();
        assertTrue(driver.findElement(By.cssSelector("dialog.testride-dialog")).getText().contains(driver.findElement(By.cssSelector("#productPage")).getAttribute("data-name")),
            "the dialog names the bike");
        new Select(driver.findElement(By.cssSelector("dialog.testride-dialog select[name=showroom]"))).selectByValue(String.valueOf(showroomId));
        new Select(driver.findElement(By.cssSelector("dialog.testride-dialog select[name=date]"))).selectByValue(day.toString());
        By slotButton = By.cssSelector(".testride-slot[data-start='" + start + "']");
        WebElement button = wait.until(ExpectedConditions.presenceOfElementLocated(slotButton));
        assertTrue(button.findElement(By.tagName("small")).getText().matches("Còn 1 chỗ|1 left"), "the slot shows one place left: " + button.getText());
        // nothing can be sent before a slot is chosen
        assertEquals("true", driver.findElement(By.cssSelector("dialog.testride-dialog [data-submit]")).getAttribute("disabled"));
        click(slotButton);
        driver.findElement(By.cssSelector("dialog.testride-dialog input[name=phone]")).sendKeys("123");
        click(By.cssSelector("dialog.testride-dialog [data-submit]"));
        WebElement refused = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("dialog.testride-dialog .support-message.error")));
        assertFalse(refused.getText().isBlank(), "a short phone number is refused before anything is sent");
        WebElement phoneBox = driver.findElement(By.cssSelector("dialog.testride-dialog input[name=phone]"));
        phoneBox.clear();
        phoneBox.sendKeys(myPhone);
        click(By.cssSelector("dialog.testride-dialog [data-submit]"));
        WebElement done = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("dialog.testride-dialog [data-field=done]")));
        assertTrue(done.getText().contains(showroomName), done.getText());

        // 4. the slot is full now: a further customer is refused, the slot list says so, and the dialog shows it disabled
        Customer lateCustomer = newCustomer("Late");
        throwaways.add(lateCustomer);
        HttpClient late = lateCustomer.client();
        String fullText = failureOf(409, late, "POST", "/api/v1/test-rides", Map.of(
            "productId", productId, "showroomId", showroomId, "slotStart", start, "contactPhone", phone(60)));
        assertTrue(fullText.contains("đủ chỗ") || fullText.contains("is full"), "the slot is full: " + fullText);
        for (JsonNode slot : slotsOn(api, showroomId, day)) {
            if (start.equals(slot.path("start").asText())) {
                assertEquals(0, slot.path("remaining").asInt());
                assertFalse(slot.path("bookable").asBoolean());
            }
        }
        openBookingDialog();
        new Select(driver.findElement(By.cssSelector("dialog.testride-dialog select[name=showroom]"))).selectByValue(String.valueOf(showroomId));
        new Select(driver.findElement(By.cssSelector("dialog.testride-dialog select[name=date]"))).selectByValue(day.toString());
        WebElement fullButton = wait.until(ExpectedConditions.presenceOfElementLocated(slotButton));
        assertEquals("true", fullButton.getAttribute("disabled"), "a full slot cannot be chosen");

        // 5. the rules of the booking, through the API
        // the same customer twice at a time that still has room, so only the duplicate rule can refuse it (the full slot above would refuse it for its capacity)
        String roomy = otherFreeSlot(day, start, showroomId);
        if (roomy == null) {
            System.err.println("TestRideSeleniumTest: no second free slot on " + day + ", the duplicate-time check is not exercised in this run");
        } else {
            callOrSkip(api, "POST", "/api/v1/test-rides", Map.of(
                "productId", productId, "showroomId", showroomId, "slotStart", roomy, "contactPhone", phone(51)));
            String duplicate = failureOf(409, api, "POST", "/api/v1/test-rides", Map.of(
                "productId", productId, "showroomId", showroomId, "slotStart", roomy, "contactPhone", phone(51)));
            assertTrue(duplicate.contains("đã có một lịch") || duplicate.contains("already have a test ride"), "the duplicate rule answered: " + duplicate);
        }
        String night = day + "T03:00:00";
        assertEquals(400, status(late, "POST", "/api/v1/test-rides", Map.of(
            "productId", productId, "showroomId", showroomId, "slotStart", night, "contactPhone", phone(61))), "outside opening hours");
        String between = start.substring(0, 14) + "30:00";
        assertEquals(400, status(late, "POST", "/api/v1/test-rides", Map.of(
            "productId", productId, "showroomId", showroomId, "slotStart", between, "contactPhone", phone(62))), "half past is between two slots");
        assertEquals(400, status(late, "POST", "/api/v1/test-rides", Map.of(
            "productId", productId, "showroomId", showroomId, "slotStart", today().minusDays(1) + "T09:00:00", "contactPhone", phone(63))), "in the past");
        assertEquals(400, status(late, "POST", "/api/v1/test-rides", Map.of(
            "productId", productId, "showroomId", showroomId, "slotStart", today().plusDays(30) + "T09:00:00", "contactPhone", phone(64))), "beyond the window");
        assertEquals(400, status(late, "POST", "/api/v1/test-rides", Map.of(
            "productId", productId, "showroomId", showroomId, "slotStart", day.plusDays(1) + "T09:00:00", "contactPhone", "12345")), "a wrong phone number");
        assertEquals(401, status(newClient(), "POST", "/api/v1/test-rides", Map.of(
            "productId", productId, "showroomId", showroomId, "slotStart", start, "contactPhone", phone(65))), "a visitor must sign in");
        assertEquals(403, status(manager, "POST", "/api/v1/test-rides", Map.of(
            "productId", productId, "showroomId", showroomId, "slotStart", start, "contactPhone", phone(66))), "staff do not book");
        assertEquals(403, status(api, "GET", "/api/v1/manager/test-rides", null), "a customer does not see the schedule");

        // 6. the customer's list shows the booking as waiting for confirmation, with a cancel button
        driver.get(baseUrl + "/customer/test-rides");
        WebElement myRow = rowWith(productNameOf(productId));
        assertEquals("REQUESTED", myRow.findElement(By.cssSelector("td[data-field='status']")).getAttribute("data-raw"), myRow.getText());
        assertTrue(myRow.getText().contains(showroomName), myRow.getText());
        assertFalse(myRow.findElements(By.cssSelector("[data-action='ride-cancel']")).isEmpty(), "an open booking can be cancelled");
        int ridesBefore = notificationsOfType(api, "TEST_RIDE");

        // 7. the manager finds it in the schedule (the date filter narrows the list) and confirms it with a note
        logIn(managerUser, managerPassword);
        driver.get(baseUrl + "/manager/test-rides");
        // a day on which no showroom has an open booking, so the filter must show the empty message (the filter covers every showroom)
        LocalDate quietDay = null;
        for (int ahead = 0; ahead <= 14 && quietDay == null; ahead++) {
            LocalDate candidate = today().plusDays(ahead);
            if (!listOf(call(manager, "GET", "/api/v1/manager/test-rides?date=" + candidate, null)).iterator().hasNext()) {
                quietDay = candidate;
            }
        }
        if (quietDay == null) {
            System.err.println("TestRideSeleniumTest: every day of the window has an open booking, the empty-filter check is not exercised in this run");
        } else {
            filterDay(quietDay);
            wait.until(d -> d.findElement(By.id("empty")).isDisplayed());
        }
        filterDay(day);
        WebElement managerRow = rowWith(myPhone);
        assertEquals("REQUESTED", managerRow.findElement(By.cssSelector("td[data-field='status']")).getAttribute("data-raw"), managerRow.getText());
        click(managerRow.findElement(By.cssSelector("[data-action='ride-step'][data-status='CONFIRMED']")));
        submitNote("Please bring your ID");
        wait.until(d -> "CONFIRMED".equals(rowWith(myPhone).findElement(By.cssSelector("td[data-field='status']")).getAttribute("data-raw")));
        wait.until(d -> ridesNow(api) >= ridesBefore + 1);

        // 8. the customer sees it confirmed with the note, and a notification that opens the bookings
        logIn(username, PASSWORD);
        driver.get(baseUrl + "/customer/test-rides");
        myRow = rowWith("Please bring your ID");
        assertEquals("CONFIRMED", myRow.findElement(By.cssSelector("td[data-field='status']")).getAttribute("data-raw"), myRow.getText());
        boolean opensBookings = false;
        for (JsonNode row : listOf(call(api, "GET", "/api/v1/notifications", null))) {
            if ("TEST_RIDE".equals(row.path("type").asText()) && "/customer/test-rides".equals(row.path("targetUrl").asText())) {
                opensBookings = true;
            }
        }
        assertTrue(opensBookings, "the notice links to the bookings");

        // 9. the customer cancels; the place is free again
        click(myRow.findElement(By.cssSelector("[data-action='ride-cancel']")));
        click(By.cssSelector("dialog.warranty-dialog button[data-submit]"));
        wait.until(d -> "CANCELLED".equals(rowWith("Please bring your ID").findElement(By.cssSelector("td[data-field='status']")).getAttribute("data-raw")));
        assertTrue(rowWith("Please bring your ID").findElements(By.cssSelector("[data-action='ride-cancel']")).isEmpty(), "a cancelled booking has no cancel button");
        for (JsonNode slot : slotsOn(api, showroomId, day)) {
            if (start.equals(slot.path("start").asText())) {
                assertEquals(1, slot.path("remaining").asInt(), "the cancelled place is free again");
            }
        }

        // 9b. the dialog is open on the last place and someone else takes it: the refusal is explained and stays on screen while the list reloads
        openBookingDialog();
        new Select(driver.findElement(By.cssSelector("dialog.testride-dialog select[name=showroom]"))).selectByValue(String.valueOf(showroomId));
        new Select(driver.findElement(By.cssSelector("dialog.testride-dialog select[name=date]"))).selectByValue(day.toString());
        WebElement lastPlace = wait.until(ExpectedConditions.presenceOfElementLocated(slotButton));
        assertTrue(lastPlace.findElement(By.tagName("small")).getText().matches("Còn 1 chỗ|1 left"), lastPlace.getText());
        click(lastPlace);
        driver.findElement(By.cssSelector("dialog.testride-dialog input[name=phone]")).sendKeys(phone(70));
        callOrSkip(late, "POST", "/api/v1/test-rides", Map.of(
            "productId", productId, "showroomId", showroomId, "slotStart", start, "contactPhone", phone(71)));
        click(By.cssSelector("dialog.testride-dialog [data-submit]"));
        By dialogError = By.cssSelector("dialog.testride-dialog .support-message.error");
        wait.until(ExpectedConditions.visibilityOfElementLocated(dialogError));
        wait.until(d -> "true".equals(d.findElement(slotButton).getAttribute("disabled"))); // the list was reloaded and shows the slot full
        assertTrue(driver.findElement(dialogError).isDisplayed() && !driver.findElement(dialogError).getText().isBlank(),
            "the reason for the refusal is still on screen after the slots reload");
        assertEquals("true", driver.findElement(By.cssSelector("dialog.testride-dialog [data-submit]")).getAttribute("disabled"), "nothing is chosen any more");

        // 10. the manager cancels another customer's booking: a reason is needed, and that customer is told
        // with room for only one booking per slot there is no filler yet: another customer takes a different slot, so this step always runs
        if (filler == null) {
            String other = otherFreeSlot(day, start, showroomId);
            assertNotNull(other, "a second free slot is needed for the manager-cancels step when the slot capacity is 1");
            filler = newCustomer("Cancelled");
            throwaways.add(filler);
            fillerPhones.add(phone(80));
            callOrSkip(filler.client(), "POST", "/api/v1/test-rides", Map.of(
                "productId", productId, "showroomId", showroomId, "slotStart", other, "contactPhone", fillerPhones.get(0), "note", "filler"));
        }
        {
            HttpClient fillerClient = filler.client();
            int fillerBefore = notificationsOfType(fillerClient, "TEST_RIDE");
            logIn(managerUser, managerPassword);
            driver.get(baseUrl + "/manager/test-rides");
            filterDay(day);
            wait.until(d -> { // the list is reloaded after the day filter: look the row up again if it went stale
                try {
                    click(rowWith(fillerPhones.get(fillerPhones.size() - 1)).findElement(By.cssSelector("[data-action='ride-step'][data-status='CANCELLED']")));
                    return true;
                } catch (StaleElementReferenceException e) {
                    return false;
                }
            });
            submitNote(""); // no reason: refused inside the dialog
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("dialog.warranty-dialog .support-message.error")));
            driver.findElement(NOTE_BOX).sendKeys("The bike is in service");
            click(By.cssSelector("dialog.warranty-dialog button[data-submit]"));
            filterStatus("ALL"); // a cancelled booking leaves the default list of open bookings
            wait.until(d -> { try { return "CANCELLED".equals(rowWith(fillerPhones.get(fillerPhones.size() - 1)).findElement(By.cssSelector("td[data-field='status']")).getAttribute("data-raw")); } catch (StaleElementReferenceException e) { return false; } });
            wait.until(d -> {
                try {
                    return notificationsOfType(fillerClient, "TEST_RIDE") >= fillerBefore + 1;
                } catch (Exception e) {
                    return false;
                }
            });
            // a closed booking cannot be moved again
            long fillerBookingId = 0;
            for (JsonNode row : listOf(call(manager, "GET", "/api/v1/manager/test-rides?status=CANCELLED&date=" + day, null))) {
                if (fillerPhones.get(fillerPhones.size() - 1).equals(row.path("contactPhone").asText())) {
                    fillerBookingId = row.path("id").asLong();
                }
            }
            assertTrue(fillerBookingId > 0, "the cancelled booking is in the schedule");
            assertEquals(400, status(manager, "PATCH", "/api/v1/manager/test-rides/" + fillerBookingId, Map.of("status", "CONFIRMED")));
        }
    }

    /** A bookable slot of the day, other than {@code except}, with every place free (or null). */
    private String otherFreeSlot(LocalDate day, String except, long showroomId) throws Exception {
        for (JsonNode slot : slotsOn(api, showroomId, day)) {
            if (!except.equals(slot.path("start").asText()) && slot.path("bookable").asBoolean()
                && slot.path("remaining").asInt() == slot.path("capacity").asInt()) {
                return slot.path("start").asText();
            }
        }
        return null;
    }

    private String productNameOf(long productId) throws Exception {
        for (JsonNode product : listOf(call(api, "GET", "/api/v1/products", null))) {
            if (product.path("id").asLong() == productId) {
                return product.path("name").asText();
            }
        }
        throw new AssertionError("product " + productId + " not found");
    }

    @Test
    void theBookingDialogFitsAPhoneScreen() throws Exception {
        Dimension before = driver.manage().window().getSize();
        driver.manage().window().setSize(new Dimension(375, 812));
        try {
            openBookingDialog();
            List<?> box = (List<?>) js("const d=document.querySelector('dialog.testride-dialog').getBoundingClientRect();"
                + "return [d.left,d.right,window.innerWidth,document.documentElement.scrollWidth]");
            double left = ((Number) box.get(0)).doubleValue(), right = ((Number) box.get(1)).doubleValue();
            double width = ((Number) box.get(2)).doubleValue(), page = ((Number) box.get(3)).doubleValue();
            assertTrue(left >= 0 && right <= width, "the dialog stays inside a 375 px screen: " + box);
            assertTrue(page <= width + 1, "the page does not scroll sideways: " + box);
            WebElement submit = driver.findElement(By.cssSelector("dialog.testride-dialog [data-submit]"));
            js("arguments[0].scrollIntoView({block:'center',behavior:'instant'})", submit);
            assertTrue(submit.isDisplayed(), "the send button can be reached");
        } finally {
            driver.manage().window().setSize(before);
        }
    }

    @Test
    void aVisitorIsSentToSignInAndComesBackToTheDialog() throws Exception {
        String slug = rideSlug();
        driver.manage().deleteAllCookies();
        driver.get(baseUrl + "/product/" + slug);
        click(By.id("bookTestRide"));
        wait.until(ExpectedConditions.urlContains("/login"));
        assertTrue(driver.getCurrentUrl().contains("testRide=1") || driver.getCurrentUrl().contains("testRide%3D1"), driver.getCurrentUrl());
        fill("usernameOrEmail", username);
        fill("password", PASSWORD);
        click(By.cssSelector("#loginForm button"));
        wait.until(ExpectedConditions.visibilityOfElementLocated(DIALOG));
        assertFalse(driver.getCurrentUrl().contains("testRide"), "the address is cleaned once the dialog is open");
        assertNotNull(driver.findElement(By.cssSelector("dialog.testride-dialog select[name=showroom]")));
    }

    @Test
    void staffAreNotOfferedTheButtonAndAreSentAwayFromTheCustomerPage() throws Exception {
        String managerUser = System.getProperty("selenium.managerUser", "");
        String managerPassword = System.getProperty("selenium.managerPassword", "");
        managerApi(); // skips without a manager account
        String slug = rideSlug();
        logIn(managerUser, managerPassword);
        driver.get(baseUrl + "/product/" + slug);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("addToCart")));
        assertTrue(driver.findElements(By.id("bookTestRide")).isEmpty(), "staff are not offered the button");
        driver.get(baseUrl + "/customer/test-rides");
        wait.until(ExpectedConditions.urlContains("/manager/test-rides"));
        HttpClient staff = managerApi();
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> call(staff, "GET", "/api/v1/test-rides", null), "the booking API refuses staff");
        assertTrue(refused.getMessage().contains("-> 403"), "refused with 403, not with some other failure: " + refused.getMessage());
    }
}
