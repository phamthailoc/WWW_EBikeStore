package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Who may open which page and call which API, for every role of the shop: a visitor, a customer, a manager, an admin, and the roles that exist but hold
 * no permission at all (OPERATOR, SUPPORT, MAINTENANCE). No browser: plain HTTP against the running app, the answer of each request is compared with the table.
 * The zero-permission accounts are created through the admin API and erased again at the end.
 */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class RoleAccessMatrixSeleniumTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PASSWORD = "Matrix@12345";
    // the order of the columns of every row below
    private static final List<String> ROLES = List.of("visitor", "customer", "manager", "admin", "operator", "support", "maintenance");

    private static String baseUrl;
    private static final Map<String, HttpClient> CLIENTS = new LinkedHashMap<>();
    private static final List<Long> CREATED = new ArrayList<>();
    private static HttpClient admin;
    private static String customerName;
    private static long pricingRuleId;
    private static String pricingRuleAmount;
    private static String pricingRuleStatus;

    private static HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager()).followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build();
    }

    private static HttpResponse<String> send(HttpClient client, String method, String path, Object body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(30)).header("Accept", "*/*");
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpClient loggedIn(String user, String password) throws Exception {
        HttpClient client = client();
        HttpResponse<String> response = send(client, "POST", "/api/v1/auth/login", Map.of("usernameOrEmail", user, "password", password));
        assertEquals(200, response.statusCode(), "login of " + user + ": " + response.body());
        return client;
    }

    @BeforeAll
    static void prepareEveryRole() throws Exception {
        String url = System.getProperty("selenium.baseUrl", "");
        assumeTrue(url.matches("https?://.+"), "selenium.baseUrl not set");
        String managerUser = System.getProperty("selenium.managerUser", "");
        String adminUser = System.getProperty("selenium.adminUser", "");
        assumeTrue(!managerUser.isBlank() && !adminUser.isBlank(), "manager and admin accounts are not configured");
        baseUrl = url.replaceAll("/+$", "");

        CLIENTS.put("visitor", client());
        String stamp = String.valueOf(System.currentTimeMillis());
        String customer = "matrixcustomer" + stamp;
        customerName = customer;
        HttpClient customerClient = client();
        assertEquals(201, send(customerClient, "POST", "/api/v1/auth/register", Map.of("username", customer, "email", customer + "@example.com",
            "password", PASSWORD, "firstName", "Matrix", "lastName", "Customer")).statusCode());
        assertEquals(200, send(customerClient, "POST", "/api/v1/auth/login", Map.of("usernameOrEmail", customer, "password", PASSWORD)).statusCode());
        CLIENTS.put("customer", customerClient);
        CLIENTS.put("manager", loggedIn(managerUser, System.getProperty("selenium.managerPassword")));
        admin = loggedIn(adminUser, System.getProperty("selenium.adminPassword"));
        CLIENTS.put("admin", admin);
        for (String role : List.of("OPERATOR", "SUPPORT", "MAINTENANCE")) {
            String name = "matrix" + role.toLowerCase() + stamp;
            HttpResponse<String> created = send(admin, "POST", "/api/v1/admin/accounts", Map.of("name", "Matrix " + role, "username", name,
                "email", name + "@example.com", "password", PASSWORD, "role", role, "status", "ACTIVE", "verified", true));
            assertEquals(201, created.statusCode(), created.body());
            CREATED.add(JSON.readTree(created.body()).path("id").asLong());
            CLIENTS.put(role.toLowerCase(), loggedIn(name, PASSWORD));
        }
        for (JsonNode rule : JSON.readTree(send(admin, "GET", "/api/v1/admin/pricing-rules", null).body())) {
            pricingRuleId = rule.path("id").asLong();
            pricingRuleAmount = rule.path("amount").asText();
            pricingRuleStatus = rule.path("status").asText();
            break;
        }
    }

    @AfterAll
    static void eraseTheAccounts() throws Exception {
        if (admin == null) {
            return;
        }
        for (Long id : CREATED) {
            if (id > 0) {
                send(admin, "DELETE", "/api/v1/admin/accounts/" + id, null);
            }
        }
        HttpClient customer = CLIENTS.get("customer");
        if (customer != null && customerName != null) {
            // the customer erases their own account: the throwaway rows do not pile up in the shared database
            send(customer, "DELETE", "/api/v1/auth/account", Map.of("password", PASSWORD, "confirmEmail", customerName + "@example.com"));
        }
    }

    /** One row: the status each role gets, in the order of {@link #ROLES}. */
    private record Row(String method, String path, int... statuses) {
    }

    private static List<Row> rows() {
        return List.of(
            // pages: a visitor is sent to the sign-in page (302), a signed-in account without the right gets 403
            new Row("GET", "/customer", 302, 200, 200, 200, 403, 403, 403),
            new Row("GET", "/customer/orders", 302, 200, 200, 200, 403, 403, 403),
            new Row("GET", "/customer/warranty", 302, 200, 302, 200, 403, 403, 403),
            new Row("GET", "/customer/test-rides", 302, 200, 302, 302, 403, 403, 403),
            new Row("GET", "/manager", 302, 403, 200, 200, 403, 403, 403),
            new Row("GET", "/manager/orders", 302, 403, 200, 200, 403, 403, 403),
            new Row("GET", "/manager/payments", 302, 403, 200, 200, 403, 403, 403),
            new Row("GET", "/manager/customers", 302, 403, 200, 200, 403, 403, 403),
            new Row("GET", "/manager/support", 302, 403, 200, 200, 403, 403, 403),
            new Row("GET", "/manager/warranty", 302, 403, 200, 200, 403, 403, 403),
            new Row("GET", "/manager/test-rides", 302, 403, 200, 200, 403, 403, 403),
            new Row("GET", "/manager/installments", 302, 403, 200, 200, 403, 403, 403),
            new Row("GET", "/manager/stock-receipts", 302, 403, 200, 200, 403, 403, 403),
            new Row("GET", "/manager/reports", 302, 403, 200, 200, 403, 403, 403),
            new Row("GET", "/admin", 302, 403, 403, 200, 403, 403, 403),
            new Row("GET", "/admin/pricing", 302, 403, 403, 200, 403, 403, 403),
            new Row("GET", "/admin/accounts", 302, 403, 403, 200, 403, 403, 403),
            new Row("GET", "/admin/roles", 302, 403, 403, 200, 403, 403, 403),
            new Row("GET", "/admin/audit-logs", 302, 403, 403, 200, 403, 403, 403),
            new Row("GET", "/admin/users", 302, 403, 403, 200, 403, 403, 403),
            new Row("GET", "/admin/warranty", 302, 403, 200, 200, 403, 403, 403),
            new Row("GET", "/admin/test-rides", 302, 403, 200, 200, 403, 403, 403),
            new Row("GET", "/admin/installments", 302, 403, 200, 200, 403, 403, 403),
            new Row("GET", "/admin/stock-receipts", 302, 403, 200, 200, 403, 403, 403),
            // the customer's own APIs
            new Row("GET", "/api/v1/orders", 401, 200, 200, 200, 403, 403, 403),
            new Row("GET", "/api/v1/warranty/units", 401, 200, 403, 200, 403, 403, 403),
            new Row("GET", "/api/v1/test-rides", 401, 200, 403, 403, 403, 403, 403),
            new Row("GET", "/api/v1/installments/mine", 401, 200, 403, 200, 403, 403, 403),
            new Row("GET", "/api/v1/notifications", 401, 200, 200, 200, 200, 200, 200),
            // the staff APIs
            new Row("GET", "/api/v1/manager/dashboard", 401, 403, 200, 200, 403, 403, 403),
            new Row("GET", "/api/v1/manager/customers", 401, 403, 200, 200, 403, 403, 403),
            new Row("GET", "/api/v1/manager/test-rides", 401, 403, 200, 200, 403, 403, 403),
            new Row("GET", "/api/v1/manager/installments", 401, 403, 200, 200, 403, 403, 403),
            new Row("GET", "/api/v1/manager/warranty-claims", 401, 403, 200, 200, 403, 403, 403),
            new Row("GET", "/api/v1/manager/stock-receipts", 401, 403, 200, 200, 403, 403, 403),
            new Row("GET", "/api/v1/manager/revenue-report", 401, 403, 200, 200, 403, 403, 403),
            new Row("GET", "/api/v1/manager/orders/export", 401, 403, 200, 200, 403, 403, 403),
            // the admin APIs
            new Row("GET", "/api/v1/admin/overview", 401, 403, 403, 200, 403, 403, 403),
            new Row("GET", "/api/v1/admin/accounts", 401, 403, 403, 200, 403, 403, 403),
            new Row("GET", "/api/v1/admin/roles", 401, 403, 403, 200, 403, 403, 403),
            new Row("GET", "/api/v1/admin/pricing-rules", 401, 403, 403, 200, 403, 403, 403),
            new Row("GET", "/api/v1/admin/audit-logs", 401, 403, 403, 200, 403, 403, 403));
    }

    @Test
    void everyRoleGetsTheAnswerOfTheTable() throws Exception {
        List<String> wrong = new ArrayList<>();
        for (Row row : rows()) {
            for (int column = 0; column < ROLES.size(); column++) {
                String role = ROLES.get(column);
                int status = send(CLIENTS.get(role), row.method(), row.path(), null).statusCode();
                if (status != row.statuses()[column]) {
                    wrong.add(role + " " + row.method() + " " + row.path() + ": got " + status + ", expected " + row.statuses()[column]);
                }
            }
        }
        assertEquals(List.of(), wrong, "answers that differ from the table of roles");
    }

    @Test
    void nobodyButAnAdminCanChangeAPriceOrAPromotion() throws Exception {
        assumeTrue(pricingRuleId > 0, "no pricing rule to try");
        // the same value again: harmless if it were ever allowed
        Map<String, Object> same = Map.of("amount", new java.math.BigDecimal(pricingRuleAmount), "status", pricingRuleStatus);
        for (String role : List.of("visitor", "customer", "manager", "operator", "support", "maintenance")) {
            int status = send(CLIENTS.get(role), "PATCH", "/api/v1/admin/pricing-rules/" + pricingRuleId, same).statusCode();
            assertEquals(role.equals("visitor") ? 401 : 403, status, role + " must not change a price");
            assertEquals(role.equals("visitor") ? 401 : 403, send(CLIENTS.get(role), "POST", "/api/v1/admin/promotions", Map.of("code", "X")).statusCode(),
                role + " must not create a promotion");
            assertEquals(role.equals("visitor") ? 401 : 403, send(CLIENTS.get(role), "POST", "/api/v1/admin/accounts", Map.of("username", "x")).statusCode(),
                role + " must not create an account");
        }
    }

    @Test
    void aCustomerCannotReadAnotherCustomersOrderOrClaim() throws Exception {
        // an order id that is not the customer's (the first order of the shop, read as the manager)
        JsonNode orders = JSON.readTree(send(CLIENTS.get("manager"), "GET", "/api/v1/orders", null).body());
        assumeTrue(orders.isArray() && orders.size() > 0, "the shop has no order yet");
        long foreign = orders.get(0).path("id").asLong();
        int status = send(CLIENTS.get("customer"), "GET", "/api/v1/orders/" + foreign, null).statusCode();
        assertEquals(true, status == 403 || status == 404, "another customer's order must not open: " + status);
        status = send(CLIENTS.get("customer"), "GET", "/api/v1/orders/" + foreign + "/receipt", null).statusCode();
        assertEquals(true, status == 403 || status == 404, "another customer's receipt must not open: " + status);
        status = send(CLIENTS.get("customer"), "GET", "/api/v1/installments/orders/" + foreign, null).statusCode();
        assertEquals(true, status == 403 || status == 404 || status == 204, "another customer's instalment file must not open: " + status);
    }
}
