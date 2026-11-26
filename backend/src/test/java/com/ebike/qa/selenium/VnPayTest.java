package com.ebike.qa.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.qa.selenium.pages.CheckoutPage;
import com.ebike.qa.selenium.pages.Page;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;

@EnabledIfSystemProperty(named = "qa.baseUrl", matches = ".+")
class VnPayTest extends QaBase {
    private final Api api = new Api();

    /** A guest VNPAY order made through the API plus the payment URL VNPay would show. */
    private record Attempt(JsonNode order, String payUrl) { }

    private Attempt attempt() {
        JsonNode order = api.placeOrder("Khach VNPay", QaConfig.uniqueUsername() + "@example.com", Shop.buySlug(), 1, "VNPAY");
        Api.Reply r = api.post("/api/v1/payments/vnpay/create", Map.of("orderId", order.get("id").asLong()));
        assertEquals(200, r.status(), r.body());
        return new Attempt(order, r.json().get("paymentUrl").asText());
    }

    private Map<String, String> returnParams(Attempt a, String responseCode, String status) {
        Map<String, String> p = new HashMap<>();
        p.put("vnp_Amount", Vnpay.param(a.payUrl(), "vnp_Amount"));
        p.put("vnp_BankCode", "NCB");
        p.put("vnp_CardType", "ATM");
        p.put("vnp_OrderInfo", "Thanh toan don hang");
        p.put("vnp_PayDate", "20261126100000");
        p.put("vnp_ResponseCode", responseCode);
        p.put("vnp_TmnCode", QaConfig.get("vnpay.tmnCode"));
        p.put("vnp_TransactionNo", "14" + System.currentTimeMillis() % 1_000_000);
        p.put("vnp_TransactionStatus", status);
        p.put("vnp_TxnRef", Vnpay.param(a.payUrl(), "vnp_TxnRef"));
        return p;
    }

    @Test
    void choosingVnpayRedirectsToTheGatewayWithASignedUrl() {
        fillCart(Shop.buySlug(), 1);
        CheckoutPage c = new CheckoutPage(ui).open().waitQuote();
        c.contact("Nguyễn Văn Cổng", "0901234567", QaConfig.uniqueUsername() + "@example.com");
        c.home("Hồ Chí Minh", "12 Nguyễn Trãi, Phường Bến Thành");
        ui.until(d -> c.quoteHas("Phí giao xe", "200.000"));
        long total = c.quoteMoney("Tổng thanh toán");
        ui.selectValue(CheckoutPage.PAYMENT, "VNPAY");
        // the sandbox answers its own redirect within milliseconds, so keep the payment URL the shop received before leaving the page
        ui.js("const f=window.fetch;window.fetch=async(...a)=>{const r=await f(...a);if(String(a[0]).includes('vnpay/create')){const j=await r.clone().json();localStorage.setItem('qa_pay',j.paymentUrl);}return r;};");
        c.place();
        ui.until(30, d -> !d.getCurrentUrl().startsWith(QaConfig.baseUrl()));
        assertTrue(ui.url().contains("vnpayment.vn"), "the buyer is sent to the VNPay sandbox: " + ui.url());
        ui.open("/");
        String url = String.valueOf(ui.js("return localStorage.getItem('qa_pay');"));
        assertEquals("QATEST01", Vnpay.param(url, "vnp_TmnCode"));
        assertEquals(String.valueOf(total * 100), Vnpay.param(url, "vnp_Amount"));
        assertTrue(Vnpay.param(url, "vnp_TxnRef").startsWith("EBIKE"));
        String hash = Vnpay.param(url, "vnp_SecureHash");
        assertEquals(128, hash.length());
        String raw = URI.create(url).getRawQuery();
        String signed = raw.substring(0, raw.indexOf("&vnp_SecureHash="));
        assertEquals(Vnpay.hmac(signed), hash, "the gateway URL must be signed with the configured secret");
    }

    @Test
    void correctlySignedReturnShowsPaymentSuccess() {
        Attempt a = attempt();
        ui.open("/payment/return?" + Vnpay.signedQuery(returnParams(a, "00", "00")));
        ui.until(20, d -> ui.exists(By.cssSelector(".order-success h2")));
        assertEquals("Thanh toán thành công", ui.text(By.cssSelector(".order-success h2")));
        assertEquals(a.order().get("orderNumber").asText(), ui.text(By.cssSelector(".order-number")));
        long expected = Math.round(a.order().get("totalAmount").asDouble());
        assertEquals(expected, Page.money(ui.text(By.xpath("//p[span[.='Số tiền']]/strong"))));
        assertTrue(ui.text(By.cssSelector(".order-success-details")).contains("PAID"));
        assertTrue(ui.shown(By.cssSelector(".success-actions")));
    }

    @Test
    void tamperedReturnIsRejected() {
        Attempt a = attempt();
        Map<String, String> p = returnParams(a, "00", "00");
        String query = Vnpay.signedQuery(p);
        ui.open("/payment/return?" + query.replace("vnp_Amount=" + p.get("vnp_Amount"), "vnp_Amount=100"));
        ui.until(20, d -> ui.exists(By.cssSelector(".order-success h2")));
        assertEquals("Không xác minh được kết quả thanh toán", ui.text(By.cssSelector(".order-success h2")));
        assertTrue(ui.text(By.cssSelector(".order-success")).contains("không hợp lệ"));
        // the order must still be unpaid
        ui.open("/payment/return?" + Vnpay.signedQuery(returnParams(a, "00", "00")));
        ui.until(20, d -> ui.exists(By.cssSelector(".order-success h2")));
        assertEquals("Thanh toán thành công", ui.text(By.cssSelector(".order-success h2")), "the genuine return afterwards still works: the forged one changed nothing");
    }

    @Test
    void unsignedReturnIsRejected() {
        Attempt a = attempt();
        ui.open("/payment/return?" + Vnpay.query(returnParams(a, "00", "00")));
        ui.until(20, d -> ui.exists(By.cssSelector(".order-success h2")));
        assertEquals("Không xác minh được kết quả thanh toán", ui.text(By.cssSelector(".order-success h2")));
    }

    @Test
    void cancelledAtGatewayShowsFailureReasonAndRetry() {
        Attempt a = attempt();
        ui.open("/payment/return?" + Vnpay.signedQuery(returnParams(a, "24", "02")));
        ui.until(20, d -> ui.exists(By.cssSelector(".order-success h2")));
        assertEquals("Thanh toán chưa thành công", ui.text(By.cssSelector(".order-success h2")));
        assertTrue(ui.text(By.cssSelector(".order-success")).contains("Bạn đã hủy giao dịch."));
        assertTrue(ui.exists(By.id("retryVnPay")), "a valid failed payment offers a retry");
    }

    @Test
    void returnPageWithoutParametersExplainsItself() {
        ui.open("/payment/return");
        ui.until(d -> ui.exists(By.cssSelector("#flow-extra .empty h2")));
        assertEquals("Không có thông tin thanh toán", ui.text(By.cssSelector("#flow-extra .empty h2")));
    }
}
