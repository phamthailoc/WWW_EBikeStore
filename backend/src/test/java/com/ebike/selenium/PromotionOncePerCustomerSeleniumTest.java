package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** B7: a customer can use a discount code once; cancelling that order frees the code again. Runs over REST. */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class PromotionOncePerCustomerSeleniumTest extends StorefrontSeleniumTest {

    @Test
    void secondUseIsRejectedUntilTheFirstOrderIsCancelled() throws Exception {
        String user = System.getProperty("selenium.adminUser", "");
        String password = System.getProperty("selenium.adminPassword", "");
        assumeTrue(!user.isBlank() && !password.isBlank(), "selenium.adminUser/selenium.adminPassword not set");
        HttpClient admin = newClient();
        call(admin, "POST", "/api/v1/auth/login", Map.of("usernameOrEmail", user, "password", password));

        String code = "ONCE" + System.currentTimeMillis() % 1_000_000;
        Map<String, Object> promotion = new LinkedHashMap<>();
        promotion.put("code", code);
        promotion.put("campaignName", "Selenium một lần mỗi khách");
        promotion.put("discountType", "FIXED_AMOUNT");
        promotion.put("discountValue", 10000);
        promotion.put("usageLimit", 50);
        promotion.put("status", "LIVE");
        promotion.put("startsAt", LocalDateTime.now().minusDays(1).toString());
        promotion.put("endsAt", LocalDateTime.now().plusDays(1).toString());
        long promotionId = call(admin, "POST", "/api/v1/admin/promotions", promotion).path("id").asLong();

        try {
            long first = placeOrderWith(code);
            assertEquals(1, usageOf(admin, promotionId), "the first order uses the code once");

            IllegalStateException second = assertThrows(IllegalStateException.class, () -> placeOrderWith(code));
            assertTrue(second.getMessage().contains("400"), "second use must be refused: " + second.getMessage());
            assertEquals(1, usageOf(admin, promotionId), "a refused order must not use up the code");

            call(api, "POST", "/api/v1/orders/" + first + "/cancellation-request", Map.of("reason", "Selenium: đổi ý"));
            assertEquals(0, usageOf(admin, promotionId), "cancelling gives the use back");

            placeOrderWith(code); // allowed again; the @AfterAll cleanup cancels it
            assertEquals(1, usageOf(admin, promotionId));
        } finally {
            call(admin, "PATCH", "/api/v1/admin/promotions/" + promotionId, Map.of(
                "campaignName", "Selenium một lần mỗi khách", "discountType", "FIXED_AMOUNT", "discountValue", 10000,
                "usageLimit", 50, "status", "DISABLED"));
        }
    }

    /** Orders one bike for pickup with the code, over REST as the test customer; returns the order id. */
    private long placeOrderWith(String code) throws Exception {
        JsonNode product = call(api, "GET", "/api/v1/products/" + productSlug, null);
        JsonNode color = null;
        for (JsonNode variant : listOf(product.path("variants"))) {
            if (variant.path("stockQuantity").asInt() > 0) {
                color = variant;
                break;
            }
        }
        assumeTrue(color != null, "no colour of " + productSlug + " is in stock");
        long showroom = listOf(call(api, "GET", "/api/v1/showrooms", null)).iterator().next().path("id").asLong();
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("customerName", "Selenium Tester");
        order.put("phoneNumber", "0901234567");
        order.put("customerEmail", username + "@example.com");
        order.put("deliveryMethod", "STORE_PICKUP");
        order.put("pickupShowroomId", showroom);
        order.put("paymentMethod", "PAY_LATER");
        order.put("discountCode", code);
        order.put("items", List.of(Map.of("productId", product.path("id").asLong(), "quantity", 1,
            "productVariantId", color.path("id").asLong())));
        return call(api, "POST", "/api/v1/orders", order).path("id").asLong();
    }

    private int usageOf(HttpClient admin, long promotionId) throws Exception {
        for (JsonNode promotion : listOf(call(admin, "GET", "/api/v1/admin/promotions", null))) {
            if (promotion.path("id").asLong() == promotionId) {
                return promotion.path("usageCount").asInt();
            }
        }
        throw new AssertionError("promotion " + promotionId + " not found");
    }
}
