package com.ebike.service.seo;

import com.ebike.dto.product.response.ProductDetailDto;
import com.ebike.dto.product.response.ProductSummaryDto;
import com.ebike.dto.product.response.ProductVariantDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * V35: everything a search engine or a chat app reads about a page, built from plain values so it can be unit tested: absolute
 * addresses, the schema.org JSON-LD blocks of a product page, {@code sitemap.xml} and {@code robots.txt}.
 *
 * <p>One instance is made per request around the shop's public address ({@code app.public-base-url}, or the address the visitor
 * used when that is not set), and the page templates call it as {@code seo}.
 */
public class SeoMarkup {
    /** A sitemap file may hold at most 50,000 addresses (sitemaps.org). */
    static final int SITEMAP_LIMIT = 50_000;
    static final int DESCRIPTION_LIMIT = 160;
    /** Who sells: the shop itself (the same name as the page title suffix). */
    static final String SELLER = "KINETIC";
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Paths crawlers are asked to stay out of: the API and the language switch, which are not pages. The private HTML pages
     * (login, cart, staff areas) stay crawlable on purpose: they carry a noindex tag, which a crawler only sees if it may fetch them.
     */
    static final List<String> PRIVATE_PATHS = List.of("/api/", "/lang/");
    /** Public pages that are not products. */
    static final List<String> STATIC_PATHS = List.of("/products", "/support", "/warranty");

    private final String baseUrl;

    public SeoMarkup(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.baseUrl = base;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /** An address a crawler can follow from anywhere: kept when already absolute, otherwise put after the shop's address. */
    public String absolute(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }
        String value = address.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return value;
        }
        if (value.startsWith("//")) {
            return (baseUrl.startsWith("https") ? "https:" : "http:") + value;
        }
        return baseUrl + (value.startsWith("/") ? "" : "/") + value;
    }

    /** The address of a page of the shop, e.g. {@code canonical("/products")}. */
    public String canonical(String path) {
        String value = path == null ? "" : path;
        return baseUrl + (value.startsWith("/") || value.isEmpty() ? "" : "/") + (value.isEmpty() ? "/" : value);
    }

    /** The one address of a product page, whichever of its aliases (/bike, /models, /product) the visitor came by. */
    public String productUrl(String slug) {
        return baseUrl + "/product/" + encodeSegment(slug);
    }

    /** First picture of a product as an absolute address, or the shop's placeholder (a PNG: chat apps do not draw SVG) for a product without photos. */
    public String productImage(List<String> images) {
        String first = images == null ? null : images.stream().filter(i -> i != null && !i.isBlank()).findFirst().orElse(null);
        return absolute(first == null ? "/images/og-default.png" : first);
    }

    /** Text for a description tag: one line, at most 160 characters, cut at a word; {@code fallback} when there is no text. */
    public static String description(String text, String fallback) {
        String clean = text == null ? "" : text.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        if (clean.isEmpty()) {
            clean = fallback == null ? "" : fallback.replaceAll("\\s+", " ").trim();
        }
        if (clean.length() <= DESCRIPTION_LIMIT) {
            return clean;
        }
        String cut = clean.substring(0, DESCRIPTION_LIMIT - 1);
        int space = cut.lastIndexOf(' ');
        if (space > DESCRIPTION_LIMIT / 2) {
            cut = cut.substring(0, space);
        }
        return cut.replaceAll("[\\s,;:.\\-]+$", "") + "…";
    }

    /** The price before any colour or battery surcharge: the discount when there is one. */
    static BigDecimal basePrice(ProductDetailDto product) {
        return product.discountPrice() != null ? product.discountPrice() : product.price();
    }

    /** The variant the page shows first: the one marked default, else the first. */
    static ProductVariantDto defaultVariant(ProductDetailDto product) {
        if (product.variants() == null || product.variants().isEmpty()) {
            return null;
        }
        return product.variants().stream().filter(v -> v != null && Boolean.TRUE.equals(v.defaultVariant())).findFirst()
            .orElseGet(() -> product.variants().stream().filter(java.util.Objects::nonNull).findFirst().orElse(null));
    }

    /** What one variant costs: the base price plus its surcharge (the same sum the product page and the cart show). */
    static BigDecimal variantPrice(ProductDetailDto product, ProductVariantDto variant) {
        BigDecimal base = basePrice(product);
        if (base == null || variant == null || variant.additionalPrice() == null) {
            return base;
        }
        return base.add(variant.additionalPrice());
    }

    /** The price a customer sees first and pays by default: the discount when there is one, plus the default variant's surcharge. */
    public static BigDecimal effectivePrice(ProductDetailDto product) {
        return variantPrice(product, defaultVariant(product));
    }

    /** True when the product has a price worth publishing (a missing or zero price would read as free). */
    public static boolean hasPrice(ProductDetailDto product) {
        BigDecimal price = effectivePrice(product);
        return price != null && price.signum() > 0;
    }

    /** Price as the plain number a crawler expects (dong has no decimals): {@code 25990000}; empty when there is none. */
    public static String priceText(BigDecimal price) {
        if (price == null) {
            return "";
        }
        return price.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    public static boolean inStock(ProductDetailDto product) {
        return product.stockQuantity() != null && product.stockQuantity() > 0;
    }

    /** The words of Open Graph's {@code product:availability}: {@code instock} or {@code oos}. */
    public static String availability(ProductDetailDto product) {
        return inStock(product) ? "instock" : "oos";
    }

    /** schema.org {@code Product} with its price, stock and (only when customers have reviewed it) rating. */
    public String productJsonLd(ProductDetailDto product) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("@context", "https://schema.org");
        node.put("@type", "Product");
        node.put("name", product.name());
        node.put("description", description(product.description(), product.name()));
        List<String> images = new ArrayList<>();
        if (product.images() != null) {
            product.images().stream().filter(i -> i != null && !i.isBlank()).map(this::absolute).forEach(images::add);
        }
        node.put("image", images.isEmpty() ? List.of(productImage(null)) : images);
        node.put("url", productUrl(product.slug()));
        ProductVariantDto first = defaultVariant(product);
        String sku = first == null || first.sku() == null || first.sku().isBlank() ? null : first.sku();
        if (sku != null) {
            node.put("sku", sku);
        }
        if (product.specification() != null && product.specification().brand() != null && !product.specification().brand().isBlank()) {
            node.put("brand", Map.of("@type", "Brand", "name", product.specification().brand().trim()));
        }
        if (product.category() != null && product.category().name() != null) {
            node.put("category", product.category().name());
        }
        // a product with no price is not free: say nothing about its offer rather than "0"
        if (hasPrice(product)) {
            Map<String, Object> offer = new LinkedHashMap<>();
            BigDecimal low = effectivePrice(product);
            BigDecimal high = low;
            java.util.Set<String> distinct = new java.util.LinkedHashSet<>();
            if (product.variants() != null) {
                for (ProductVariantDto variant : product.variants()) {
                    BigDecimal price = variant == null ? null : variantPrice(product, variant);
                    if (price != null) {
                        distinct.add(priceText(price));
                        low = low == null || price.compareTo(low) < 0 ? price : low;
                        high = high == null || price.compareTo(high) > 0 ? price : high;
                    }
                }
            }
            if (distinct.size() > 1) {
                // variants cost different amounts: say so, rather than one price that is wrong for some of them
                offer.put("@type", "AggregateOffer");
                offer.put("lowPrice", priceText(low));
                offer.put("highPrice", priceText(high));
                offer.put("offerCount", distinct.size());
            } else {
                offer.put("@type", "Offer");
                offer.put("price", priceText(effectivePrice(product)));
            }
            offer.put("url", productUrl(product.slug()));
            offer.put("priceCurrency", "VND");
            offer.put("itemCondition", "https://schema.org/NewCondition");
            offer.put("seller", Map.of("@type", "Organization", "name", SELLER, "url", canonical("/")));
            offer.put("availability", inStock(product) ? "https://schema.org/InStock" : "https://schema.org/OutOfStock");
            node.put("offers", offer);
        }
        // An aggregate rating with no reviews is invalid and would be a made-up number: leave it out.
        if (product.reviewCount() != null && product.reviewCount() > 0 && product.rating() != null && product.rating().signum() > 0) {
            Map<String, Object> rating = new LinkedHashMap<>();
            rating.put("@type", "AggregateRating");
            rating.put("ratingValue", product.rating().setScale(1, RoundingMode.HALF_UP).toPlainString());
            rating.put("reviewCount", product.reviewCount());
            rating.put("bestRating", "5");
            rating.put("worstRating", "1");
            node.put("aggregateRating", rating);
        }
        return scriptSafe(node);
    }

    /** schema.org {@code BreadcrumbList}: shop, catalogue, this bike. */
    public String breadcrumbJsonLd(ProductDetailDto product, String homeLabel, String catalogLabel) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("@context", "https://schema.org");
        node.put("@type", "BreadcrumbList");
        node.put("itemListElement", List.of(
            crumb(1, homeLabel, canonical("/")),
            crumb(2, catalogLabel, canonical("/products")),
            crumb(3, product.name(), productUrl(product.slug()))));
        return scriptSafe(node);
    }

    private static Map<String, Object> crumb(int position, String name, String url) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("@type", "ListItem");
        item.put("position", position);
        item.put("name", name);
        item.put("item", url);
        return item;
    }

    /**
     * JSON that may sit inside a script element: {@code <}, {@code >} and {@code &} are written as unicode escapes so a product
     * named {@code </script>} cannot close the element.
     */
    static String scriptSafe(Object node) {
        try {
            return JSON.writeValueAsString(node)
                .replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")
                .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to write structured data", exception);
        }
    }

    /** {@code sitemap.xml}: home page, catalogue, support, warranty and every product on sale (with its first photo). */
    public String sitemapXml(List<ProductSummaryDto> products) {
        StringBuilder xml = new StringBuilder(4096);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\" xmlns:image=\"http://www.google.com/schemas/sitemap-image/1.1\">\n");
        xml.append("  <url><loc>").append(escapeXml(canonical("/"))).append("</loc></url>\n");
        int count = 1;
        for (String path : STATIC_PATHS) {
            xml.append("  <url><loc>").append(escapeXml(baseUrl + path)).append("</loc></url>\n");
            count++;
        }
        if (products != null) {
            for (ProductSummaryDto product : products) {
                if (count >= SITEMAP_LIMIT) {
                    break;
                }
                if (product == null || product.slug() == null || product.slug().isBlank()) {
                    continue;
                }
                xml.append("  <url><loc>").append(escapeXml(productUrl(product.slug()))).append("</loc>");
                String photo = product.images() == null ? null : product.images().stream().filter(i -> i != null && !i.isBlank()).findFirst().orElse(null);
                if (photo != null) {
                    xml.append("<image:image><image:loc>").append(escapeXml(absolute(photo))).append("</image:loc></image:image>");
                }
                xml.append("</url>\n");
                count++;
            }
        }
        return xml.append("</urlset>\n").toString();
    }

    /** {@code robots.txt}: open to crawlers except the API and the language switch (private pages stay open so their noindex is seen); points at the sitemap. */
    public String robotsTxt() {
        StringBuilder text = new StringBuilder("User-agent: *\nAllow: /\n");
        // the reviews and colours of a product page are read from here by the page itself: a crawler that renders the page must be able to load them
        text.append("Allow: /api/v1/products/\n");
        for (String path : PRIVATE_PATHS) {
            text.append("Disallow: ").append(path).append('\n');
        }
        text.append("\nSitemap: ").append(baseUrl).append("/sitemap.xml\n");
        return text.toString();
    }

    static String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    /** One path segment, percent-encoded as UTF-8 (a slug is normally plain ASCII, but never trust it). */
    static String encodeSegment(String segment) {
        return segment == null ? "" : URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
