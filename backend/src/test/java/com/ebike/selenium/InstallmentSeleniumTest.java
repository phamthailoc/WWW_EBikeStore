package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
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
 * V24: simulated instalments. The calculator on the bike page follows the slider and the term and agrees with the server's quote; a customer applies at
 * checkout (ID number masked, income checked), the order waits for the staff and cannot be confirmed or paid before the application is approved; the
 * manager approves it from the queue, the customer is told and sees the plan on the order, and the down payment is taken at the store; a second
 * application is rejected with a reason, which cancels the order and tells the customer; the rules answer 4xx (visitor, staff account, bad ID number,
 * income, down payment, another customer's application); the pages speak English. The timer that lets an unreviewed application go and the emails run
 * where a browser cannot reach; they are covered by unit tests.
 *
 * <p>Needs the manager account of the other tests (selenium.managerUser and selenium.managerPassword): without it the test is skipped. The customer of
 * this run is erased when the class is done. Every order made here is cancelled in the end, so no stock stays held.
 *
 * <pre>
 * mvn test -Dtest=InstallmentSeleniumTest -Dselenium.baseUrl=http://localhost:8080 -Dselenium.managerUser=... -Dselenium.managerPassword=...
 * </pre>
 */
@Tag("selenium")
@Execution(ExecutionMode.SAME_THREAD)
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class InstallmentSeleniumTest extends StorefrontSeleniumTest {

    private static final String ID_NUMBER = "079123456789";
    private static final long INCOME = 100_000_000L;
    private static final By NOTE_BOX = By.cssSelector("dialog.warranty-dialog textarea[name=text]");

    @org.junit.jupiter.api.BeforeAll
    static void eraseTheCustomerWhenDone() {
        eraseCustomerWhenDone = true;
    }

    // ---- helpers ----

    private static String digits(String text) {
        return text.replaceAll("[^0-9]", "");
    }

    private static String failureOf(HttpClient client, String method, String path, Object body) throws Exception {
        try {
            call(client, method, path, body);
        } catch (IllegalStateException e) {
            assumeTrue(!e.getMessage().contains("-> 429"), "rate limit reached: start the app with APP_RATE_LIMIT_ENABLED=false (" + e.getMessage() + ")");
            return e.getMessage();
        }
        throw new AssertionError(method + " " + path + " should have been refused");
    }

    private static void assertRefused(int code, HttpClient client, String method, String path, Object body) throws Exception {
        String failure = failureOf(client, method, path, body);
        assertTrue(failure.contains("-> " + code), "expected " + code + " but got: " + failure);
    }

    /** The first product with bikes in stock, its price for one bike and its first colour that is in stock (or null). */
    private record Bike(long productId, long variantId, java.math.BigDecimal price) {
    }

    private Bike bike() throws Exception {
        JsonNode product = call(api, "GET", "/api/v1/products/" + productSlug, null);
        java.math.BigDecimal base = product.path("discountPrice").isNull() || product.path("discountPrice").isMissingNode()
            ? product.path("price").decimalValue() : product.path("discountPrice").decimalValue();
        long variant = 0;
        java.math.BigDecimal price = base;
        for (JsonNode colour : product.path("variants")) {
            if (colour.path("stockQuantity").asInt() > 0) {
                variant = colour.path("id").asLong();
                price = base.add(colour.path("additionalPrice").isNull() ? java.math.BigDecimal.ZERO : colour.path("additionalPrice").decimalValue());
                break;
            }
        }
        return new Bike(product.path("id").asLong(), variant, price);
    }

    private static Map<String, Object> orderBody(Bike bike, long showroomId, String method, Map<String, Object> installment) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerName", "Selenium Tester");
        body.put("phoneNumber", "0901234567");
        body.put("customerEmail", username + "@example.com");
        body.put("deliveryMethod", "STORE_PICKUP");
        body.put("pickupShowroomId", showroomId);
        body.put("paymentMethod", method);
        body.put("includeRegistrationService", false);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productId", bike.productId());
        item.put("quantity", 1);
        item.put("productVariantId", bike.variantId() == 0 ? null : bike.variantId());
        body.put("items", List.of(item));
        if (installment != null) {
            body.put("installment", installment);
        }
        return body;
    }

    private static Map<String, Object> plan(String id, Object income, Object percent, Object term) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("idNumber", id);
        plan.put("monthlyIncome", income);
        plan.put("downPaymentPercent", percent);
        plan.put("termMonths", term);
        return plan;
    }

    private static long showroomId() throws Exception {
        for (JsonNode room : listOf(call(api, "GET", "/api/v1/showrooms", null))) {
            if (room.path("active").asBoolean(true)) {
                return room.path("id").asLong();
            }
        }
        throw new AssertionError("no active showroom");
    }

    private static JsonNode applicationOf(HttpClient client, long orderId) throws Exception {
        return call(client, "GET", "/api/v1/installments/orders/" + orderId, null);
    }

    private static int installmentNotices(HttpClient client) {
        try {
            int count = 0;
            for (JsonNode row : listOf(call(client, "GET", "/api/v1/notifications", null))) {
                if ("INSTALLMENT".equals(row.path("type").asText())) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            return -1;
        }
    }

    private WebElement queueRow(String orderNumber) {
        return wait.until(d -> d.findElements(By.cssSelector("#data-table tbody tr")).stream()
            .filter(row -> row.getText().contains(orderNumber)).findFirst().orElse(null));
    }

    private void submitDialog(String text) {
        WebElement box = wait.until(ExpectedConditions.visibilityOfElementLocated(NOTE_BOX));
        if (!text.isEmpty()) {
            box.sendKeys(text);
        }
        click(By.cssSelector("dialog.warranty-dialog button[data-submit]"));
    }

    /** Signs the browser in as the manager, opens the queue and presses a decision button of one order. */
    private void decideInBrowser(String orderNumber, String decision, String text) {
        logIn(System.getProperty("selenium.managerUser"), System.getProperty("selenium.managerPassword"));
        driver.get(baseUrl + "/manager/installments");
        WebElement row = queueRow(orderNumber);
        click(row.findElement(By.cssSelector("[data-action='installment-decide'][data-decision='" + decision + "']")));
        submitDialog(text);
        wait.until(d -> d.findElements(By.cssSelector("#data-table tbody tr")).stream().noneMatch(r -> r.getText().contains(orderNumber)));
    }

    /** Places an instalment order through the checkout form and returns its number. */
    private String placeInstallmentOrderInBrowser() {
        openCheckoutWithOneBike();
        fill("customerName", "Selenium Tester");
        fill("phoneNumber", "0901234567");
        fill("customerEmail", username + "@example.com");
        chooseStorePickup();
        wait.until(d -> !d.findElements(By.cssSelector(".showroom-option input")).isEmpty());
        js("arguments[0].click()", driver.findElement(By.cssSelector(".showroom-option input")));
        new Select(driver.findElement(By.name("paymentMethod"))).selectByValue("INSTALLMENT");
        WebElement box = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("installmentCheckout")));
        wait.until(d -> !box.findElements(By.cssSelector("[data-field=monthly]")).isEmpty());
        fill("idNumber", ID_NUMBER);
        fill("monthlyIncome", String.valueOf(INCOME));
        click(By.id("placeOrder"));
        wait.until(ExpectedConditions.urlContains("/checkout/success"));
        return wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".order-number"))).getText();
    }

    // ---- the calculator ----

    @Test
    void theCalculatorFollowsTheSliderAndTheTermAndAgreesWithTheServer() throws Exception {
        driver.get(baseUrl + "/product/" + productSlug);
        WebElement box = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("installmentBox")));
        wait.until(d -> !box.findElements(By.cssSelector("[data-field=monthly]")).isEmpty());
        assertTrue(box.getText().contains("mô phỏng") || box.getText().toLowerCase().contains("simulation"), "the box says it is a simulation: " + box.getText());

        String amount = String.valueOf(((Number) js("return Math.round(window.kineticProductAmount())")).longValue());
        String firstMonthly = box.findElement(By.cssSelector("[data-field=monthly]")).getText();

        // 20% down, 6 months: the figure changes, and the server's quote for the same choice gives the same monthly payment
        js("const s=document.querySelector('#installmentBox [name=down]');s.value='20';s.dispatchEvent(new Event('input',{bubbles:true}))");
        click(By.cssSelector("#installmentBox input[name=term][value='6']"));
        wait.until(d -> !firstMonthly.equals(d.findElement(By.cssSelector("#installmentBox [data-field=monthly]")).getText()));
        JsonNode quote = call(api, "GET", "/api/v1/installments/quote?amount=" + amount + "&downPaymentPercent=20&termMonths=6", null);
        assertEquals(digits(quote.path("monthlyPayment").asText()).replaceFirst("0+$", ""),
            digits(driver.findElement(By.cssSelector("#installmentBox [data-field=monthly]")).getText()).replaceFirst("0+$", ""));
        assertEquals(20, quote.path("downPaymentPercent").asInt());
        assertEquals(6, quote.path("termMonths").asInt());

        // the quote is public: a visitor gets the same answer; a wrong term or down payment is a 400
        HttpClient visitor = newClient();
        assertEquals(6, call(visitor, "GET", "/api/v1/installments/quote?amount=" + amount + "&downPaymentPercent=20&termMonths=6", null).path("termMonths").asInt());
        assertRefused(400, visitor, "GET", "/api/v1/installments/quote?amount=" + amount + "&downPaymentPercent=20&termMonths=24", null);
        assertRefused(400, visitor, "GET", "/api/v1/installments/quote?amount=" + amount + "&downPaymentPercent=10&termMonths=6", null);
        assertTrue(call(visitor, "GET", "/api/v1/installments/config", null).path("terms").size() >= 1);
    }

    // ---- apply, wait, approve, pay ----

    @Test
    void applyAtCheckoutThenTheManagerApprovesAndTheDownPaymentIsTakenAtTheStore() throws Exception {
        HttpClient manager = managerApi();
        String orderNumber = placeInstallmentOrderInBrowser();
        JsonNode order = findOrder(orderNumber);
        long orderId = order.path("id").asLong();
        try {
            assertEquals("INSTALLMENT", order.path("paymentMethod").asText());
            JsonNode application = applicationOf(api, orderId);
            assertEquals("PENDING", application.path("status").asText());
            assertEquals("********6789", application.path("idNumberMasked").asText(), "only the last four digits are kept");
            assertFalse(application.toString().contains(ID_NUMBER), "the full ID number is never returned");

            // waiting for the staff: the order cannot be confirmed and the down payment cannot be taken or paid yet
            assertRefused(400, manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", "CONFIRMED"));
            long paymentId = paymentIdOf(manager, orderNumber);
            assertRefused(400, manager, "PATCH", "/api/v1/manager/payments/" + paymentId + "/confirm", Map.of());
            assertRefused(400, api, "POST", "/api/v1/payments/vnpay/create", Map.of("orderId", orderId, "language", "vn"));

            // the customer's own order page shows the waiting application
            driver.get(baseUrl + "/customer/orders/" + orderId);
            WebElement panel = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("installmentPanel")));
            assertTrue(panel.getText().contains("Chờ duyệt"), panel.getText());

            // the queue shows it with the masked number; the manager approves it with a note
            int before = installmentNotices(api);
            decideInBrowser(orderNumber, "APPROVE", "Mang theo CCCD khi nhận xe");
            JsonNode approved = applicationOf(api, orderId);
            assertEquals("APPROVED", approved.path("status").asText());
            assertEquals("Mang theo CCCD khi nhận xe", approved.path("reviewNote").asText());
            assertTrue(approved.path("canPay").asBoolean());
            assertTrue(approved.path("reviewedBy").isNull(), "the customer does not see who decided");
            wait.until(d -> installmentNotices(api) > before);

            // the customer sees the plan and the deadline on the order
            logIn(username, PASSWORD);
            driver.get(baseUrl + "/customer/orders/" + orderId);
            panel = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("installmentPanel")));
            wait.until(d -> d.findElement(By.id("installmentPanel")).getText().contains("Đã duyệt"));
            assertTrue(panel.getText().contains("Mang theo CCCD khi nhận xe"), panel.getText());
            assertEquals(digits(approved.path("downPayment").asText()).replaceFirst("0+$", ""),
                digits(panel.findElement(By.cssSelector("[data-field=down]")).getText()).replaceFirst("0+$", ""));

            // a second decision is a conflict, not a second verdict
            assertRefused(409, manager, "PATCH", "/api/v1/manager/installments/" + approved.path("id").asLong(), Map.of("decision", "REJECT", "note", "late"));

            // the down payment is taken at the store: the payment row holds the down payment, and the order moves on by itself
            JsonNode payment = null;
            for (JsonNode row : listOf(call(manager, "GET", "/api/v1/manager/payments?search=" + orderNumber, null))) {
                payment = row;
            }
            assertEquals(0, payment.path("amount").decimalValue().compareTo(approved.path("downPayment").decimalValue()), "the payment is the down payment, not the whole order");
            // the manager presses the button on the payments page (an unapproved application showed none: the API refused above)
            logIn(System.getProperty("selenium.managerUser"), System.getProperty("selenium.managerPassword"));
            driver.get(baseUrl + "/manager/payments");
            WebElement paymentRow = wait.until(d -> d.findElements(By.cssSelector("#data-table tbody tr")).stream()
                .filter(r -> r.getText().contains(orderNumber) && !r.findElements(By.cssSelector("[data-action='confirm-payment']")).isEmpty()).findFirst().orElse(null));
            click(paymentRow.findElement(By.cssSelector("[data-action='confirm-payment']")));
            wait.until(d -> "CONFIRMED".equals(orderStatusOf(orderId)));
            logIn(username, PASSWORD);
            assertEquals("CONFIRMED", call(api, "GET", "/api/v1/orders/" + orderId, null).path("status").asText());
            assertFalse(applicationOf(api, orderId).path("canPay").asBoolean(), "nothing left to pay once the down payment is in");

            // the pages speak English
            driver.get(baseUrl + "/lang/en?back=/customer/orders/" + orderId);
            wait.until(d -> d.findElement(By.id("installmentPanel")).getText().contains("Instalments (simulation)"));
        } finally {
            driver.get(baseUrl + "/lang/vi?back=/");
            cancelQuietly(orderId);
        }
    }

    private static String orderStatusOf(long orderId) {
        try {
            return call(api, "GET", "/api/v1/orders/" + orderId, null).path("status").asText();
        } catch (Exception e) {
            return "";
        }
    }

    private static long paymentIdOf(HttpClient manager, String orderNumber) throws Exception {
        for (JsonNode row : listOf(call(manager, "GET", "/api/v1/manager/payments?search=" + orderNumber, null))) {
            if (orderNumber.equals(row.path("orderNumber").asText())) {
                return row.path("id").asLong();
            }
        }
        throw new AssertionError("no payment row for " + orderNumber);
    }

    private static void cancelQuietly(long orderId) {
        try {
            call(api, "POST", "/api/v1/orders/" + orderId + "/cancellation-request", Map.of("reason", "Selenium test cleanup"));
        } catch (Exception ignored) {
            // cleanup only: an order that cannot be cancelled any more is left as it is
        }
    }

    // ---- reject ----

    @Test
    void rejectingAnApplicationCancelsTheOrderWithTheReasonAndTellsTheCustomer() throws Exception {
        HttpClient manager = managerApi();
        Bike bike = bike();
        JsonNode order = call(api, "POST", "/api/v1/orders", orderBody(bike, showroomId(), "INSTALLMENT", plan(ID_NUMBER, INCOME, 30, 12)));
        long orderId = order.path("id").asLong();
        String orderNumber = order.path("orderNumber").asText();
        try {
            int before = installmentNotices(api);
            decideInBrowser(orderNumber, "REJECT", "Thu nhập chưa đủ điều kiện");
            assertEquals("REJECTED", applicationOf(api, orderId).path("status").asText());
            JsonNode cancelled = call(api, "GET", "/api/v1/orders/" + orderId, null);
            assertEquals("CANCELLED", cancelled.path("status").asText());
            assertTrue(cancelled.path("cancellationReason").asText().contains("Thu nhập chưa đủ điều kiện"));
            wait.until(d -> installmentNotices(api) > before);

            // the verdict is kept after the cancellation closed the open application; nothing is left to pay
            JsonNode kept = applicationOf(api, orderId);
            assertEquals("REJECTED", kept.path("status").asText());
            assertFalse(kept.path("canPay").asBoolean());
            assertRefused(409, manager, "PATCH", "/api/v1/manager/installments/" + kept.path("id").asLong(), Map.of("decision", "APPROVE"));

            logIn(username, PASSWORD);
            driver.get(baseUrl + "/customer/orders/" + orderId);
            WebElement panel = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("installmentPanel")));
            wait.until(d -> d.findElement(By.id("installmentPanel")).getText().contains("Thu nhập chưa đủ điều kiện"));
            assertTrue(panel.getText().contains("Bị từ chối"), panel.getText());
        } finally {
            cancelQuietly(orderId);
        }
    }

    @Test
    void cancellingTheOrderClosesAWaitingApplication() throws Exception {
        Bike bike = bike();
        JsonNode order = call(api, "POST", "/api/v1/orders", orderBody(bike, showroomId(), "INSTALLMENT", plan(ID_NUMBER, INCOME, 30, 12)));
        long orderId = order.path("id").asLong();
        call(api, "POST", "/api/v1/orders/" + orderId + "/cancellation-request", Map.of("reason", "Đổi ý"));
        assertEquals("CANCELLED", applicationOf(api, orderId).path("status").asText());
        assertFalse(applicationOf(api, orderId).path("canDecide").asBoolean(), "nobody can decide an application whose order is gone");
        HttpClient manager = managerApi();
        assertRefused(409, manager, "PATCH", "/api/v1/manager/installments/" + applicationOf(api, orderId).path("id").asLong(), Map.of("decision", "APPROVE"));
    }

    // ---- the rules and who may do what ----

    @Test
    void theRulesAnswerFourHundredsAndTheRolesAreKept() throws Exception {
        HttpClient manager = managerApi();
        Bike bike = bike();
        long showroom = showroomId();

        // a visitor is told to sign in; a staff account is refused (instalments are for customers)
        assertRefused(401, newClient(), "POST", "/api/v1/orders", orderBody(bike, showroom, "INSTALLMENT", plan(ID_NUMBER, INCOME, 30, 12)));
        assertRefused(403, manager, "POST", "/api/v1/orders", orderBody(bike, showroom, "INSTALLMENT", plan(ID_NUMBER, INCOME, 30, 12)));

        // the application is checked before any stock is held
        assertRefused(400, api, "POST", "/api/v1/orders", orderBody(bike, showroom, "INSTALLMENT", null));
        assertRefused(400, api, "POST", "/api/v1/orders", orderBody(bike, showroom, "INSTALLMENT", plan("12345", INCOME, 30, 12)));
        assertRefused(400, api, "POST", "/api/v1/orders", orderBody(bike, showroom, "INSTALLMENT", plan(ID_NUMBER, null, 30, 12)));
        assertRefused(400, api, "POST", "/api/v1/orders", orderBody(bike, showroom, "INSTALLMENT", plan(ID_NUMBER, 1_000_000, 30, 12)));
        assertRefused(400, api, "POST", "/api/v1/orders", orderBody(bike, showroom, "INSTALLMENT", plan(ID_NUMBER, INCOME, 10, 12)));
        assertRefused(400, api, "POST", "/api/v1/orders", orderBody(bike, showroom, "INSTALLMENT", plan(ID_NUMBER, INCOME, 60, 12)));
        assertRefused(400, api, "POST", "/api/v1/orders", orderBody(bike, showroom, "INSTALLMENT", plan(ID_NUMBER, INCOME, 30, 24)));
        // the monthly payment may not pass 60% of the declared income
        assertRefused(400, api, "POST", "/api/v1/orders", orderBody(bike, showroom, "INSTALLMENT", plan(ID_NUMBER, 3_000_000, 20, 6)));

        // an application belongs to its order: another customer sees nothing, a customer cannot use the staff endpoints
        JsonNode order = call(api, "POST", "/api/v1/orders", orderBody(bike, showroom, "INSTALLMENT", plan(ID_NUMBER, INCOME, 30, 12)));
        long orderId = order.path("id").asLong();
        HttpClient other = newClient();
        String name = "inst" + System.currentTimeMillis();
        try {
            call(other, "POST", "/api/v1/auth/register", Map.of("username", name, "email", name + "@example.com", "password", PASSWORD, "firstName", "Other", "lastName", "Customer"));
            call(other, "POST", "/api/v1/auth/login", Map.of("usernameOrEmail", name, "password", PASSWORD));
            assertRefused(404, other, "GET", "/api/v1/installments/orders/" + orderId, null);
            assertRefused(403, other, "GET", "/api/v1/manager/installments", null);
            assertRefused(403, other, "PATCH", "/api/v1/manager/installments/" + applicationOf(api, orderId).path("id").asLong(), Map.of("decision", "APPROVE"));
            assertRefused(403, api, "GET", "/api/v1/manager/installments/backlog", null);
            assertRefused(401, newClient(), "GET", "/api/v1/installments/orders/" + orderId, null);

            // the manager's queue lists the waiting application and the backlog counts it
            boolean listed = false;
            for (JsonNode row : listOf(call(manager, "GET", "/api/v1/manager/installments", null))) {
                listed |= orderId == row.path("orderId").asLong();
            }
            assertTrue(listed, "the waiting application is in the queue");
            assertTrue(call(manager, "GET", "/api/v1/manager/installments/backlog", null).path("pending").asLong() >= 1);
            assertRefused(400, manager, "GET", "/api/v1/manager/installments?status=WHATEVER", null);
            assertRefused(400, manager, "PATCH", "/api/v1/manager/installments/" + applicationOf(api, orderId).path("id").asLong(), Map.of("decision", "REJECT", "note", " "));
        } finally {
            cancelQuietly(orderId);
            try {
                call(other, "DELETE", "/api/v1/auth/account", Map.of("password", PASSWORD, "confirmEmail", name + "@example.com"));
            } catch (Exception ignored) {
                // cleanup only: the account stays as a harmless test customer
            }
        }
    }

    // ---- the checkout form ----

    @Test
    void checkoutShowsTheApplicationOnlyForInstalmentsAndRefusesAnEmptyOne() throws Exception {
        openCheckoutWithOneBike();
        WebElement box = driver.findElement(By.id("installmentCheckout"));
        assertFalse(box.isDisplayed(), "the application is hidden for the other payment methods");
        fill("customerName", "Selenium Tester");
        fill("phoneNumber", "0901234567");
        fill("customerEmail", username + "@example.com");
        chooseStorePickup();
        wait.until(d -> !d.findElements(By.cssSelector(".showroom-option input")).isEmpty());
        js("arguments[0].click()", driver.findElement(By.cssSelector(".showroom-option input")));
        new Select(driver.findElement(By.name("paymentMethod"))).selectByValue("INSTALLMENT");
        wait.until(ExpectedConditions.visibilityOf(box));
        wait.until(d -> !box.findElements(By.cssSelector("[data-field=monthly]")).isEmpty());
        assertTrue(box.getText().contains("mô phỏng"), box.getText());

        // an empty application is refused in the page, and no order is made
        click(By.id("placeOrder"));
        String message = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("checkoutError"))).getText();
        assertFalse(message.isBlank());
        assertTrue(driver.getCurrentUrl().contains("/checkout") && !driver.getCurrentUrl().contains("success"));

        // a payment above the share of the income is warned about before the order is sent
        fill("idNumber", ID_NUMBER);
        // an income of half the monthly payment is far under what the payment needs (60% of the income at most), whatever the bike costs
        long monthly = Long.parseLong(digits(box.findElement(By.cssSelector("[data-field=monthly]")).getText()));
        fill("monthlyIncome", String.valueOf(Math.max(1, monthly / 2)));
        wait.until(d -> !d.findElement(By.cssSelector("#installmentCheckout [data-affordability]")).getText().isBlank());

        // the other methods hide it again
        new Select(driver.findElement(By.name("paymentMethod"))).selectByValue("PAY_LATER");
        wait.until(ExpectedConditions.invisibilityOf(box));
        assertNotEquals("", driver.findElement(By.id("placeOrder")).getText());
    }

    @Test
    void theManagerQueueSpeaksEnglish() throws Exception {
        managerApi();
        logIn(System.getProperty("selenium.managerUser"), System.getProperty("selenium.managerPassword"));
        try {
            driver.get(baseUrl + "/lang/en?back=/manager/installments");
            wait.until(d -> d.findElement(By.cssSelector("h1")).getText().contains("Instalment applications"));
            wait.until(d -> d.findElement(By.id("empty")).isDisplayed() || !d.findElements(By.cssSelector("#data-table tbody tr")).isEmpty());
        } finally {
            driver.get(baseUrl + "/lang/vi?back=/");
        }
    }
}
