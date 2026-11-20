package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;

/**
 * V19: the Manager and Admin area in English. Signs in as each staff account, switches to EN, opens every page and
 * the admin/stock/image dialogs, and fails on any Vietnamese text left in the interface (data typed by people, such
 * as product or customer names, is ignored). The list is written to target/selenium/staff-english-*.txt.
 */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class StaffEnglishSeleniumTest extends EnglishPageSeleniumTest {

    private String signedInAs;

    @AfterEach
    void putTheAccountBackInVietnamese() {
        // /lang/{code} also stores the choice on the account; other tests expect the staff accounts in Vietnamese.
        if (signedInAs != null) {
            driver.get(baseUrl + "/lang/vi?back=/");
        }
    }

    private void signInInEnglish(String userProperty, String passwordProperty, String home) {
        String user = System.getProperty(userProperty, "");
        String password = System.getProperty(passwordProperty, "");
        assumeTrue(!user.isBlank() && !password.isBlank(), userProperty + "/" + passwordProperty + " not set");
        logIn(user, password);
        signedInAs = user;
        switchToEnglish(home);
    }

    private void assertMenuAndTitleInEnglish(String title, List<String> menu) {
        assertEquals(title, driver.findElement(By.cssSelector("main h1")).getText().trim());
        String nav = driver.findElement(By.cssSelector(".work-sidebar")).getText();
        for (String link : menu) {
            assertTrue(nav.contains(link), "menu has " + link + ": " + nav);
        }
    }

    @Test
    void managerAreaIsInEnglish() throws Exception {
        HttpClient manager = managerApi();
        rememberShopData(); // colour names in the stock and image dialogs
        signInInEnglish("selenium.managerUser", "selenium.managerPassword", "/manager");
        visit("/manager");
        assertMenuAndTitleInEnglish("Store overview",
            List.of("Overview", "Orders", "Payments", "Customers", "Support", "Inventory", "Products", "Reports"));

        for (String path : List.of("/manager/orders", "/manager/payments", "/manager/support", "/manager/customers",
                "/manager/inventory", "/manager/products", "/manager/reports")) {
            visit(path);
        }
        Long orderId = null;
        for (JsonNode order : listOf(call(manager, "GET", "/api/v1/orders", null))) {
            orderId = order.path("id").asLong();
            break;
        }
        if (orderId != null) {
            visit("/manager/orders/" + orderId);
        }

        visit("/manager/inventory");
        scanDialog("/manager/inventory stock dialog", By.cssSelector("#data-table [data-action='stock'][data-slug]"));
        scanDialog("/manager/inventory image dialog", By.cssSelector("#data-table [data-action='images']"));
        report("staff-english-manager");
    }

    @Test
    void adminAreaIsInEnglish() throws Exception {
        signInInEnglish("selenium.adminUser", "selenium.adminPassword", "/admin");
        visit("/admin");
        assertMenuAndTitleInEnglish("Administration dashboard",
            List.of("Overview", "Products", "Inventory", "Pricing & promotions", "Accounts", "Roles", "Log", "Users"));

        for (String path : List.of("/admin/products", "/admin/inventory", "/admin/pricing", "/admin/roles",
                "/admin/audit-logs", "/admin/users")) {
            visit(path);
        }

        visit("/admin/accounts");
        assertTrue(driver.findElement(By.cssSelector(".theme-toggle")).getAttribute("title").startsWith("Appearance"),
            "theme button tooltip is in English");
        scanDialog("/admin/accounts create dialog", By.id("create-account"));
        scanDialog("/admin/accounts role dialog", By.cssSelector("#data-table [data-action='role']"));
        scanDialog("/admin/accounts delete dialog", By.cssSelector("#data-table [data-action='delete-account']"));

        visit("/admin/pricing");
        scanDialog("/admin/pricing new code dialog", By.id("addPromotion"));
        scanDialog("/admin/pricing edit code dialog", By.cssSelector("[data-action='promotion']"));
        report("staff-english-admin");
    }
}
