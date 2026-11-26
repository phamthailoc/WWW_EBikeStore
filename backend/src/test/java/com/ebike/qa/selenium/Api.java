package com.ebike.qa.selenium;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Plain HTTP client with its own cookie jar, used only to prepare data and to read facts the browser cannot see (status codes, mail sink). */
public final class Api {
    public static final ObjectMapper JSON = new ObjectMapper();

    public record Reply(int status, String body, java.net.http.HttpHeaders headers) {
        public JsonNode json() {
            try { return JSON.readTree(body); } catch (Exception e) { throw new IllegalStateException("not JSON: " + body, e); }
        }
    }

    private final HttpClient client = HttpClient.newBuilder()
        .cookieHandler(new CookieManager()).followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build();

    public Reply send(String method, String path, Object body) {
        try {
            String url = path.startsWith("http") ? path : QaConfig.baseUrl() + path;
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("Accept", "application/json");
            if (body == null) {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body instanceof String s ? s : JSON.writeValueAsString(body)));
            }
            HttpResponse<String> r = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Reply(r.statusCode(), r.body(), r.headers());
        } catch (Exception e) {
            throw new IllegalStateException(method + " " + path + " failed", e);
        }
    }

    public Reply get(String path) { return send("GET", path, null); }

    public Reply post(String path, Object body) { return send("POST", path, body); }

    public Api login(String user, String password) {
        ObjectNode b = JSON.createObjectNode().put("usernameOrEmail", user).put("password", password);
        Reply r = post("/api/v1/auth/login", b);
        if (r.status() != 200) throw new IllegalStateException("login " + user + " -> " + r.status() + " " + r.body());
        return this;
    }

    public static Api staff(String role) {
        return new Api().login(QaConfig.get("staff." + role + ".username"), QaConfig.get("staff." + role + ".password"));
    }

    /** Status code of a plain GET without cookies (static files, error pages). */
    public static int statusOf(String path) { return new Api().get(path).status(); }

    public JsonNode product(String slug) { return get("/api/v1/products/" + slug).json(); }

    /** First product in stock with a coloured variant: the product the tests buy. */
    public JsonNode firstSellable() {
        JsonNode p = product(Shop.buySlug());
        return p;
    }

    public static ObjectNode orderBody(String name, String email, String phone, JsonNode product, int quantity, String payment) {
        ObjectNode o = JSON.createObjectNode();
        o.put("customerName", name).put("phoneNumber", phone).put("customerEmail", email);
        o.put("deliveryMethod", "HOME_DELIVERY").put("province", "TP. Hồ Chí Minh").put("detailedAddress", "12 Nguyễn Trãi, Phường Bến Thành");
        o.put("paymentMethod", payment).put("includeRegistrationService", false).put("notes", "qa selenium");
        ArrayNode items = o.putArray("items");
        ObjectNode line = items.addObject().put("productId", product.get("id").asLong()).put("quantity", quantity);
        JsonNode variants = product.get("variants");
        if (variants != null && variants.size() > 0) {
            for (JsonNode v : variants) {
                if (v.get("stockQuantity").asInt() > quantity) { line.put("productVariantId", v.get("id").asLong()); break; }
            }
        }
        return o;
    }

    /** Places an order through the API (as whoever this client is logged in as, or as a guest) and returns the order JSON. */
    public JsonNode placeOrder(String name, String email, String slug, int quantity, String payment) {
        ObjectNode body = orderBody(name, email, "0901234567", product(slug), quantity, payment);
        Reply r = post("/api/v1/orders", body);
        if (r.status() >= 300) throw new IllegalStateException("order failed " + r.status() + " " + r.body());
        return r.json();
    }

    /** A valid quote request for the bike tests buy. */
    public ObjectNode quoteBody(String code, boolean registration, String delivery) {
        JsonNode p = product(Shop.buySlug());
        ObjectNode o = JSON.createObjectNode();
        ObjectNode line = o.putArray("items").addObject().put("productId", p.get("id").asLong()).put("quantity", 1);
        for (JsonNode v : p.get("variants")) { if (v.get("stockQuantity").asInt() > 0) { line.put("productVariantId", v.get("id").asLong()); break; } }
        if (code != null) o.put("discountCode", code);
        o.put("includeRegistrationService", registration).put("deliveryMethod", delivery);
        if (delivery.equals("HOME_DELIVERY")) o.put("province", "Hà Nội");
        return o;
    }

    public JsonNode placeOrder(String email) { return placeOrder("Khach Kiem Thu", email, Shop.buySlug(), 1, "PAY_LATER"); }
}
