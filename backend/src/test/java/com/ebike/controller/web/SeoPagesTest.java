package com.ebike.controller.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.ebike.dto.product.response.CategorySummaryDto;
import com.ebike.dto.product.response.ProductDetailDto;
import com.ebike.dto.product.response.ProductFilterOptionsDto;
import com.ebike.dto.product.response.ProductSpecificationDto;
import com.ebike.dto.product.response.ProductSummaryDto;
import com.ebike.dto.product.response.ProductVariantDto;
import com.ebike.service.product.ProductService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * V35: renders the real pages with the real templates (products mocked) and reads the head of each: canonical address, Open Graph,
 * structured data, noindex on private and narrowed pages, plus the sitemap and robots.txt endpoints and their headers.
 */
class SeoPagesTest {
    private static final String BASE = "https://shop.example.vn";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    private final ProductService products = mock(ProductService.class);
    private MockMvc vietnamese;
    private MockMvc english;

    private MockMvc build(String language, String publicBaseUrl) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setTemplateEngineMessageSource(messages);
        ThymeleafViewResolver views = new ThymeleafViewResolver();
        views.setTemplateEngine(engine);
        views.setCharacterEncoding("UTF-8");
        views.setContentType("text/html;charset=UTF-8");
        return MockMvcBuilders
            .standaloneSetup(new StorefrontController(products, 14, CLOCK), new SeoController(products, publicBaseUrl),
                new WorkspaceController(mock(com.ebike.config.order.VnPayProperties.class), messages))
            .setControllerAdvice(new SeoModelAdvice(publicBaseUrl))
            .setViewResolvers(views)
            .setLocaleResolver(new FixedLocaleResolver(Locale.forLanguageTag(language)))
            .build();
    }

    private static ProductDetailDto detail(String name, Integer stock, BigDecimal rating, Integer reviews) {
        return new ProductDetailDto(7L, name, "kinetic-x", "Xe tay ga điện êm ái, đi xa.", new BigDecimal("25990000"), new BigDecimal("23990000"), rating, reviews, stock, false,
            new CategorySummaryDto(1, "Xe tay ga điện", "xe-tay-ga"),
            new ProductSpecificationDto("X1", "Kinetic", "E_SCOOTER", "LFP", null, new BigDecimal("45"), new BigDecimal("90"), 1500, null, null, 24, false),
            List.of(new ProductVariantDto(5L, "KX-RED", "Đỏ", "Đỏ", "#f00", "/images/catalog/x-red.jpg", null, BigDecimal.ZERO, 3, true, List.of("/images/catalog/x-red.jpg"))),
            List.of("/images/catalog/x-1.jpg", "/images/catalog/x-2.jpg"));
    }

    private static ProductSummaryDto summary(String slug) {
        return new ProductSummaryDto(7L, "Kinetic X", slug, "d", new BigDecimal("25990000"), null, null, 0, 3, false,
            new CategorySummaryDto(1, "Xe tay ga điện", "xe-tay-ga"), List.of("/images/catalog/x-1.jpg"));
    }

    @BeforeEach
    void setUp() {
        vietnamese = build("vi", BASE);
        english = build("en", BASE);
        when(products.getProductBySlug("kinetic-x")).thenReturn(detail("Kinetic X", 3, new BigDecimal("4.5"), 8));
        when(products.getRelatedProducts(anyString(), anyInt())).thenReturn(List.of());
        when(products.getProducts(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of(summary("kinetic-x"), summary("kinetic-y")));
        when(products.getFilterOptions()).thenReturn(new ProductFilterOptionsDto(List.of("Kinetic"), List.of("E_SCOOTER"), List.of("LFP")));
    }

    private static String content(MockMvc mvc, String path) throws Exception {
        return mvc.perform(get(path)).andReturn().getResponse().getContentAsString();
    }

    @Test
    void theProductPageNamesItsCanonicalAddressImageAndPrice() throws Exception {
        String html = content(vietnamese, "/bike/kinetic-x");
        assertTrue(html.contains("<link rel=\"canonical\" href=\"" + BASE + "/product/kinetic-x\">"), "one canonical address for every alias");
        assertTrue(html.contains("<meta property=\"og:type\" content=\"product\">"));
        assertTrue(html.contains("<meta property=\"og:title\" content=\"Kinetic X | KINETIC\">"));
        assertTrue(html.contains("<meta property=\"og:image\" content=\"" + BASE + "/images/catalog/x-1.jpg\">"));
        assertTrue(html.contains("<meta property=\"og:url\" content=\"" + BASE + "/product/kinetic-x\">"));
        assertTrue(html.contains("<meta property=\"og:description\" content=\"Xe tay ga điện êm ái, đi xa.\">"));
        assertTrue(html.contains("<meta property=\"product:price:amount\" content=\"23990000\">"));
        assertTrue(html.contains("<meta property=\"product:price:currency\" content=\"VND\">"));
        assertTrue(html.contains("<meta property=\"product:availability\" content=\"instock\">"));
        assertTrue(html.contains("<meta property=\"og:locale\" content=\"vi_VN\">"));
        assertTrue(html.contains("<meta name=\"twitter:card\" content=\"summary_large_image\">"));
        assertFalse(html.contains("noindex"), "a product page is for search engines");
    }

    @Test
    void theProductPageCarriesStructuredDataThatSurvivesTheTemplate() throws Exception {
        String html = content(vietnamese, "/product/kinetic-x");
        int start = html.indexOf("<script type=\"application/ld+json\">");
        assertTrue(start > 0, "JSON-LD present");
        String block = html.substring(start + "<script type=\"application/ld+json\">".length(), html.indexOf("</script>", start));
        com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(block);
        assertEquals("Product", node.path("@type").asText());
        assertEquals("23990000", node.path("offers").path("price").asText());
        assertEquals("https://schema.org/InStock", node.path("offers").path("availability").asText());
        assertEquals(8, node.path("aggregateRating").path("reviewCount").asInt());
        assertTrue(html.contains("\"@type\":\"BreadcrumbList\""));
        assertTrue(html.contains("Trang chủ"), "breadcrumb is written in the page language");
    }

    @Test
    void aSoldOutProductSaysOutOfStock() throws Exception {
        when(products.getProductBySlug("kinetic-x")).thenReturn(detail("Kinetic X", 0, null, 0));
        String html = content(vietnamese, "/product/kinetic-x");
        assertTrue(html.contains("<meta property=\"product:availability\" content=\"oos\">"));
        assertTrue(html.contains("https://schema.org/OutOfStock"));
        assertFalse(html.contains("AggregateRating"), "no reviews, no rating");
    }

    @Test
    void aProductNameCannotBreakOutOfThePageHead() throws Exception {
        when(products.getProductBySlug("kinetic-x")).thenReturn(detail("</script><script>alert(1)</script>\"", 3, null, 0));
        String html = content(vietnamese, "/product/kinetic-x");
        assertFalse(html.contains("<script>alert(1)"), "the name is escaped in the tags and the JSON");
        assertFalse(html.contains("content=\"</script>"), html.substring(0, Math.min(html.length(), 400)));
    }

    @Test
    void theHomePageIsIndexedUnlessItShowsASearch() throws Exception {
        String home = content(vietnamese, "/");
        assertTrue(home.contains("<link rel=\"canonical\" href=\"" + BASE + "/\">"));
        assertTrue(home.contains("<meta property=\"og:type\" content=\"website\">"));
        assertTrue(home.contains("<meta property=\"og:image\" content=\"" + BASE + "/images/catalog/x-1.jpg\">"));
        assertFalse(home.contains("noindex"));
        assertTrue(content(vietnamese, "/?q=xe").contains("<meta name=\"robots\" content=\"noindex,follow\">"));
        String blank = vietnamese.perform(get("/").param("q", "  ")).andReturn().getResponse().getContentAsString();
        assertFalse(blank.contains("noindex"), "a blank search is the plain page");
    }

    @Test
    void theCatalogueIsIndexedOnlyInItsPlainForm() throws Exception {
        String plain = content(vietnamese, "/products");
        assertTrue(plain.contains("<link rel=\"canonical\" href=\"" + BASE + "/products\">"));
        assertFalse(plain.contains("noindex"));
        for (String narrowed : List.of("/products?q=xe", "/products?brand=Kinetic", "/products?inStock=true", "/products?sort=price-asc", "/products?minPrice=100", "/products?categoryId=1")) {
            String html = content(vietnamese, narrowed);
            assertTrue(html.contains("<meta name=\"robots\" content=\"noindex,follow\">"), narrowed);
            assertTrue(html.contains("<link rel=\"canonical\" href=\"" + BASE + "/products\">"), narrowed + " points at the plain catalogue");
        }
    }

    @Test
    void theSitemapAndRobotsAreServedWithTheRightTypesAndAShortCache() throws Exception {
        var sitemap = vietnamese.perform(get("/sitemap.xml")).andReturn().getResponse();
        assertEquals(200, sitemap.getStatus());
        assertTrue(sitemap.getContentType().startsWith("application/xml"), sitemap.getContentType());
        assertTrue(sitemap.getHeader(HttpHeaders.CACHE_CONTROL).contains("max-age=600"));
        String xml = sitemap.getContentAsString();
        assertTrue(xml.contains("<loc>" + BASE + "/product/kinetic-x</loc>"));
        assertTrue(xml.contains("<loc>" + BASE + "/product/kinetic-y</loc>"));
        var robots = vietnamese.perform(get("/robots.txt")).andReturn().getResponse();
        assertEquals(200, robots.getStatus());
        assertTrue(robots.getContentType().startsWith("text/plain"), robots.getContentType());
        assertTrue(robots.getContentAsString().contains("Sitemap: " + BASE + "/sitemap.xml"));
    }

    @Test
    void withoutAConfiguredAddressTheAddressTheVisitorUsedIsTheBase() throws Exception {
        MockMvc unconfigured = build("vi", "");
        String robots = unconfigured.perform(get("/robots.txt").with(r -> { r.setScheme("https"); r.setServerName("kinetic.example"); r.setServerPort(443); return r; })).andReturn().getResponse().getContentAsString();
        assertTrue(robots.contains("Sitemap: https://kinetic.example/sitemap.xml"), robots);
        String local = unconfigured.perform(get("/robots.txt")).andReturn().getResponse().getContentAsString();
        assertTrue(local.contains("Sitemap: http://localhost/sitemap.xml"), local);
    }

    @Test
    void theLocalDefaultCountsAsNotSetSoTheVisitorsAddressIsUsed() throws Exception {
        MockMvc defaulted = build("vi", "http://localhost:8080");
        String robots = defaulted.perform(get("/robots.txt").with(r -> { r.setScheme("https"); r.setServerName("kinetic.example"); r.setServerPort(443); return r; })).andReturn().getResponse().getContentAsString();
        assertTrue(robots.contains("Sitemap: https://kinetic.example/sitemap.xml"), robots);
    }

    @Test
    void aPageBuiltFromTheHostHeaderIsCachedPerHostOnly() throws Exception {
        var open = build("vi", "").perform(get("/robots.txt")).andReturn().getResponse();
        assertTrue(open.getHeaders(HttpHeaders.VARY).contains("Host"), "no configured address: cache per Host");
        var fixed = vietnamese.perform(get("/robots.txt")).andReturn().getResponse();
        assertTrue(fixed.getHeaders(HttpHeaders.VARY).isEmpty(), "configured address: nothing depends on Host");
    }

    @Test
    void privatePagesCarryNoindexAndSupportAndWarrantyCarryShareTags() throws Exception {
        for (String path : List.of("/login", "/auth", "/signup", "/cart", "/checkout", "/checkout/success", "/payment/return", "/favorites", "/compare", "/chatbot", "/customer", "/manager", "/admin")) {
            String html = content(vietnamese, path);
            assertTrue(html.contains("<meta name=\"robots\" content=\"noindex"), path + " is marked noindex");
        }
        for (String path : List.of("/support", "/warranty")) {
            String html = content(vietnamese, path);
            assertFalse(html.contains("noindex"), path + " is for search engines");
            assertTrue(html.contains("<link rel=\"canonical\" href=\"" + BASE + path + "\">"), path);
            assertTrue(html.contains("<meta name=\"description\" content=\""), path);
            assertTrue(html.contains("<meta property=\"og:title\""), path);
            assertTrue(html.contains("<meta property=\"og:url\" content=\"" + BASE + path + "\">"), path);
            assertTrue(html.contains("<meta property=\"og:image\" content=\"" + BASE + "/images/og-default.png\">"), path);
        }
    }

    @Test
    void laterCataloguePagesGetTheirOwnTitleAndDescription() throws Exception {
        List<ProductSummaryDto> many = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            many.add(summary("kinetic-" + i));
        }
        when(products.getProducts(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(many);
        String first = content(vietnamese, "/products");
        assertTrue(first.contains("<meta property=\"og:title\" content=\"Bộ sưu tập xe | KINETIC\">"));
        String second = content(vietnamese, "/products?page=2");
        assertTrue(second.contains("<meta property=\"og:title\" content=\"Bộ sưu tập xe – trang 2 | KINETIC\">"), second);
        assertTrue(second.contains("<title>Bộ sưu tập xe – trang 2 | KINETIC</title>"));
        assertTrue(second.contains("<meta name=\"description\" content=\"Trang 2. Toàn bộ mẫu xe"));
    }
}
