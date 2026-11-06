package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Shared setup for storefront tests that drive a real Chrome against an already running app.
 *
 * <p>Skipped unless {@code -Dselenium.baseUrl=http://localhost:8080} is given. Each test class
 * registers one throwaway customer, logs Chrome in as that customer before every test, and cancels
 * the customer's open orders afterwards so stock is restored. Optional properties:
 * {@code selenium.headless=false} to watch the browser, {@code selenium.managerUser} and
 * {@code selenium.managerPassword} for tests that need a manager account.
 */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
abstract class StorefrontSeleniumTest {

    protected static final String PASSWORD = "Selenium@123";
    protected static final ObjectMapper JSON = new ObjectMapper();

    protected static String baseUrl;
    protected static String username;
    protected static String productSlug;
    /** REST client logged in as the test customer. */
    protected static HttpClient api;

    protected WebDriver driver;
    protected WebDriverWait wait;

    @BeforeAll
    static void prepareCustomerAndProduct() throws Exception {
        // Condition annotations on this abstract class are not inherited, so guard here as well:
        // a plain `mvn test` must skip these tests instead of failing.
        String url = System.getProperty("selenium.baseUrl", "");
        assumeTrue(url.matches("https?://.+"), "selenium.baseUrl not set; Selenium tests skipped");
        baseUrl = url.replaceAll("/+$", "");
        api = newClient();
        username = "selenium" + System.currentTimeMillis();
        JsonNode registered = call(api, "POST", "/api/v1/auth/register", Map.of(
            "username", username, "email", username + "@example.com", "password", PASSWORD,
            "firstName", "Selenium", "lastName", "Tester"));
        assertTrue(registered != null, "could not register the test customer");
        call(api, "POST", "/api/v1/auth/login", Map.of("usernameOrEmail", username, "password", PASSWORD));

        productSlug = null;
        for (JsonNode product : listOf(call(api, "GET", "/api/v1/products", null))) {
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
        for (JsonNode order : listOf(call(api, "GET", "/api/v1/orders", null))) {
            if (List.of("PENDING", "CONFIRMED", "PROCESSING").contains(order.path("status").asText())) {
                try {
                    call(api, "POST", "/api/v1/orders/" + order.path("id").asLong() + "/cancellation-request",
                        Map.of("reason", "Selenium test cleanup"));
                } catch (IllegalStateException e) {
                    // e.g. a test moved the shipment to SHIPPED on purpose; leave that order as it is
                }
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
        // Keep the browser on signed VNPay URLs: with fake keys the real sandbox would immediately
        // redirect to its error page and tests could no longer read the payment URL.
        String gatewayHost = System.getProperty("selenium.gatewayHost", "sandbox.vnpayment.vn");
        options.addArguments("--host-resolver-rules=MAP " + gatewayHost + " 127.0.0.1:9");
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        logIn(username, PASSWORD);
    }

    @AfterEach
    void closeBrowser() {
        if (driver != null) {
            driver.quit();
        }
    }

    // ---- browser steps -------------------------------------------------------------------

    protected void logIn(String user, String password) {
        driver.manage().deleteAllCookies();
        driver.get(baseUrl + "/login");
        fill("usernameOrEmail", user);
        fill("password", password);
        click(By.cssSelector("#loginForm button"));
        wait.until(d -> !d.getCurrentUrl().contains("/login"));
    }

    protected void openCheckoutWithOneBike() {
        driver.get(baseUrl + "/product/" + productSlug);
        // Pick the colour with the most bikes left. Tests that ship orders keep their stock for good,
        // so always taking the first colour would eventually sell it out and disable "add to cart".
        driver.findElements(By.cssSelector(".variant-option")).stream()
            .max(java.util.Comparator.comparingInt(color -> Integer.parseInt(color.getAttribute("data-stock"))))
            .ifPresent(color -> {
                assumeTrue(Integer.parseInt(color.getAttribute("data-stock")) > 0, "every colour of " + productSlug + " is sold out");
                if (!color.getAttribute("class").contains("selected")) {
                    click(color);
                }
            });
        click(By.id("addToCart"));
        wait.until(d -> {
            Object cart = js("return localStorage.getItem('kinetic_cart')");
            return cart != null && !"[]".equals(cart);
        });
        driver.get(baseUrl + "/checkout");
        wait.until(ExpectedConditions.elementToBeClickable(By.id("placeOrder")));
    }

    /** Fills the checkout form for home delivery and submits it with the given payment method. */
    protected void placeOrder(String paymentMethod) {
        openCheckoutWithOneBike();
        fill("customerName", "Selenium Tester");
        fill("phoneNumber", "0901234567");
        fill("customerEmail", username + "@example.com");
        Select province = new Select(driver.findElement(By.name("province")));
        if (province.getFirstSelectedOption().getAttribute("value").isBlank()) {
            province.selectByIndex(1);
        }
        fill("detailedAddress", "123 Đường Kiểm Thử, Phường 1");
        new Select(driver.findElement(By.name("paymentMethod"))).selectByValue(paymentMethod);
        click(By.id("placeOrder"));
    }

    protected void click(By locator) {
        click(wait.until(ExpectedConditions.presenceOfElementLocated(locator)));
    }

    /** Scrolls the element to the middle of the viewport first; the storefront uses smooth scrolling. */
    protected void click(WebElement element) {
        js("arguments[0].scrollIntoView({block:'center',behavior:'instant'})", element);
        try {
            wait.until(ExpectedConditions.elementToBeClickable(element)).click();
        } catch (ElementClickInterceptedException e) {
            Object blocker = js("const r=arguments[0].getBoundingClientRect();"
                + "const el=document.elementFromPoint(r.x+r.width/2,r.y+r.height/2);return el?el.outerHTML.slice(0,300):null", element);
            throw new AssertionError("click blocked by " + blocker + "; " + pageState("click"), e);
        }
    }

    protected void fill(String name, String value) {
        WebElement field = driver.findElement(By.name(name));
        field.clear();
        field.sendKeys(value);
    }

    protected WebElement orderRow(String orderNumber) {
        return wait.until(d -> d.findElements(By.cssSelector("#data-table tbody tr")).stream()
            .filter(row -> row.getText().contains(orderNumber)).findFirst().orElse(null));
    }

    protected Object js(String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
    }

    /** Current URL, visible messages and a screenshot under target/selenium, for failure reports. */
    protected String pageState(String label) {
        Object messages = js("return [...document.querySelectorAll('#checkoutError,#notice,#vnpayError,#productNotice')]"
            + ".filter(e=>!e.hidden&&e.textContent.trim()).map(e=>e.id+': '+e.textContent.trim()).join(' | ')");
        String screenshot = "";
        try {
            Path dir = Files.createDirectories(Path.of("target", "selenium"));
            Path file = dir.resolve(label + "-" + System.currentTimeMillis() + ".png");
            Files.write(file, ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES));
            screenshot = file.toAbsolutePath().toString();
        } catch (Exception ignored) {
            // the failure message is still useful without a screenshot
        }
        return "url=" + driver.getCurrentUrl() + ", messages=[" + messages + "], screenshot=" + screenshot;
    }

    // ---- REST helpers ----------------------------------------------------------------------

    protected static HttpClient newClient() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager()).connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** A REST client logged in as the manager given by system properties; skips the test when none is configured. */
    protected static HttpClient managerApi() throws Exception {
        String user = System.getProperty("selenium.managerUser", "");
        String password = System.getProperty("selenium.managerPassword", "");
        assumeTrue(!user.isBlank() && !password.isBlank(), "selenium.managerUser/selenium.managerPassword not set");
        HttpClient manager = newClient();
        call(manager, "POST", "/api/v1/auth/login", Map.of("usernameOrEmail", user, "password", password));
        return manager;
    }

    protected static JsonNode call(HttpClient client, String method, String path, Object body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json");
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        }
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException(method + " " + path + " -> " + response.statusCode() + ": " + response.body());
        }
        return response.body().isBlank() ? null : JSON.readTree(response.body());
    }

    protected static Iterable<JsonNode> listOf(JsonNode node) {
        if (node == null) {
            return List.of();
        }
        return node.isArray() ? node : node.path("content");
    }

    /** The customer's order with this number, read through the REST API. */
    protected static JsonNode findOrder(String orderNumber) throws Exception {
        for (JsonNode order : listOf(call(api, "GET", "/api/v1/orders", null))) {
            if (orderNumber.equals(order.path("orderNumber").asText())) {
                return order;
            }
        }
        throw new AssertionError("order " + orderNumber + " not found for " + username);
    }

    protected String placePayLaterOrder() {
        placeOrder("PAY_LATER");
        wait.until(ExpectedConditions.urlContains("/checkout/success"));
        return wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".order-number"))).getText();
    }

    protected void answerPrompt(String reason) {
        Alert prompt = wait.until(ExpectedConditions.alertIsPresent());
        prompt.sendKeys(reason);
        prompt.accept();
    }

    protected String notice() {
        return wait.until(d -> {
            WebElement notice = d.findElement(By.id("notice"));
            return notice.isDisplayed() && !notice.getText().isBlank() ? notice.getText() : null;
        });
    }
}
