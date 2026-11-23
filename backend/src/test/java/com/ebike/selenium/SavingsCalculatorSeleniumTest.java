package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * V33: "how much does an electric bike save". On the page of a bike with its battery data the box shows the saving for 20 km a day and follows the rider's
 * numbers; a visitor sees it too; a wrong number shows a message instead of figures; the API answers 404 for an unknown bike and 400 for a number out of
 * range and is never cached; the figures agree with the API; the page speaks English and does not overflow a phone; the price the admin sets changes the
 * answer (needs the admin account, otherwise skipped).
 *
 * <p>Run the pricing script update_savings_calculator.sql first. Start the app with APP_RATE_LIMIT_ENABLED=false.
 *
 * <pre>
 * mvn test -Dtest=SavingsCalculatorSeleniumTest -Dselenium.baseUrl=http://localhost:8080 [-Dselenium.adminUser=... -Dselenium.adminPassword=...]
 * </pre>
 */
@Tag("selenium")
@Execution(ExecutionMode.SAME_THREAD)
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class SavingsCalculatorSeleniumTest extends StorefrontSeleniumTest {

    private static final By BOX = By.id("savingsBox");
    private static final By MONTHLY_SAVING = By.cssSelector("#savingsBox [data-field=monthly-saving]");
    private static final By KM = By.cssSelector("#savingsBox input[name=kmPerDay]");
    private static final By DAYS = By.cssSelector("#savingsBox input[name=daysPerMonth]");
    private static final By PROBLEM = By.cssSelector("#savingsBox .installment-problem");

    private static String slug;

    /** The first bike whose specification has what the calculation needs; the base class picked one only for its stock. */
    @BeforeAll
    static void findABikeWithBatteryData() throws Exception {
        slug = null;
        for (JsonNode product : listOf(call(api, "GET", "/api/v1/products", null))) {
            String candidate = product.path("slug").asText();
            JsonNode answer = call(api, "GET", "/api/v1/products/" + candidate + "/savings", null);
            if (answer.path("calculable").asBoolean()) {
                slug = candidate;
                break;
            }
        }
        assumeTrue(slug != null, "no bike has battery and range data (or update_savings_calculator.sql was not applied)");
    }

    private static String digits(String text) {
        return text.replaceAll("[^0-9]", "");
    }

    private WebElement openBike() {
        driver.get(baseUrl + "/product/" + slug);
        return wait.until(ExpectedConditions.visibilityOfElementLocated(BOX));
    }

    /**
     * Replaces what is in the box the way a person does, so that the page gets an input event for every value (WebDriver's clear() sends none, so an empty
     * box would never be seen by the page).
     */
    private void type(By locator, String value) {
        WebElement input = driver.findElement(locator);
        input.click();
        input.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        input.sendKeys(value.isEmpty() ? Keys.BACK_SPACE.toString() : value);
    }

    @Test
    void theBoxShowsTheSavingForTheDefaultAndFollowsTheRider() throws Exception {
        openBike();
        JsonNode defaults = call(api, "GET", "/api/v1/products/" + slug + "/savings", null);
        assertEquals("20", driver.findElement(KM).getAttribute("value"));
        assertEquals("26", driver.findElement(DAYS).getAttribute("value"));
        wait.until(d -> digits(d.findElement(MONTHLY_SAVING).getText()).equals(defaults.path("monthlySaving").decimalValue().toBigInteger().toString()));

        String before = digits(driver.findElement(MONTHLY_SAVING).getText());
        type(KM, "60");
        JsonNode far = call(api, "GET", "/api/v1/products/" + slug + "/savings?kmPerDay=60&daysPerMonth=26", null);
        wait.until(d -> digits(d.findElement(MONTHLY_SAVING).getText()).equals(far.path("monthlySaving").decimalValue().toBigInteger().toString()));
        assertNotEquals(before, digits(driver.findElement(MONTHLY_SAVING).getText()), "more kilometres, a different figure");
        // the sentence under the figures names the price of electricity (as the page writes money), the share of the range and the charger efficiency
        String assumptions = driver.findElement(By.cssSelector("#savingsBox [data-assumptions]")).getText();
        String electricity = java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("vi-VN"))
            .format(defaults.path("electricityPricePerKwh").decimalValue().toBigInteger());
        assertTrue(assumptions.contains(electricity), "the price of a kWh (" + electricity + ") is in: " + assumptions);
        assertTrue(assumptions.contains(defaults.path("realRangePercent").asInt() + "%"), "the share of the published range is in: " + assumptions);
        assertTrue(assumptions.contains(defaults.path("chargerEfficiencyPercent").asInt() + "%"), "the charger efficiency is in: " + assumptions);
    }

    @Test
    void aVisitorSeesItToo() {
        driver.manage().deleteAllCookies();
        openBike();
        assertTrue(driver.findElement(MONTHLY_SAVING).isDisplayed());
    }

    @Test
    void aWrongNumberShowsAMessageNotFigures() {
        openBike();
        for (String bad : new String[] {"0", "5000", "", "2.5"}) {
            // a right number first, so that the message waited for below is this value's own and not one left by the previous value
            type(KM, "15");
            wait.until(ExpectedConditions.visibilityOfElementLocated(MONTHLY_SAVING));
            assertTrue(driver.findElements(PROBLEM).isEmpty(), "the message goes away with a right number");
            type(KM, bad);
            wait.until(d -> d.findElements(MONTHLY_SAVING).isEmpty() && d.findElement(PROBLEM).getText().contains("số km mỗi ngày"));
        }
        type(KM, "15");
        wait.until(ExpectedConditions.visibilityOfElementLocated(MONTHLY_SAVING));
        type(DAYS, "40");
        wait.until(d -> d.findElements(MONTHLY_SAVING).isEmpty() && d.findElement(PROBLEM).getText().contains("số ngày đi mỗi tháng"));
    }

    @Test
    void theApiRefusesWhatIsWrongAndIsNeverCached() throws Exception {
        IllegalStateException unknown = assertThrows(IllegalStateException.class,
            () -> call(api, "GET", "/api/v1/products/no-such-bike-v33/savings", null), "an unknown bike is refused");
        assertTrue(unknown.getMessage().contains("-> 404"), "404 for an unknown bike: " + unknown.getMessage());
        for (String query : new String[] {"kmPerDay=0", "kmPerDay=301", "daysPerMonth=0", "daysPerMonth=32", "kmPerDay=abc"}) {
            IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> call(api, "GET", "/api/v1/products/" + slug + "/savings?" + query, null), query);
            assertTrue(refused.getMessage().contains("-> 400"), query + ": " + refused.getMessage());
        }
        java.net.http.HttpResponse<String> response = api.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl + "/api/v1/products/" + slug + "/savings")).build(),
            java.net.http.HttpResponse.BodyHandlers.ofString());
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"), "the prices can change at any time");
    }

    @Test
    void theBoxSpeaksEnglish() {
        driver.get(baseUrl + "/lang/en?back=/product/" + slug);
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(BOX));
            String text = driver.findElement(BOX).getText();
            assertTrue(text.contains("How much does an electric bike save?"), text);
            wait.until(d -> d.findElement(BOX).getText().contains("Charging cost a month"));
            assertFalse(driver.findElement(BOX).getText().matches("(?s).*(Tiền sạc|Tiết kiệm|tiết kiệm).*"), "no Vietnamese is left in the box");
            type(KM, "0");
            wait.until(d -> d.findElement(PROBLEM).getText().contains("whole number"));
        } finally {
            // the language is saved on the test customer's account, so a failure above must not leave the next tests in English
            driver.get(baseUrl + "/lang/vi?back=/product/" + slug);
        }
    }

    @Test
    void theBoxFitsAPhone() {
        driver.manage().window().setSize(new org.openqa.selenium.Dimension(360, 740));
        openBike();
        wait.until(ExpectedConditions.visibilityOfElementLocated(MONTHLY_SAVING));
        Object overflow = js("return document.documentElement.scrollWidth > document.documentElement.clientWidth + 1");
        assertEquals(Boolean.FALSE, overflow, "the page must not scroll sideways on a 360 px screen");
        Object boxWidth = js("return document.querySelector('#savingsBox').getBoundingClientRect().right <= document.documentElement.clientWidth");
        assertEquals(Boolean.TRUE, boxWidth);
    }

    @Test
    void thePriceTheAdminSetsChangesTheAnswer() throws Exception {
        String user = System.getProperty("selenium.adminUser", "");
        String password = System.getProperty("selenium.adminPassword", "");
        assumeTrue(!user.isBlank() && !password.isBlank(), "selenium.adminUser/selenium.adminPassword not set");
        HttpClient admin = newClient();
        call(admin, "POST", "/api/v1/auth/login", Map.of("usernameOrEmail", user, "password", password));
        JsonNode rule = null;
        for (JsonNode candidate : listOf(call(admin, "GET", "/api/v1/admin/pricing-rules", null))) {
            if ("SAVINGS_PETROL_PRICE".equals(candidate.path("code").asText())) {
                rule = candidate;
            }
        }
        assumeTrue(rule != null, "database has no SAVINGS_PETROL_PRICE rule; apply backend/database/update_savings_calculator.sql");
        long id = rule.path("id").asLong();
        String originalAmount = rule.path("amount").decimalValue().toPlainString();
        String originalStatus = rule.path("status").asText();
        try {
            // a value that is not above zero, or far too large, is refused by the admin console
            assertThrows(IllegalStateException.class, () -> call(admin, "PATCH", "/api/v1/admin/pricing-rules/" + id, Map.of("amount", 0, "status", "ACTIVE")));
            assertThrows(IllegalStateException.class, () -> call(admin, "PATCH", "/api/v1/admin/pricing-rules/" + id, Map.of("amount", 999999, "status", "ACTIVE")));

            call(admin, "PATCH", "/api/v1/admin/pricing-rules/" + id, Map.of("amount", 40000, "status", "ACTIVE"));
            JsonNode dearPetrol = call(api, "GET", "/api/v1/products/" + slug + "/savings", null);
            assertEquals(40000, dearPetrol.path("petrolPricePerLiter").asInt());
            openBike();
            wait.until(d -> digits(d.findElement(MONTHLY_SAVING).getText()).equals(dearPetrol.path("monthlySaving").decimalValue().toBigInteger().toString()));

            call(admin, "PATCH", "/api/v1/admin/pricing-rules/" + id, Map.of("amount", 10000, "status", "ACTIVE"));
            JsonNode cheapPetrol = call(api, "GET", "/api/v1/products/" + slug + "/savings", null);
            assertTrue(cheapPetrol.path("monthlySaving").asLong() < dearPetrol.path("monthlySaving").asLong(), "cheaper petrol, smaller saving");
        } finally {
            call(admin, "PATCH", "/api/v1/admin/pricing-rules/" + id, Map.of("amount", new java.math.BigDecimal(originalAmount), "status", originalStatus));
        }
    }
}
