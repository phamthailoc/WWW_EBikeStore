package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * V19: the storefront and the customer area in English. A visitor opens the public pages, then the test customer
 * shops (cart, checkout, pay-later order) and opens every page of their account, all in EN; the test fails on any
 * Vietnamese text left in the interface. Product names, descriptions and colours, categories, vehicle/battery types, provinces and the
 * address typed at checkout are data and are skipped. The list is written to target/selenium/customer-english.txt.
 */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class CustomerEnglishSeleniumTest extends EnglishPageSeleniumTest {

    private static final String ADDRESS = "123 Đường Kiểm Thử, Phường 1";

    @Override
    protected String dataSelector() {
        // Lists of shop data: categories, vehicle and battery types (stored in Vietnamese), provinces, and the
        // language picker, which names "Tiếng Việt" on purpose.
        return "select[name=categoryId] option, select[name=vehicleType] option, select[name=batteryType] option,"
            + " #langChoice option";
    }

    @Test
    void storefrontAndCustomerAreaAreInEnglish() throws Exception {
        rememberShopData();
        dataTexts().add(ADDRESS); // typed by placeOrder(), shown again on the success page

        // ---- visitor ----
        driver.manage().deleteAllCookies();
        switchToEnglish("/");
        for (String path : List.of("/", "/products", "/product/" + productSlug, "/login", "/signup", "/support",
                "/chatbot", "/cart")) {
            visit(path);
        }
        JsonNode product = call(api, "GET", "/api/v1/products/" + productSlug, null);
        js("localStorage.setItem('kinetic_compare', JSON.stringify([arguments[0]]))", product.path("id").asLong());
        visit("/compare");

        // ---- signed-in customer ----
        logIn(username, PASSWORD);
        switchToEnglish("/customer");
        assertEquals("en", js("return document.documentElement.lang"));
        call(api, "POST", "/api/v1/favorites", Map.of("productId", product.path("id").asLong()));

        openCheckoutWithOneBike();
        scanCurrentPage("/checkout");
        visit("/cart");
        placeOrder("PAY_LATER");
        wait.until(ExpectedConditions.urlContains("/checkout/success"));
        String orderNumber = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".order-number"))).getText();
        scanCurrentPage("/checkout/success");

        long orderId = findOrder(orderNumber).path("id").asLong();
        for (String path : List.of("/customer", "/customer/orders", "/customer/orders/" + orderId, "/customer/payments",
                "/customer/notifications", "/customer/profile", "/favorites", "/product/" + productSlug)) {
            visit(path);
        }
        report("customer-english");
    }
}
