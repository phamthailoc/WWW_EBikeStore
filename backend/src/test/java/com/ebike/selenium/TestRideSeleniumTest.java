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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.openqa.selenium.By;
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
 * Every booking made here is cancelled at the end, so the slot is free for the next run. The manager account comes from the
 * selenium.managerUser and selenium.managerPassword properties; without it the test is skipped.
 */
@Tag("selenium")
@Execution(ExecutionMode.SAME_THREAD)
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class TestRideSeleniumTest extends StorefrontSeleniumTest {

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

    private HttpClient newCustomer(String tag) throws Exception {
        HttpClient client = newClient();
        String name = "ride" + tag + System.currentTimeMillis();
        call(client, "POST", "/api/v1/auth/register", Map.of(
            "username", name, "email", name + "@example.com", "password", PASSWORD, "firstName", "Ride", "lastName", tag));
        call(client, "POST", "/api/v1/auth/login", Map.of("usernameOrEmail", name, "password", PASSWORD));
        return client;
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
            return Integer.parseInt(message.substring(arrow + 3, arrow + 6));
        }
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

    private void openBookingDialog() {
        driver.get(baseUrl + "/product/" + productSlug);
        click(By.id("bookTestRide"));
        wait.until(ExpectedConditions.visibilityOfElementLocated(DIALOG));
        // the showroom list arrives with a second request
        wait.until(d -> !"true".equals(d.findElement(By.cssSelector("dialog.testride-dialog select[name=showroom]")).getAttribute("disabled")));
    }

    @Test
    void bookConfirmCancelAndTheRules() throws Exception {
        String managerUser = System.getProperty("selenium.managerUser", "");
        String managerPassword = System.getProperty("selenium.managerPassword", "");
        HttpClient manager = managerApi(); // skips without a manager account: the schedule is the manager's
        List<HttpClient> throwaways = new ArrayList<>();
        try {
            walkThroughTheBooking(manager, managerUser, managerPassword, throwaways);
        } finally {
            cancelAllOpen(api);
            throwaways.forEach(TestRideSeleniumTest::cancelAllOpen);
        }
    }

    private void walkThroughTheBooking(HttpClient manager, String managerUser, String managerPassword, List<HttpClient> throwaways) throws Exception {
        long productId = call(api, "GET", "/api/v1/products/" + productSlug, null).path("id").asLong();
        JsonNode showrooms = call(api, "GET", "/api/v1/showrooms", null);
        assumeTrue(showrooms.size() > 0, "no showroom to book at");
        long showroomId = showrooms.get(0).path("id").asLong();
        String showroomName = showrooms.get(0).path("name").asText();

        // 1. find a slot, in the next days, that is still completely free
        LocalDate day = null;
        String start = null;
        int capacity = 0;
        for (int ahead = 3; ahead <= 12 && start == null; ahead++) {
            JsonNode slots = slotsOn(api, showroomId, LocalDate.now().plusDays(ahead));
            for (JsonNode slot : slots) {
                if (slot.path("bookable").asBoolean() && slot.path("remaining").asInt() == slot.path("capacity").asInt()) {
                    day = LocalDate.now().plusDays(ahead);
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
        HttpClient filler = null;
        for (int i = 0; i < capacity - 1; i++) {
            filler = newCustomer("Filler" + i);
            throwaways.add(filler);
            String fillerPhone = phone(i + 1);
            fillerPhones.add(fillerPhone);
            call(filler, "POST", "/api/v1/test-rides", Map.of(
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
        assertTrue(button.getText().contains("1"), "the slot shows one place left: " + button.getText());
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
        HttpClient late = newCustomer("Late");
        throwaways.add(late);
        assertEquals(409, status(late, "POST", "/api/v1/test-rides", Map.of(
            "productId", productId, "showroomId", showroomId, "slotStart", start, "contactPhone", phone(60))), "the slot is full");
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
        assertEquals(409, status(api, "POST", "/api/v1/test-rides", Map.of(
            "productId", productId, "showroomId", showroomId, "slotStart", start, "contactPhone", myPhone)), "the same customer, the same time");
        String night = day + "T03:00:00";
        assertEquals(400, status(late, "POST", "/api/v1/test-rides", Map.of(
            "productId", productId, "showroomId", showroomId, "slotStart", night, "contactPhone", phone(61))), "outside opening hours");
        String between = start.substring(0, 14) + "30:00";
        assertEquals(400, status(late, "POST", "/api/v1/test-rides", Map.of(
            "productId", productId, "showroomId", showroomId, "slotStart", between, "contactPhone", phone(62))), "half past is between two slots");
        assertEquals(400, status(late, "POST", "/api/v1/test-rides", Map.of(
            "productId", productId, "showroomId", showroomId, "slotStart", LocalDate.now().minusDays(1) + "T09:00:00", "contactPhone", phone(63))), "in the past");
        assertEquals(400, status(late, "POST", "/api/v1/test-rides", Map.of(
            "productId", productId, "showroomId", showroomId, "slotStart", LocalDate.now().plusDays(30) + "T09:00:00", "contactPhone", phone(64))), "beyond the window");
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
        WebElement managerRow = rowWith(myPhone);
        assertEquals("REQUESTED", managerRow.findElement(By.cssSelector("td[data-field='status']")).getAttribute("data-raw"), managerRow.getText());
        js("const i=document.querySelector('#filters [name=date]');i.value=arguments[0];i.dispatchEvent(new Event('change',{bubbles:true}))", day.plusDays(1).toString());
        wait.until(d -> d.findElement(By.id("empty")).isDisplayed());
        js("const i=document.querySelector('#filters [name=date]');i.value=arguments[0];i.dispatchEvent(new Event('change',{bubbles:true}))", day.toString());
        managerRow = rowWith(myPhone);
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

        // 10. the manager cancels another customer's booking: a reason is needed, and that customer is told
        if (filler != null) {
            HttpClient fillerClient = filler;
            int fillerBefore = notificationsOfType(fillerClient, "TEST_RIDE");
            logIn(managerUser, managerPassword);
            driver.get(baseUrl + "/manager/test-rides");
            WebElement fillerRow = rowWith(fillerPhones.get(fillerPhones.size() - 1));
            click(fillerRow.findElement(By.cssSelector("[data-action='ride-step'][data-status='CANCELLED']")));
            submitNote(""); // no reason: refused inside the dialog
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("dialog.warranty-dialog .support-message.error")));
            driver.findElement(NOTE_BOX).sendKeys("The bike is in service");
            click(By.cssSelector("dialog.warranty-dialog button[data-submit]"));
            wait.until(d -> "CANCELLED".equals(rowWith(fillerPhones.get(fillerPhones.size() - 1)).findElement(By.cssSelector("td[data-field='status']")).getAttribute("data-raw")));
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

        // 11. the pages speak English when the device says so
        logIn(username, PASSWORD);
        driver.get(baseUrl + "/lang/en?back=" + "/product/" + productSlug);
        try {
            assertEquals("Book a test ride", wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("bookTestRide"))).getText().trim());
            driver.get(baseUrl + "/customer/test-rides");
            WebElement englishRow = rowWith("Please bring your ID");
            assertEquals("Cancelled", englishRow.findElement(By.cssSelector("td[data-field='status']")).getText());
            assertTrue(driver.findElement(By.tagName("h1")).getText().contains("Test rides"), driver.findElement(By.tagName("h1")).getText());
            openBookingDialog();
            assertTrue(driver.findElement(DIALOG).getText().contains("Book a test ride"), driver.findElement(DIALOG).getText());
        } finally {
            driver.get(baseUrl + "/lang/vi?back=/");
        }
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
    void aVisitorIsSentToSignInAndComesBackToTheDialog() throws Exception {
        driver.manage().deleteAllCookies();
        driver.get(baseUrl + "/product/" + productSlug);
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
        logIn(managerUser, managerPassword);
        driver.get(baseUrl + "/product/" + productSlug);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("addToCart")));
        assertTrue(driver.findElements(By.id("bookTestRide")).isEmpty(), "staff are not offered the button");
        driver.get(baseUrl + "/customer/test-rides");
        wait.until(ExpectedConditions.urlContains("/manager/test-rides"));
        assertThrows(IllegalStateException.class, () -> call(managerApi(), "GET", "/api/v1/test-rides", null), "the booking API refuses staff");
    }
}
