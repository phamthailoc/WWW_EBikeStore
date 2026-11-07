package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ebike.util.order.VnPayUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end checks of the VNPay checkout flow in a real Chrome browser.
 *
 * <p>Needs the application already running with VNPay keys set (fake values are fine, the
 * gateway page itself is never used). Skipped unless {@code selenium.baseUrl} is given:
 *
 * <pre>
 * mvn test -Dtest=VnPayCheckoutSeleniumTest \
 *     -Dselenium.baseUrl=http://localhost:8080 \
 *     -Dselenium.vnpayHashSecret=&lt;same value as VNPAY_HASH_SECRET&gt;
 * </pre>
 *
 * Add {@code -Dselenium.headless=false} to watch the browser. Each run registers one throwaway
 * customer, places orders as that customer and cancels them at the end so stock is restored.
 */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class VnPayCheckoutSeleniumTest {

    private static final String PASSWORD = "Selenium@123";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static String baseUrl;
    private static String hashSecret;
    private static String username;
    private static String productSlug;
    private static HttpClient api;

    private WebDriver driver;
    private WebDriverWait wait;

    @BeforeAll
    static void prepareCustomerAndProduct() throws Exception {
        baseUrl = System.getProperty("selenium.baseUrl").replaceAll("/+$", "");
        hashSecret = System.getProperty("selenium.vnpayHashSecret", "");
        assumeTrue(!hashSecret.isBlank(), "selenium.vnpayHashSecret is required to sign fake VNPay results");

        api = HttpClient.newBuilder().cookieHandler(new CookieManager()).connectTimeout(Duration.ofSeconds(10)).build();
        username = "selenium" + System.currentTimeMillis();
        JsonNode registered = call("POST", "/api/v1/auth/register", Map.of(
            "username", username, "email", username + "@example.com", "password", PASSWORD,
            "firstName", "Selenium", "lastName", "Tester"));
        assertTrue(registered != null, "could not register the test customer");
        call("POST", "/api/v1/auth/login", Map.of("usernameOrEmail", username, "password", PASSWORD));

        for (JsonNode product : listOf(call("GET", "/api/v1/products", null))) {
            if (product.path("stockQuantity").asInt() >= 5) {
                productSlug = product.path("slug").asText();
                break;
            }
        }
        assumeTrue(productSlug != null, "no product with at least 5 bikes in stock");
    }

    @AfterAll
    static void cancelTestOrders() throws Exception {
        if (api == null) {
            return;
        }
        for (JsonNode order : listOf(call("GET", "/api/v1/orders", null))) {
            String status = order.path("status").asText();
            if (!List.of("CANCELLED", "SHIPPED", "DELIVERED").contains(status)) {
                call("POST", "/api/v1/orders/" + order.path("id").asLong() + "/cancellation-request",
                    Map.of("reason", "Selenium test cleanup"));
            }
        }
    }

    @BeforeEach
    void openBrowserAndLogIn() {
        ChromeOptions options = new ChromeOptions();
        if (!"false".equals(System.getProperty("selenium.headless"))) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=1366,900", "--lang=vi");
        // Keep the browser on the signed payment URL: with fake keys the real sandbox would immediately
        // redirect to its error page and the test could no longer read vnp_TxnRef from the address bar.
        String gatewayHost = System.getProperty("selenium.gatewayHost", "sandbox.vnpayment.vn");
        options.addArguments("--host-resolver-rules=MAP " + gatewayHost + " 127.0.0.1:9");
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));

        driver.get(baseUrl + "/login");
        driver.findElement(By.name("usernameOrEmail")).sendKeys(username);
        driver.findElement(By.name("password")).sendKeys(PASSWORD);
        driver.findElement(By.cssSelector("#loginForm button")).click();
        wait.until(d -> !d.getCurrentUrl().contains("/login"));
    }

    @AfterEach
    void closeBrowser() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    void checkoutOffersVnPayWhenItIsConfigured() {
        openCheckoutWithOneBike();

        List<String> methods = new Select(driver.findElement(By.name("paymentMethod"))).getOptions()
            .stream().map(option -> option.getAttribute("value")).toList();
        assertEquals(List.of("PAY_LATER", "VNPAY"), methods);
    }

    @Test
    void vnpayOrderGoesToGatewayAndSuccessfulReturnMarksItPaid() {
        Map<String, String> gateway = placeVnPayOrder();
        assertEquals(baseUrl + "/payment/return", gateway.get("vnp_ReturnUrl"));
        assertTrue(signatureIsValid(gateway), "payment URL must be signed with the configured secret");

        driver.get(returnUrl(signedResult(gateway, "00")));

        assertEquals("Thanh toán thành công", heading());
        assertEquals(orderNumber(gateway), driver.findElement(By.cssSelector(".order-number")).getText());
        assertTrue(pageText().contains("PAID"));
        assertTrue(driver.findElements(By.id("retryVnPay")).isEmpty(), "paid orders must not offer a retry");

        driver.get(baseUrl + "/customer/orders");
        WebElement row = orderRow(orderNumber(gateway));
        assertTrue(row.getText().contains("PAID"));
        assertTrue(row.findElements(By.cssSelector("[data-action='pay-vnpay']")).isEmpty());
    }

    @Test
    void cancelledPaymentExplainsWhyAndRetryReturnsToGateway() {
        Map<String, String> gateway = placeVnPayOrder();

        driver.get(returnUrl(signedResult(gateway, "24")));

        assertEquals("Thanh toán chưa thành công", heading());
        assertTrue(pageText().contains("Bạn đã hủy giao dịch."));
        click(By.id("retryVnPay"));

        Map<String, String> retried = waitForGateway();
        assertEquals(orderNumber(gateway), orderNumber(retried));
        assertNotEquals(gateway.get("vnp_TxnRef"), retried.get("vnp_TxnRef"), "a retry must use a new transaction reference");
    }

    @Test
    void tamperedReturnIsRejected() {
        Map<String, String> gateway = placeVnPayOrder();
        Map<String, String> result = signedResult(gateway, "00");
        result.put("vnp_Amount", "100"); // changed after signing

        driver.get(returnUrl(result));

        assertEquals("Không xác minh được kết quả thanh toán", heading());
        assertTrue(driver.findElements(By.id("retryVnPay")).isEmpty());

        driver.get(baseUrl + "/customer/orders");
        assertFalse(orderRow(orderNumber(gateway)).getText().contains("PAID"), "a forged result must not mark the order paid");
    }

    @Test
    void unpaidVnPayOrderCanBePaidFromMyOrders() {
        Map<String, String> gateway = placeVnPayOrder();

        driver.get(baseUrl + "/customer/orders");
        click(orderRow(orderNumber(gateway)).findElement(By.cssSelector("[data-action='pay-vnpay']")));

        Map<String, String> again = waitForGateway();
        assertEquals(orderNumber(gateway), orderNumber(again));
    }

    // ---- browser steps -------------------------------------------------------------------

    private void openCheckoutWithOneBike() {
        driver.get(baseUrl + "/product/" + productSlug);
        List<WebElement> colors = driver.findElements(By.cssSelector(".variant-option"));
        if (!colors.isEmpty() && driver.findElements(By.cssSelector(".variant-option.selected")).isEmpty()) {
            click(By.cssSelector(".variant-option"));
        }
        click(By.id("addToCart"));
        wait.until(d -> {
            Object cart = ((org.openqa.selenium.JavascriptExecutor) d).executeScript("return localStorage.getItem('kinetic_cart')");
            return cart != null && !"[]".equals(cart);
        });
        driver.get(baseUrl + "/checkout");
        wait.until(ExpectedConditions.elementToBeClickable(By.id("placeOrder")));
    }

    private Map<String, String> placeVnPayOrder() {
        openCheckoutWithOneBike();
        fill("customerName", "Selenium Tester");
        fill("phoneNumber", "0901234567");
        fill("customerEmail", username + "@example.com");
        Select province = new Select(driver.findElement(By.name("province")));
        if (province.getFirstSelectedOption().getAttribute("value").isBlank()) {
            province.selectByIndex(1);
        }
        fill("detailedAddress", "123 Đường Kiểm Thử, Phường 1");
        new Select(driver.findElement(By.name("paymentMethod"))).selectByValue("VNPAY");
        click(By.id("placeOrder"));
        return waitForGateway();
    }

    private Map<String, String> waitForGateway() {
        try {
            // Our own /payment/return URL also carries vnp_TxnRef, so only accept addresses outside the app.
            wait.until(d -> !d.getCurrentUrl().startsWith(baseUrl) && d.getCurrentUrl().contains("vnp_TxnRef="));
        } catch (org.openqa.selenium.TimeoutException e) {
            throw new AssertionError("never reached the VNPay gateway; " + pageState("gateway"), e);
        }
        return query(driver.getCurrentUrl());
    }

    private void click(By locator) {
        click(wait.until(ExpectedConditions.presenceOfElementLocated(locator)));
    }

    /** Scrolls the element to the middle of the viewport first; the storefront scrolls inside its own layout. */
    private void click(WebElement element) {
        js("arguments[0].scrollIntoView({block:'center',behavior:'instant'})", element);
        try {
            wait.until(ExpectedConditions.elementToBeClickable(element)).click();
        } catch (org.openqa.selenium.ElementClickInterceptedException e) {
            Object blocker = js("const r=arguments[0].getBoundingClientRect();"
                + "const el=document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);return el?el.outerHTML.slice(0,300):null", element);
            throw new AssertionError("click blocked by " + blocker + "; " + pageState("click"), e);
        }
    }

    private Object js(String script, Object... args) {
        return ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(script, args);
    }

    /** Current URL, visible error messages and a screenshot saved under target/selenium for failure reports. */
    private String pageState(String label) {
        String errors = String.valueOf(js("return [...document.querySelectorAll('#checkoutError,#notice,#vnpayError,#productNotice')]"
            + ".filter(e=>!e.hidden&&e.textContent.trim()).map(e=>e.id+': '+e.textContent.trim()).join(' | ')"));
        String screenshot = "";
        try {
            java.nio.file.Path dir = java.nio.file.Files.createDirectories(java.nio.file.Path.of("target", "selenium"));
            java.nio.file.Path file = dir.resolve(label + "-" + System.currentTimeMillis() + ".png");
            java.nio.file.Files.write(file, ((org.openqa.selenium.TakesScreenshot) driver).getScreenshotAs(org.openqa.selenium.OutputType.BYTES));
            screenshot = file.toAbsolutePath().toString();
        } catch (Exception ignored) {
            // the failure message is still useful without a screenshot
        }
        return "url=" + driver.getCurrentUrl() + ", messages=[" + errors + "], screenshot=" + screenshot;
    }

    private void fill(String name, String value) {
        WebElement field = driver.findElement(By.name(name));
        field.clear();
        field.sendKeys(value);
    }

    private WebElement orderRow(String orderNumber) {
        return wait.until(d -> d.findElements(By.cssSelector("#data-table tbody tr")).stream()
            .filter(row -> row.getText().contains(orderNumber)).findFirst().orElse(null));
    }

    private String heading() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".order-success h2"))).getText();
    }

    private String pageText() {
        return driver.findElement(By.cssSelector(".order-success")).getText();
    }

    // ---- fake VNPay gateway ----------------------------------------------------------------

    /** Builds the query VNPay would send back, signed the same way the real gateway signs it. */
    private static Map<String, String> signedResult(Map<String, String> gateway, String responseCode) {
        Map<String, String> result = new HashMap<>();
        result.put("vnp_TmnCode", gateway.get("vnp_TmnCode"));
        result.put("vnp_TxnRef", gateway.get("vnp_TxnRef"));
        result.put("vnp_Amount", gateway.get("vnp_Amount"));
        result.put("vnp_OrderInfo", gateway.get("vnp_OrderInfo"));
        result.put("vnp_ResponseCode", responseCode);
        result.put("vnp_TransactionStatus", "00".equals(responseCode) ? "00" : "02");
        result.put("vnp_TransactionNo", String.valueOf(System.currentTimeMillis() % 100_000_000));
        result.put("vnp_BankCode", "NCB");
        result.put("vnp_CardType", "ATM");
        result.put("vnp_PayDate", VnPayUtil.formatDate(LocalDateTime.now()));
        result.put("vnp_SecureHash", VnPayUtil.hmacSHA512(hashSecret, VnPayUtil.buildQuery(result)));
        return result;
    }

    private static boolean signatureIsValid(Map<String, String> params) {
        Map<String, String> signed = new HashMap<>(params);
        String hash = signed.remove("vnp_SecureHash");
        signed.remove("vnp_SecureHashType");
        return VnPayUtil.hmacSHA512(hashSecret, VnPayUtil.buildQuery(signed)).equalsIgnoreCase(hash);
    }

    private static String returnUrl(Map<String, String> params) {
        Map<String, String> unsigned = new HashMap<>(params);
        String hash = unsigned.remove("vnp_SecureHash");
        return baseUrl + "/payment/return?" + VnPayUtil.buildQuery(unsigned) + "&vnp_SecureHash=" + hash;
    }

    private static String orderNumber(Map<String, String> gateway) {
        // vnp_OrderInfo is "Thanh toan don hang <orderNumber> - Kinetic E-Bike"
        return gateway.get("vnp_OrderInfo").replaceFirst("^Thanh toan don hang ", "").replaceFirst(" - Kinetic E-Bike$", "");
    }

    private static Map<String, String> query(String url) {
        Map<String, String> params = new LinkedHashMap<>();
        String query = URI.create(url).getRawQuery();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            params.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
        }
        return params;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    // ---- REST helpers for setup and cleanup ---------------------------------------------------

    private static JsonNode call(String method, String path, Object body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json");
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        }
        HttpResponse<String> response = api.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException(method + " " + path + " -> " + response.statusCode() + ": " + response.body());
        }
        return response.body().isBlank() ? null : JSON.readTree(response.body());
    }

    private static Iterable<JsonNode> listOf(JsonNode node) {
        if (node == null) {
            return List.of();
        }
        return node.isArray() ? node : node.path("content");
    }
}
