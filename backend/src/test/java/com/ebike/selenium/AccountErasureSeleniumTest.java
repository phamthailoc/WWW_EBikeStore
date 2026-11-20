package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * V6: deleting an account anonymises it (status DELETED) and keeps the orders; a customer can do it from the profile,
 * an admin only after typing the account's email. The audit trail records staff actions and sign-ins with the caller's
 * IP. Needs the manager and admin accounts:
 *
 * <pre>
 * mvn test -Dtest=AccountErasureSeleniumTest -Dselenium.baseUrl=http://localhost:8080 \
 *     -Dselenium.managerUser=... -Dselenium.managerPassword=... -Dselenium.adminUser=... -Dselenium.adminPassword=...
 * </pre>
 */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class AccountErasureSeleniumTest extends StorefrontSeleniumTest {

    /** A throwaway customer of one test, signed in over REST. */
    private record Customer(HttpClient client, long id, String username, String email) {
    }

    @Test
    void customerDeletesTheirAccountFromTheProfileAndTheOrderStays() throws Exception {
        HttpClient manager = managerApi();
        HttpClient admin = adminApi();
        Customer customer = newCustomer();
        call(customer.client(), "POST", "/api/v1/users/" + customer.id() + "/addresses", Map.of(
            "addressType", "SHIPPING", "street", "12 Đường Xóa Thử, Phường 1", "city", "Hà Nội", "country", "VN", "isDefault", true));
        long productId = call(api, "GET", "/api/v1/products/" + productSlug, null).path("id").asLong();
        call(customer.client(), "POST", "/api/v1/favorites", Map.of("productId", productId));

        // A delivered order, which also lets the customer review the bike.
        long orderId = placePickupOrder(customer);
        for (String status : List.of("CONFIRMED", "PROCESSING", "READY_FOR_PICKUP", "DELIVERED")) {
            if (status.equals("DELIVERED")) enterUnits(manager, orderId);
            call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", status));
        }
        int reviewsBefore = reviewCount();
        call(customer.client(), "POST", "/api/v1/products/" + productSlug + "/reviews",
            Map.of("rating", 5, "comment", "Selenium: đánh giá sẽ bị xóa cùng tài khoản"));
        assertEquals(reviewsBefore + 1, reviewCount());
        JsonNode orderBefore = call(manager, "GET", "/api/v1/orders/" + orderId, null);

        logIn(customer.username(), PASSWORD);
        driver.get(baseUrl + "/customer/profile");
        WebElement form = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("deleteForm")));
        form.findElement(By.name("password")).sendKeys(PASSWORD);
        form.findElement(By.name("confirmEmail")).sendKeys("nguoi.khac@example.com");
        click(form.findElement(By.cssSelector("button[type=submit]")));
        wait.until(ExpectedConditions.alertIsPresent()).accept();
        assertTrue(notice().contains("Email xác nhận không khớp"), "a wrong email is refused");

        WebElement email = form.findElement(By.name("confirmEmail"));
        email.clear();
        email.sendKeys(customer.email());
        click(form.findElement(By.cssSelector("button[type=submit]")));
        wait.until(ExpectedConditions.alertIsPresent()).accept();
        wait.until(d -> d.getCurrentUrl().equals(baseUrl + "/"));

        // Signed out everywhere, cannot sign in again.
        assertThrows(IllegalStateException.class, () -> call(customer.client(), "GET", "/api/v1/auth/profile", null));
        assertThrows(IllegalStateException.class, () -> call(newClient(), "POST", "/api/v1/auth/login",
            Map.of("usernameOrEmail", customer.username(), "password", PASSWORD)));

        // The order is kept as it was; the review is gone and the rating recomputed.
        JsonNode orderAfter = call(manager, "GET", "/api/v1/orders/" + orderId, null);
        assertEquals(orderBefore.path("orderNumber").asText(), orderAfter.path("orderNumber").asText());
        assertEquals(orderBefore.path("customerEmail").asText(), orderAfter.path("customerEmail").asText());
        assertEquals(orderBefore.path("shipment").path("recipientName").asText(), orderAfter.path("shipment").path("recipientName").asText());
        assertEquals("DELIVERED", orderAfter.path("status").asText());
        assertEquals(reviewsBefore, reviewCount(), "the deleted account's review is removed");

        // Hidden from the Admin and Manager lists; the audit trail names the account by number only.
        assertFalse(mentions(call(admin, "GET", "/api/v1/admin/accounts", null), customer.email()), "not in the admin list");
        assertFalse(mentions(call(manager, "GET", "/api/v1/manager/customers", null), customer.email()), "not in the manager list");
        assertTrue(hasAudit(admin, "khách tự xóa tài khoản", "tài khoản #" + customer.id()), "self-deletion is in the audit trail");

        // The same email and username can register again.
        HttpClient again = newClient();
        call(again, "POST", "/api/v1/auth/register", Map.of("username", customer.username(), "email", customer.email(),
            "password", PASSWORD, "firstName", "Selenium", "lastName", "Again"));
        call(again, "POST", "/api/v1/auth/login", Map.of("usernameOrEmail", customer.username(), "password", PASSWORD));
        JsonNode fresh = call(again, "GET", "/api/v1/auth/profile", null);
        assertTrue(fresh.path("userId").asLong() != customer.id(), "a new account, not the old one revived");
        assertTrue(listEmpty(call(again, "GET", "/api/v1/orders", null)), "the new account does not inherit the old orders");
        assertTrue(listEmpty(call(again, "GET", "/api/v1/favorites", null)), "nor the old favourites");
    }

    @Test
    void customerCannotDeleteWithAnOrderOnItsWayOrTheWrongPassword() throws Exception {
        Customer customer = newCustomer();
        long orderId = placePickupOrder(customer);
        IllegalStateException wrongPassword = assertThrows(IllegalStateException.class, () -> deleteOwn(customer, "Sai@123", customer.email()));
        assertTrue(wrongPassword.getMessage().contains("400") && wrongPassword.getMessage().contains("Mật khẩu không đúng"), wrongPassword.getMessage());
        IllegalStateException open = assertThrows(IllegalStateException.class, () -> deleteOwn(customer, PASSWORD, customer.email()));
        assertTrue(open.getMessage().contains("đơn hàng chưa hoàn tất"), open.getMessage());

        // Once the order is cancelled the account can go.
        call(customer.client(), "POST", "/api/v1/orders/" + orderId + "/cancellation-request", Map.of("reason", "Selenium"));
        deleteOwn(customer, PASSWORD, customer.email());
        assertThrows(IllegalStateException.class, () -> call(customer.client(), "GET", "/api/v1/auth/profile", null));
    }

    @Test
    void staffCannotUseTheCustomerDeletion() throws Exception {
        HttpClient manager = managerApi();
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> call(manager, "DELETE", "/api/v1/auth/account",
            Map.of("password", System.getProperty("selenium.managerPassword"), "confirmEmail", "x@example.com")));
        assertTrue(refused.getMessage().contains("403"), refused.getMessage());
    }

    @Test
    void adminErasesACustomerWithOrdersOnlyWithTheRightEmail() throws Exception {
        HttpClient manager = managerApi();
        HttpClient admin = adminApi();
        Customer customer = newCustomer();
        long orderId = placePickupOrder(customer);

        assertThrows(IllegalStateException.class, () -> call(admin, "DELETE", "/api/v1/admin/accounts/" + customer.id(), null),
            "the email is required");
        IllegalStateException wrong = assertThrows(IllegalStateException.class, () -> adminDelete(admin, customer.id(), "sai@example.com"));
        assertTrue(wrong.getMessage().contains("400"), wrong.getMessage());
        assertTrue(mentions(call(admin, "GET", "/api/v1/admin/accounts", null), customer.email()), "still there after a wrong email");

        adminDelete(admin, customer.id(), customer.email().toUpperCase());
        assertFalse(mentions(call(admin, "GET", "/api/v1/admin/accounts", null), customer.email()));
        assertEquals(orderId, call(manager, "GET", "/api/v1/orders/" + orderId, null).path("id").asLong(), "the order is kept");
        assertTrue(hasAudit(admin, "xóa tài khoản (ẩn danh hóa)", "tài khoản #" + customer.id()));

        // A deleted account is final.
        assertThrows(IllegalStateException.class, () -> call(admin, "PATCH", "/api/v1/admin/accounts/" + customer.id() + "/status", Map.of("status", "ACTIVE")));
        assertThrows(IllegalStateException.class, () -> call(admin, "PATCH", "/api/v1/admin/accounts/" + customer.id() + "/role", Map.of("role", "MANAGER")));
        assertThrows(IllegalStateException.class, () -> adminDelete(admin, customer.id(), customer.email()));
        // The shop can still close the order of an erased account, and that adds no notification to it.
        call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", "CANCELLED", "reason", "Selenium cleanup"));
        assertEquals("CANCELLED", call(manager, "GET", "/api/v1/orders/" + orderId, null).path("status").asText());
        // (no API reads another account's notifications; checked in the database: none for DELETED accounts)
    }

    @Test
    void adminDeletesFromTheAccountsPageAfterTypingTheEmail() throws Exception {
        adminApi();
        Customer customer = newCustomer();
        logIn(System.getProperty("selenium.adminUser"), System.getProperty("selenium.adminPassword"));
        driver.get(baseUrl + "/admin/accounts");
        WebElement row = orderRow(customer.email());
        click(row.findElement(By.cssSelector("[data-action='delete-account']")));
        WebElement dialog = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("dialog[open]")));
        assertTrue(dialog.getText().contains(customer.email()) && dialog.getText().contains("không khôi phục được"), dialog.getText());
        dialog.findElement(By.name("confirmEmail")).sendKeys(customer.email());
        click(dialog.findElement(By.cssSelector("[type=submit]")));
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector("dialog[open]")));
        wait.until(d -> d.findElements(By.cssSelector("#data-table tbody tr")).stream().noneMatch(r -> r.getText().contains(customer.email())));
    }

    @Test
    void auditTrailRecordsStaffSignInsAndManagerActionsWithTheAddress() throws Exception {
        HttpClient admin = adminApi();
        String managerUser = System.getProperty("selenium.managerUser");
        assumeTrue(managerUser != null && !managerUser.isBlank(), "selenium.managerUser not set");
        assertThrows(IllegalStateException.class, () -> call(newClient(), "POST", "/api/v1/auth/login",
            Map.of("usernameOrEmail", managerUser, "password", "Sai-mat-khau-1")));
        HttpClient manager = managerApi();
        Customer customer = newCustomer();
        long orderId = placePickupOrder(customer);
        String orderNumber = call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", "CONFIRMED")).path("orderNumber").asText();

        JsonNode failed = audit(admin, "đăng nhập thất bại", "sai mật khẩu", managerUser);
        assertTrue(failed != null, "a refused staff sign-in stays on record");
        String ip = failed.path("ipAddress").asText();
        assertTrue(!ip.isBlank() && !"system".equals(ip), "the caller's address is recorded: " + ip);
        assertTrue(audit(admin, "đăng nhập", managerUser, managerUser) != null, "a staff sign-in is recorded");
        JsonNode change = audit(admin, "đổi trạng thái đơn", orderNumber, managerUser);
        assertTrue(change != null && change.path("target").asText().contains("CONFIRMED"), "manager order changes are recorded: " + change);

        // Customers' own sign-ins do not flood the trail.
        assertTrue(audit(admin, "đăng nhập", customer.username(), customer.username()) == null);

        logIn(System.getProperty("selenium.adminUser"), System.getProperty("selenium.adminPassword"));
        driver.get(baseUrl + "/admin/audit-logs");
        WebElement table = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#data-table thead")));
        assertTrue(table.getText().contains("Địa chỉ IP".toUpperCase()) || table.getText().contains("Địa chỉ IP"), table.getText());
        call(customer.client(), "POST", "/api/v1/orders/" + orderId + "/cancellation-request", Map.of("reason", "Selenium cleanup"));
    }

    // ---- steps ---------------------------------------------------------------------------

    private static HttpClient adminApi() throws Exception {
        String user = System.getProperty("selenium.adminUser", "");
        String password = System.getProperty("selenium.adminPassword", "");
        assumeTrue(!user.isBlank() && !password.isBlank(), "selenium.adminUser/selenium.adminPassword not set");
        HttpClient admin = newClient();
        call(admin, "POST", "/api/v1/auth/login", Map.of("usernameOrEmail", user, "password", password));
        return admin;
    }

    private static Customer newCustomer() throws Exception {
        HttpClient client = newClient();
        String name = "seleniumdel" + System.nanoTime();
        call(client, "POST", "/api/v1/auth/register", Map.of("username", name, "email", name + "@example.com",
            "password", PASSWORD, "firstName", "Selenium", "lastName", "Xoa"));
        call(client, "POST", "/api/v1/auth/login", Map.of("usernameOrEmail", name, "password", PASSWORD));
        long id = call(client, "GET", "/api/v1/auth/profile", null).path("userId").asLong();
        return new Customer(client, id, name, name + "@example.com");
    }

    private static long placePickupOrder(Customer customer) throws Exception {
        JsonNode product = call(api, "GET", "/api/v1/products/" + productSlug, null);
        JsonNode color = null;
        for (JsonNode variant : listOf(product.path("variants"))) {
            if (color == null || variant.path("stockQuantity").asInt() > color.path("stockQuantity").asInt()) {
                color = variant;
            }
        }
        assumeTrue(color == null || color.path("stockQuantity").asInt() > 0, "every colour of " + productSlug + " is sold out");
        long showroom = listOf(call(api, "GET", "/api/v1/showrooms", null)).iterator().next().path("id").asLong();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productId", product.path("id").asLong());
        item.put("quantity", 1);
        item.put("productVariantId", color == null ? null : color.path("id").asLong());
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("customerName", "Selenium Xoa");
        order.put("phoneNumber", "0901234567");
        order.put("customerEmail", customer.email());
        order.put("deliveryMethod", "STORE_PICKUP");
        order.put("pickupShowroomId", showroom);
        order.put("paymentMethod", "PAY_LATER");
        order.put("items", List.of(item));
        return call(customer.client(), "POST", "/api/v1/orders", order).path("id").asLong();
    }

    private static void deleteOwn(Customer customer, String password, String email) throws Exception {
        call(customer.client(), "DELETE", "/api/v1/auth/account", Map.of("password", password, "confirmEmail", email));
    }

    private static void adminDelete(HttpClient admin, long id, String email) throws Exception {
        call(admin, "DELETE", "/api/v1/admin/accounts/" + id + "?confirmEmail=" + URLEncoder.encode(email, StandardCharsets.UTF_8), null);
    }

    private static int reviewCount() throws Exception {
        return call(api, "GET", "/api/v1/products/" + productSlug, null).path("reviewCount").asInt();
    }

    private static boolean mentions(JsonNode list, String text) {
        for (JsonNode row : listOf(list)) {
            if (row.toString().contains(text)) {
                return true;
            }
        }
        return false;
    }

    private static boolean listEmpty(JsonNode list) {
        return !listOf(list).iterator().hasNext();
    }

    private static boolean hasAudit(HttpClient admin, String action, String target) throws Exception {
        for (JsonNode log : listOf(call(admin, "GET", "/api/v1/admin/audit-logs", null))) {
            if (action.equals(log.path("action").asText()) && target.equals(log.path("target").asText())) {
                return true;
            }
        }
        return false;
    }

    /** The newest audit entry with this action whose target contains the text and whose actor is the given user. */
    private static JsonNode audit(HttpClient admin, String action, String targetPart, String actor) throws Exception {
        for (JsonNode log : listOf(call(admin, "GET", "/api/v1/admin/audit-logs", null))) {
            if (action.equals(log.path("action").asText()) && log.path("target").asText().contains(targetPart)
                && actor.equals(log.path("actor").asText())) {
                return log;
            }
        }
        return null;
    }
}
