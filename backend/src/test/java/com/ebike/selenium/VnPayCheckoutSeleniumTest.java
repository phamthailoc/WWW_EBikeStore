package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ebike.util.order.VnPayUtil;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

/**
 * End-to-end checks of the VNPay checkout flow in a real Chrome browser.
 *
 * <p>Needs the application running with VNPay keys set (fake values are fine, the gateway page
 * itself is never used):
 *
 * <pre>
 * mvn test -Dtest=VnPayCheckoutSeleniumTest \
 *     -Dselenium.baseUrl=http://localhost:8080 \
 *     -Dselenium.vnpayHashSecret=&lt;same value as VNPAY_HASH_SECRET&gt;
 * </pre>
 */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class VnPayCheckoutSeleniumTest extends StorefrontSeleniumTest {

    private static String hashSecret;

    @BeforeAll
    static void requireHashSecret() {
        hashSecret = System.getProperty("selenium.vnpayHashSecret", "");
        assumeTrue(!hashSecret.isBlank(), "selenium.vnpayHashSecret is required to sign fake VNPay results");
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
        assertTrue(row.getText().contains("Đã thanh toán"), "the order list shows the payment status in Vietnamese: " + row.getText());
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

    // ---- steps ---------------------------------------------------------------------------

    private Map<String, String> placeVnPayOrder() {
        placeOrder("VNPAY");
        return waitForGateway();
    }

    private Map<String, String> waitForGateway() {
        try {
            // Our own /payment/return URL also carries vnp_TxnRef, so only accept addresses outside the app.
            wait.until(d -> !d.getCurrentUrl().startsWith(baseUrl) && d.getCurrentUrl().contains("vnp_TxnRef="));
        } catch (TimeoutException e) {
            throw new AssertionError("never reached the VNPay gateway; " + pageState("gateway"), e);
        }
        return query(driver.getCurrentUrl());
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
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            int eq = pair.indexOf('=');
            params.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
        }
        return params;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
