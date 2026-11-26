package com.ebike.qa.selenium;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/**
 * Which bike a test uses. Orders take stock out of the shop, so tests that place orders buy the bike with the most stock left
 * (never the two bikes the look-only tests use), and look-only tests keep to a bike that no order touches.
 */
public final class Shop {
    /** A bike with six colours that no test buys: product page, cart and compare tests use it. */
    public static final String SHOW = "kinetic-creer-e";
    public static final String SHOW_NAME = "Creer E";
    /** A second bike for "two different bikes in the cart". */
    public static final String OTHER = "kinetic-amy";

    private static final Set<String> RESERVED = Set.of(SHOW, OTHER);
    private static String cached;
    private static long cachedAt;

    private Shop() { }

    public static synchronized String buySlug() {
        if (cached != null && System.currentTimeMillis() - cachedAt < 120_000) return cached;
        Api api = new Api();
        String best = null;
        int bestStock = -1;
        for (JsonNode p : api.get("/api/v1/products").json()) {
            String slug = p.get("slug").asText();
            if (RESERVED.contains(slug)) continue;
            JsonNode detail = api.product(slug);
            int stock = 0;
            JsonNode variants = detail.get("variants");
            if (variants != null && variants.size() > 0) {
                for (JsonNode v : variants) stock = Math.max(stock, v.get("stockQuantity").asInt());
            } else {
                stock = detail.get("stockQuantity").asInt();
            }
            if (stock > bestStock) { bestStock = stock; best = slug; }
        }
        cached = best;
        cachedAt = System.currentTimeMillis();
        return cached;
    }

    /** Name (without the brand) of the bike tests buy, as it appears in order lines. */
    public static String buyName() { return new Api().product(buySlug()).get("name").asText(); }
}
