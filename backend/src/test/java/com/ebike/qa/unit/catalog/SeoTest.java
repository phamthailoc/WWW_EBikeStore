package com.ebike.qa.unit.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ebike.controller.web.SeoController;
import com.ebike.dto.product.response.CategorySummaryDto;
import com.ebike.dto.product.response.ProductDetailDto;
import com.ebike.dto.product.response.ProductSpecificationDto;
import com.ebike.dto.product.response.ProductSummaryDto;
import com.ebike.dto.product.response.ProductVariantDto;
import com.ebike.service.product.ProductService;
import com.ebike.service.seo.SeoAddress;
import com.ebike.service.seo.SeoMarkup;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SeoTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final SeoMarkup seo = new SeoMarkup("https://shop.example.com/");

    private static BigDecimal d(String v) {
        return v == null ? null : new BigDecimal(v);
    }

    private static ProductVariantDto variant(String sku, String extra, boolean def) {
        return new ProductVariantDto(1L, sku, "n", "c", "#000", "img", null, d(extra), 1, def, List.of());
    }

    private static ProductDetailDto product(String name, String price, String discount, Integer stock, BigDecimal rating, Integer reviews,
                                            List<ProductVariantDto> variants, List<String> images, String desc) {
        return new ProductDetailDto(1L, name, "xe-1", desc, d(price), d(discount), rating, reviews, stock, false,
            new CategorySummaryDto(1, "Xe dien", "xe-dien"),
            new ProductSpecificationDto("m", " Yadea ", "E_BIKE", null, null, null, null, null, null, null, 12, false),
            variants, images);
    }

    private JsonNode ld(ProductDetailDto p) throws Exception {
        return JSON.readTree(seo.productJsonLd(p));
    }

    // ---- addresses

    @Test
    void baseUrlLosesTrailingSlashesAndBlankIsEmpty() {
        assertThat(new SeoMarkup("https://a.com///").getBaseUrl()).isEqualTo("https://a.com");
        assertThat(new SeoMarkup(null).getBaseUrl()).isEmpty();
        assertThat(new SeoMarkup("  ").getBaseUrl()).isEmpty();
    }

    @Test
    void absoluteKeepsAbsoluteAddressesAndPrefixesRelativeOnes() {
        assertThat(seo.absolute("https://cdn.x/a.png")).isEqualTo("https://cdn.x/a.png");
        assertThat(seo.absolute("HTTP://cdn.x/a.png")).isEqualTo("HTTP://cdn.x/a.png");
        assertThat(seo.absolute("/images/a.png")).isEqualTo("https://shop.example.com/images/a.png");
        assertThat(seo.absolute("images/a.png")).isEqualTo("https://shop.example.com/images/a.png");
        assertThat(seo.absolute("//cdn.x/a.png")).isEqualTo("https://cdn.x/a.png");
        assertThat(new SeoMarkup("http://h").absolute("//cdn.x/a.png")).isEqualTo("http://cdn.x/a.png");
        assertThat(seo.absolute(null)).isEmpty();
        assertThat(seo.absolute("  ")).isEmpty();
    }

    @Test
    void canonicalAddresses() {
        assertThat(seo.canonical("/")).isEqualTo("https://shop.example.com/");
        assertThat(seo.canonical("")).isEqualTo("https://shop.example.com/");
        assertThat(seo.canonical(null)).isEqualTo("https://shop.example.com/");
        assertThat(seo.canonical("/products")).isEqualTo("https://shop.example.com/products");
        assertThat(seo.canonical("products")).isEqualTo("https://shop.example.com/products");
    }

    @Test
    void productUrlEncodesTheSlug() {
        assertThat(seo.productUrl("xe-dien")).isEqualTo("https://shop.example.com/product/xe-dien");
        assertThat(seo.productUrl("a b/ç?x")).isEqualTo("https://shop.example.com/product/a%20b%2F%C3%A7%3Fx");
        assertThat(seo.productUrl(null)).isEqualTo("https://shop.example.com/product/");
    }

    @Test
    void productImageUsesFirstNonBlankOrAPngPlaceholder() {
        assertThat(seo.productImage(java.util.Arrays.asList(null, " ", "/a.png", "/b.png"))).isEqualTo("https://shop.example.com/a.png");
        assertThat(seo.productImage(null)).isEqualTo("https://shop.example.com/images/og-default.png");
        assertThat(seo.productImage(List.of())).endsWith(".png");
    }

    // ---- description

    @Test
    void descriptionStripsTagsCollapsesWhitespaceAndFallsBack() {
        assertThat(SeoMarkup.description(" <b>Xe</b>\n\n  điện\t nhanh ", "x")).isEqualTo("Xe điện nhanh");
        assertThat(SeoMarkup.description(null, " fallback  text ")).isEqualTo("fallback text");
        assertThat(SeoMarkup.description("<p></p>", "fb")).isEqualTo("fb");
        assertThat(SeoMarkup.description(null, null)).isEmpty();
    }

    @Test
    void descriptionIsAtMost160CharactersAndCutAtAWord() {
        String text = "word ".repeat(100);
        String out = SeoMarkup.description(text, "x");
        assertThat(out.length()).isLessThanOrEqualTo(160);
        assertThat(out).endsWith("…").doesNotContain("wor…");
        assertThat(SeoMarkup.description("a".repeat(160), "x")).hasSize(160);
        assertThat(SeoMarkup.description("a".repeat(161), "x")).hasSize(159 + 1);
    }

    @Test
    void descriptionNeverSplitsAnEmojiInHalf() {
        String text = "a".repeat(157) + "\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00";
        String out = SeoMarkup.description(text, "x");
        for (int i = 0; i < out.length(); i++) {
            if (Character.isHighSurrogate(out.charAt(i))) {
                assertThat(i + 1).isLessThan(out.length());
                assertThat(Character.isLowSurrogate(out.charAt(i + 1))).isTrue();
            }
        }
    }

    // ---- price helpers

    @Test
    void effectivePriceIsDiscountPlusDefaultVariantSurcharge() {
        ProductDetailDto p = product("n", "30000000", "25000000", 1, null, null,
            List.of(variant("A", "1000000", false), variant("B", "2000000", true)), List.of(), null);
        assertThat(SeoMarkup.effectivePrice(p)).isEqualByComparingTo("27000000");
        assertThat(SeoMarkup.hasPrice(p)).isTrue();
        assertThat(SeoMarkup.priceText(new BigDecimal("25990000.50"))).isEqualTo("25990001");
        assertThat(SeoMarkup.priceText(null)).isEmpty();
    }

    @Test
    void zeroOrMissingPriceIsNotPublished() throws Exception {
        assertThat(SeoMarkup.hasPrice(product("n", "0", null, 1, null, null, null, null, null))).isFalse();
        assertThat(SeoMarkup.hasPrice(product("n", null, null, 1, null, null, null, null, null))).isFalse();
        assertThat(ld(product("n", "0", null, 1, null, null, null, null, null)).has("offers")).isFalse();
    }

    @Test
    void availabilityAndStock() {
        assertThat(SeoMarkup.availability(product("n", "1", null, 3, null, null, null, null, null))).isEqualTo("instock");
        assertThat(SeoMarkup.availability(product("n", "1", null, 0, null, null, null, null, null))).isEqualTo("oos");
        assertThat(SeoMarkup.availability(product("n", "1", null, null, null, null, null, null, null))).isEqualTo("oos");
        assertThat(SeoMarkup.inStock(product("n", "1", null, -1, null, null, null, null, null))).isFalse();
    }

    // ---- JSON-LD

    @Test
    void productJsonLdCarriesOfferBrandCategoryAndSku() throws Exception {
        JsonNode n = ld(product("Xe A", "25990000", null, 2, null, null, List.of(variant("SKU1", null, true)), List.of("/a.png"), "<i>mô tả</i>"));
        assertThat(n.get("@type").asText()).isEqualTo("Product");
        assertThat(n.get("description").asText()).isEqualTo("mô tả");
        assertThat(n.get("image").get(0).asText()).isEqualTo("https://shop.example.com/a.png");
        assertThat(n.get("url").asText()).isEqualTo("https://shop.example.com/product/xe-1");
        assertThat(n.get("sku").asText()).isEqualTo("SKU1");
        assertThat(n.get("brand").get("name").asText()).isEqualTo("Yadea");
        assertThat(n.get("category").asText()).isEqualTo("Xe dien");
        JsonNode offer = n.get("offers");
        assertThat(offer.get("@type").asText()).isEqualTo("Offer");
        assertThat(offer.get("price").asText()).isEqualTo("25990000");
        assertThat(offer.get("priceCurrency").asText()).isEqualTo("VND");
        assertThat(offer.get("availability").asText()).isEqualTo("https://schema.org/InStock");
    }

    @Test
    void variantsWithDifferentPricesBecomeAnAggregateOffer() throws Exception {
        JsonNode offer = ld(product("n", "100", "90", 1, null, null,
            List.of(variant("A", "0", true), variant("B", "50", false)), null, null)).get("offers");
        assertThat(offer.get("@type").asText()).isEqualTo("AggregateOffer");
        assertThat(offer.get("lowPrice").asText()).isEqualTo("90");
        assertThat(offer.get("highPrice").asText()).isEqualTo("140");
        assertThat(offer.get("offerCount").asInt()).isEqualTo(2);
    }

    @Test
    void ratingIsOmittedWithoutReviewsAndPresentWithThem() throws Exception {
        assertThat(ld(product("n", "1", null, 1, new BigDecimal("4.5"), 0, null, null, null)).has("aggregateRating")).isFalse();
        assertThat(ld(product("n", "1", null, 1, BigDecimal.ZERO, 3, null, null, null)).has("aggregateRating")).isFalse();
        assertThat(ld(product("n", "1", null, 1, null, null, null, null, null)).has("aggregateRating")).isFalse();
        JsonNode rating = ld(product("n", "1", null, 1, new BigDecimal("4.666"), 3, null, null, null)).get("aggregateRating");
        assertThat(rating.get("ratingValue").asText()).isEqualTo("4.7");
        assertThat(rating.get("reviewCount").asInt()).isEqualTo(3);
        assertThat(rating.get("bestRating").asText()).isEqualTo("5");
    }

    @Test
    void scriptClosingTextInAProductNameCannotBreakOutOfTheJsonLdBlock() {
        String json = seo.productJsonLd(product("</script><script>alert(1)</script> & <b>", "1", null, 1, null, null, null, null, "x"));
        assertThat(json).doesNotContain("<").doesNotContain(">").doesNotContain("&");
        assertThat(json).contains("\\u003c/script\\u003e");
    }

    @Test
    void productWithoutImagesGetsThePlaceholderImage() throws Exception {
        assertThat(ld(product("n", "1", null, 1, null, null, null, List.of(), null)).get("image").get(0).asText()).endsWith("/images/og-default.png");
    }

    @Test
    void breadcrumbHasThreeLevels() throws Exception {
        JsonNode n = JSON.readTree(seo.breadcrumbJsonLd(product("Xe A", "1", null, 1, null, null, null, null, null), "Home", "Shop"));
        assertThat(n.get("itemListElement")).hasSize(3);
        assertThat(n.get("itemListElement").get(2).get("name").asText()).isEqualTo("Xe A");
        assertThat(n.get("itemListElement").get(0).get("item").asText()).isEqualTo("https://shop.example.com/");
    }

    // ---- sitemap / robots

    private static ProductSummaryDto summary(String slug, List<String> images) {
        return new ProductSummaryDto(1L, "n", slug, "d", BigDecimal.ONE, null, null, 0, 1, false, null, images);
    }

    @Test
    void sitemapIsWellFormedXmlWithEscapedAddressesAndPhotos() throws Exception {
        String xml = seo.sitemapXml(List.of(summary("a&b", List.of("/x.png?a=1&b=2")), summary("ok", null), summary(" ", null), summary(null, null)));
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(xml).contains("<loc>https://shop.example.com/product/a%26b</loc>").contains("a=1&amp;b=2");
        assertThat(xml).contains("/products</loc>").contains("/support</loc>").contains("/warranty</loc>");
        assertThat(xml.split("<url>").length - 1).isEqualTo(4 + 2);
    }

    @Test
    void sitemapOfNullProductsStillListsStaticPages() {
        assertThat(seo.sitemapXml(null).split("<url>").length - 1).isEqualTo(4);
    }

    @Test
    void sitemapNeverExceedsFiftyThousandUrls() {
        List<ProductSummaryDto> many = new ArrayList<>();
        for (int i = 0; i < 50_100; i++) {
            many.add(summary("p" + i, null));
        }
        assertThat(seo.sitemapXml(many).split("<url>").length - 1).isEqualTo(50_000);
    }

    @Test
    void robotsBlocksOnlyTheApiAndPointsAtTheSitemap() {
        String robots = seo.robotsTxt();
        assertThat(robots).contains("User-agent: *").contains("Disallow: /api/").contains("Sitemap: https://shop.example.com/sitemap.xml");
        assertThat(robots).doesNotContain("Disallow: /\n").doesNotContain("Disallow: /admin").doesNotContain("Disallow: /customer");
        assertThat(robots.indexOf("Allow: /api/v1/products/")).isLessThan(robots.indexOf("Disallow: /api/"));
    }

    // ---- SeoAddress

    @Test
    void configuredAddressIsOnlyUsedWhenItIsPublic() {
        assertThat(new SeoAddress("https://shop.vn").isConfigured()).isTrue();
        assertThat(new SeoAddress("").isConfigured()).isFalse();
        assertThat(new SeoAddress(null).isConfigured()).isFalse();
        assertThat(new SeoAddress("http://localhost:8080").isConfigured()).isFalse();
        assertThat(new SeoAddress("http://127.0.0.1:8080").isConfigured()).isFalse();
        assertThat(new SeoAddress("http://[::1]:8080").isConfigured()).isFalse();
    }

    @Test
    void varyHeaderOnlyWhenTheAddressDependsOnTheHost() {
        assertThat(new SeoAddress("https://shop.vn").varyHeaders().get("Vary")).isNull();
        assertThat(new SeoAddress("").varyHeaders().get("Vary")).containsExactly("Host");
    }

    @Test
    void markupFollowsTheVisitorsAddressWhenNothingIsConfigured() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("example.org");
        request.setServerPort(8080);
        request.setContextPath("/shop");
        assertThat(new SeoAddress("").markup(request).getBaseUrl()).isEqualTo("http://example.org:8080/shop");
        request.setServerPort(80);
        assertThat(new SeoAddress("").markup(request).getBaseUrl()).isEqualTo("http://example.org/shop");
        request.setScheme("https");
        request.setServerPort(443);
        assertThat(new SeoAddress("").markup(request).getBaseUrl()).isEqualTo("https://example.org/shop");
        request.setServerPort(80); // https on 80 is not the standard port
        assertThat(new SeoAddress("").markup(request).getBaseUrl()).isEqualTo("https://example.org:80/shop");
        assertThat(new SeoAddress("https://shop.vn/").markup(request).getBaseUrl()).isEqualTo("https://shop.vn");
    }

    // ---- controller

    @Test
    void sitemapAndRobotsAreCachedPubliclyForTenMinutes() throws Exception {
        ProductService products = mock(ProductService.class);
        when(products.getProducts(null, null, null, null, null, null, null, null, null, null, null, null)).thenReturn(List.of(summary("xe-1", null)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SeoController(products, new SeoAddress("https://shop.vn"))).build();
        mvc.perform(MockMvcRequestBuilders.get("/sitemap.xml"))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.header().string("Cache-Control", "max-age=600, public"))
            .andExpect(MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("https://shop.vn/product/xe-1")));
        mvc.perform(MockMvcRequestBuilders.get("/robots.txt"))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Sitemap: https://shop.vn/sitemap.xml")));
    }
}
