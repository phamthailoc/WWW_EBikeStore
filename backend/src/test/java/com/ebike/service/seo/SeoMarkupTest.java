package com.ebike.service.seo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.dto.product.response.CategorySummaryDto;
import com.ebike.dto.product.response.ProductDetailDto;
import com.ebike.dto.product.response.ProductSpecificationDto;
import com.ebike.dto.product.response.ProductSummaryDto;
import com.ebike.dto.product.response.ProductVariantDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** V35: addresses, structured data, sitemap and robots.txt are built from plain values, so they are checked without a server. */
class SeoMarkupTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final SeoMarkup seo = new SeoMarkup("https://shop.example.vn/");

    private static ProductDetailDto detail(String name, BigDecimal price, BigDecimal discount, BigDecimal rating, Integer reviews, Integer stock, List<String> images) {
        return new ProductDetailDto(1L, name, "kinetic-x", "Xe điện  nhẹ,\n bền.", price, discount, rating, reviews, stock, false,
            new CategorySummaryDto(1, "Xe tay ga điện", "xe-tay-ga"),
            new ProductSpecificationDto("X1", "Kinetic", "E_SCOOTER", "LFP", null, null, null, null, null, null, 24, false),
            List.of(new ProductVariantDto(5L, "KX-RED", "Đỏ", "Đỏ", "#f00", null, null, null, 3, true, List.of())), images);
    }

    private static ProductSummaryDto summary(String slug, List<String> images) {
        return new ProductSummaryDto(1L, "N", slug, "d", BigDecimal.TEN, null, null, 0, 1, false, new CategorySummaryDto(1, "c", "c"), images);
    }

    @Test
    void theShopAddressLosesItsTrailingSlashAndRelativeAddressesBecomeAbsolute() {
        assertEquals("https://shop.example.vn", seo.getBaseUrl());
        assertEquals("https://shop.example.vn/images/a.png", seo.absolute("/images/a.png"));
        assertEquals("https://shop.example.vn/images/a.png", seo.absolute("images/a.png"));
        assertEquals("https://cdn.example.com/p.jpg", seo.absolute("https://cdn.example.com/p.jpg"));
        assertEquals("https://cdn.example.com/p.jpg", seo.absolute("//cdn.example.com/p.jpg"));
        assertEquals("", seo.absolute("  "));
        assertEquals("", seo.absolute(null));
        assertEquals("https://shop.example.vn/", seo.canonical("/"));
        assertEquals("https://shop.example.vn/products?page=2", seo.canonical("/products?page=2"));
    }

    @Test
    void aProductHasOneAddressWhateverItsSlugHolds() {
        assertEquals("https://shop.example.vn/product/kinetic-x", seo.productUrl("kinetic-x"));
        assertEquals("https://shop.example.vn/product/xe%20%C4%91i%E1%BB%87n", seo.productUrl("xe điện"));
        assertEquals("https://shop.example.vn/product/a%2Fb%3Fc", seo.productUrl("a/b?c"));
    }

    @Test
    void theShareImageIsTheFirstPhotoOrThePlaceholder() {
        assertEquals("https://shop.example.vn/images/catalog/a.jpg", seo.productImage(List.of("", "/images/catalog/a.jpg", "/images/b.jpg")));
        assertEquals("https://shop.example.vn/images/hero-fallback.svg", seo.productImage(List.of()));
        assertEquals("https://shop.example.vn/images/hero-fallback.svg", seo.productImage(null));
    }

    @Test
    void descriptionsAreOneShortLine() {
        assertEquals("Xe điện nhẹ, bền.", SeoMarkup.description("Xe điện  nhẹ,\n bền.", "x"));
        assertEquals("Tên xe", SeoMarkup.description("   ", "Tên xe"));
        assertEquals("Mô tả", SeoMarkup.description("<p>Mô <b>tả</b></p>", "x"));
        String cut = SeoMarkup.description("từ ".repeat(100), "x");
        assertTrue(cut.length() <= SeoMarkup.DESCRIPTION_LIMIT, "at most 160 characters: " + cut.length());
        assertTrue(cut.endsWith("…"));
        assertFalse(cut.contains("\n"));
        assertEquals("", SeoMarkup.description(null, null));
    }

    @Test
    void priceIsThePlainDiscountedNumber() {
        ProductDetailDto full = detail("X", new BigDecimal("25990000.00"), null, null, 0, 5, List.of());
        ProductDetailDto sale = detail("X", new BigDecimal("25990000"), new BigDecimal("23990000.4"), null, 0, 5, List.of());
        assertEquals("25990000", SeoMarkup.priceText(SeoMarkup.effectivePrice(full)));
        assertEquals("23990000", SeoMarkup.priceText(SeoMarkup.effectivePrice(sale)));
        assertEquals("0", SeoMarkup.priceText(null));
    }

    @Test
    void stockDecidesAvailability() {
        assertEquals("instock", SeoMarkup.availability(detail("X", BigDecimal.ONE, null, null, 0, 1, List.of())));
        assertEquals("oos", SeoMarkup.availability(detail("X", BigDecimal.ONE, null, null, 0, 0, List.of())));
        assertEquals("oos", SeoMarkup.availability(detail("X", BigDecimal.ONE, null, null, 0, null, List.of())));
    }

    @Test
    void theProductDataCarriesPriceStockAndRating() throws Exception {
        JsonNode node = JSON.readTree(seo.productJsonLd(detail("Kinetic X", new BigDecimal("25990000"), new BigDecimal("23990000"), new BigDecimal("4.55"), 12, 3,
            List.of("/images/a.jpg", "https://cdn.example.com/b.jpg"))));
        assertEquals("Product", node.path("@type").asText());
        assertEquals("Kinetic X", node.path("name").asText());
        assertEquals("KX-RED", node.path("sku").asText());
        assertEquals("Kinetic", node.path("brand").path("name").asText());
        assertEquals("https://shop.example.vn/images/a.jpg", node.path("image").get(0).asText());
        assertEquals("https://cdn.example.com/b.jpg", node.path("image").get(1).asText());
        JsonNode offer = node.path("offers");
        assertEquals("VND", offer.path("priceCurrency").asText());
        assertEquals("23990000", offer.path("price").asText());
        assertEquals("https://schema.org/InStock", offer.path("availability").asText());
        assertEquals("https://shop.example.vn/product/kinetic-x", offer.path("url").asText());
        assertEquals("4.6", node.path("aggregateRating").path("ratingValue").asText());
        assertEquals(12, node.path("aggregateRating").path("reviewCount").asInt());
    }

    @Test
    void aProductWithoutReviewsClaimsNoRatingAndASoldOutOneSaysSo() throws Exception {
        for (BigDecimal rating : new BigDecimal[] {null, BigDecimal.ZERO, new BigDecimal("4.5")}) {
            JsonNode node = JSON.readTree(seo.productJsonLd(detail("X", BigDecimal.TEN, null, rating, rating == null ? null : 0, 0, List.of())));
            assertTrue(node.path("aggregateRating").isMissingNode(), "no reviews, no rating: " + rating);
            assertEquals("https://schema.org/OutOfStock", node.path("offers").path("availability").asText());
            assertEquals("https://shop.example.vn/images/hero-fallback.svg", node.path("image").get(0).asText(), "a product without photos still names an image");
        }
    }

    @Test
    void aProductWithoutSpecificationOrVariantsStillProducesValidData() throws Exception {
        ProductDetailDto bare = new ProductDetailDto(1L, "Bare", "bare", null, BigDecimal.TEN, null, null, null, null, null, null, null, null, null);
        JsonNode node = JSON.readTree(seo.productJsonLd(bare));
        assertTrue(node.path("brand").isMissingNode());
        assertTrue(node.path("sku").isMissingNode());
        assertTrue(node.path("category").isMissingNode());
        assertEquals("Bare", node.path("description").asText(), "falls back to the name");
    }

    @Test
    void aNameCannotCloseTheScriptElement() throws Exception {
        String json = seo.productJsonLd(detail("</script><script>alert(1)</script> & Co", BigDecimal.TEN, null, null, 0, 1, List.of()));
        assertFalse(json.contains("<"), json);
        assertFalse(json.contains(">"), json);
        assertFalse(json.contains("&"), json);
        assertEquals("</script><script>alert(1)</script> & Co", JSON.readTree(json).path("name").asText(), "the escapes decode back to the name");
    }

    @Test
    void theBreadcrumbListsShopCatalogueAndBike() throws Exception {
        JsonNode node = JSON.readTree(seo.breadcrumbJsonLd(detail("Kinetic X", BigDecimal.TEN, null, null, 0, 1, List.of()), "Trang chủ", "Bộ sưu tập"));
        assertEquals("BreadcrumbList", node.path("@type").asText());
        JsonNode items = node.path("itemListElement");
        assertEquals(3, items.size());
        assertEquals("https://shop.example.vn/", items.get(0).path("item").asText());
        assertEquals("https://shop.example.vn/products", items.get(1).path("item").asText());
        assertEquals("Kinetic X", items.get(2).path("name").asText());
        assertEquals(3, items.get(2).path("position").asInt());
    }

    @Test
    void theSitemapListsThePagesAndEveryProductWithItsPhoto() {
        String xml = seo.sitemapXml(List.of(summary("kinetic-x", List.of("/images/a.jpg")), summary("kinetic-y", List.of()), summary("a&b", List.of("/i.jpg?x=1&y=2"))));
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(xml.contains("<loc>https://shop.example.vn/</loc>"));
        assertTrue(xml.contains("<loc>https://shop.example.vn/products</loc>"));
        assertTrue(xml.contains("<loc>https://shop.example.vn/support</loc>"));
        assertTrue(xml.contains("<loc>https://shop.example.vn/warranty</loc>"));
        assertTrue(xml.contains("<loc>https://shop.example.vn/product/kinetic-x</loc><image:image><image:loc>https://shop.example.vn/images/a.jpg</image:loc></image:image>"));
        assertTrue(xml.contains("<loc>https://shop.example.vn/product/kinetic-y</loc></url>"), "no photo, no image element");
        assertTrue(xml.contains("<loc>https://shop.example.vn/product/a%26b</loc>"));
        assertTrue(xml.contains("https://shop.example.vn/i.jpg?x=1&amp;y=2"), "ampersands are escaped");
        assertTrue(xml.trim().endsWith("</urlset>"));
    }

    @Test
    void anEmptyShopStillGivesAValidSitemap() {
        for (List<ProductSummaryDto> none : Arrays.asList(List.<ProductSummaryDto>of(), null)) {
            String xml = seo.sitemapXml(none);
            assertTrue(xml.contains("<loc>https://shop.example.vn/</loc>"));
            assertFalse(xml.contains("/product/"));
        }
    }

    @Test
    void productsWithoutASlugAreSkippedAndTheListIsCapped() {
        List<ProductSummaryDto> many = new ArrayList<>();
        many.add(summary(null, List.of()));
        many.add(summary(" ", List.of()));
        many.add(null);
        for (int i = 0; i < SeoMarkup.SITEMAP_LIMIT + 10; i++) {
            many.add(summary("p" + i, List.of()));
        }
        String xml = seo.sitemapXml(many);
        assertEquals(SeoMarkup.SITEMAP_LIMIT, xml.split("<url>", -1).length - 1, "no more than 50,000 addresses");
        assertFalse(xml.contains("<loc>https://shop.example.vn/product/</loc>"));
    }

    @Test
    void robotsKeepsCrawlersOutOfAccountStaffCartAndApiPagesAndNamesTheSitemap() {
        String robots = seo.robotsTxt();
        assertTrue(robots.startsWith("User-agent: *\nAllow: /\n"));
        for (String path : List.of("/api/", "/admin", "/manager", "/customer", "/cart", "/checkout", "/favorites", "/login", "/signup", "/lang/")) {
            assertTrue(robots.contains("Disallow: " + path + "\n"), path);
        }
        assertFalse(robots.contains("Disallow: /\n"), "the shop itself stays open");
        assertFalse(robots.contains("Disallow: /product"), "products stay open");
        assertTrue(robots.contains("Sitemap: https://shop.example.vn/sitemap.xml\n"));
    }
    private static ProductDetailDto withVariants(BigDecimal price, BigDecimal discount, ProductVariantDto... variants) {
        ProductDetailDto base = detail("X", price, discount, null, 0, 5, List.of());
        return new ProductDetailDto(base.id(), base.name(), base.slug(), base.description(), base.price(), base.discountPrice(), base.rating(), base.reviewCount(),
            base.stockQuantity(), base.featured(), base.category(), base.specification(), List.of(variants), base.images());
    }

    private static ProductVariantDto variant(String sku, String surcharge, boolean isDefault) {
        return new ProductVariantDto(1L, sku, sku, sku, "#000", null, null, new BigDecimal(surcharge), 3, isDefault, List.of());
    }

    @Test
    void theDefaultVariantsSurchargeAndSkuAreWhatTheOfferStates() throws Exception {
        ProductDetailDto product = withVariants(new BigDecimal("20000000"), new BigDecimal("18000000"), variant("A", "0", false), variant("B", "0", true));
        assertEquals("18000000", SeoMarkup.priceText(SeoMarkup.effectivePrice(product)));
        ProductDetailDto surcharged = withVariants(new BigDecimal("20000000"), new BigDecimal("18000000"), variant("A", "0", false), variant("B", "500000", true));
        assertEquals("18500000", SeoMarkup.priceText(SeoMarkup.effectivePrice(surcharged)));
        JsonNode node = JSON.readTree(seo.productJsonLd(surcharged));
        assertEquals("B", node.path("sku").asText(), "the default variant, not the first one");
        assertEquals("AggregateOffer", node.path("offers").path("@type").asText());
        assertEquals("18000000", node.path("offers").path("lowPrice").asText());
        assertEquals("18500000", node.path("offers").path("highPrice").asText());
        assertEquals(2, node.path("offers").path("offerCount").asInt());
    }

    @Test
    void variantsThatCostTheSameGiveOneSimpleOffer() throws Exception {
        ProductDetailDto product = withVariants(new BigDecimal("20000000"), null, variant("A", "0", true), variant("B", "0", false));
        JsonNode offer = JSON.readTree(seo.productJsonLd(product)).path("offers");
        assertEquals("Offer", offer.path("@type").asText());
        assertEquals("20000000", offer.path("price").asText());
    }
}
