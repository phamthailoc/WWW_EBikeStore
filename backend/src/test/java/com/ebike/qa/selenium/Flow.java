package com.ebike.qa.selenium;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;

/** Moves orders through their life by API, so that UI tests can start from the state they want to look at. */
public final class Flow {
    private Flow() { }

    public static JsonNode guestOrder(String paymentMethod) {
        return new Api().placeOrder("Khách Kiểm Thử", QaConfig.uniqueUsername() + "@example.com", Shop.buySlug(), 1, paymentMethod);
    }

    public static JsonNode guestPickupOrder() {
        Api guest = new Api();
        ObjectNode body = Api.orderBody("Khách Nhận Xe", QaConfig.uniqueUsername() + "@example.com", "0901234567", guest.product(Shop.buySlug()), 1, "PAY_LATER");
        body.put("deliveryMethod", "STORE_PICKUP").putNull("province").putNull("detailedAddress").put("pickupShowroomId", 1);
        Api.Reply r = guest.post("/api/v1/orders", body);
        if (r.status() >= 300) throw new IllegalStateException(r.status() + " " + r.body());
        return r.json();
    }

    public static void status(Api manager, JsonNode order, String status) {
        Api.Reply r = manager.send("PATCH", "/api/v1/orders/" + order.get("id").asLong() + "/status", status.equals("CANCELLED") ? Map.of("status", status, "reason", "QA huy don") : Map.of("status", status));
        if (r.status() >= 300) throw new IllegalStateException("status " + status + ": " + r.status() + " " + r.body());
    }

    /** Frame and engine numbers for every bike of the order; returns the first frame number. */
    public static String units(Api manager, JsonNode order) {
        String stamp = Long.toString(System.nanoTime() % 100_000_000_000L);
        var units = Api.JSON.createArrayNode();
        String first = null;
        for (JsonNode item : order.get("items")) {
            for (int i = 0; i < item.get("quantity").asInt(); i++) {
                String frame = "QAFR" + stamp + i;
                if (first == null) first = frame;
                units.addObject().put("orderItemId", item.get("id").asLong()).put("frameNumber", frame).put("engineNumber", "QAEN" + stamp + i);
            }
        }
        Api.Reply r = manager.send("PUT", "/api/v1/manager/orders/" + order.get("id").asLong() + "/units", Api.JSON.createObjectNode().set("units", units));
        if (r.status() >= 300) throw new IllegalStateException("units: " + r.status() + " " + r.body());
        return first;
    }

    /** Confirms, processes, numbers, ships and delivers a home-delivery order; returns the frame number. */
    public static String deliver(JsonNode order) {
        Api manager = Api.staff("manager");
        status(manager, order, "CONFIRMED");
        status(manager, order, "PROCESSING");
        String frame = units(manager, order);
        status(manager, order, "SHIPPED");
        status(manager, order, "DELIVERED");
        return frame;
    }
}
