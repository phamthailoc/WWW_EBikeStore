package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.Dimension;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * V35: search-engine and link-preview markup. A visitor (no cookies) reads {@code /robots.txt} and {@code /sitemap.xml}; the bike page
 * names one canonical address, carries Open Graph tags and JSON-LD whose price and stock match what the page shows; private pages and
 * narrowed catalogue views ask not to be indexed; English pages say so.
 *
 * <pre>
 * mvn test -Dtest=SeoSeleniumTest -Dselenium.baseUrl=http://localhost:8080
 * </pre>
 */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class SeoSeleniumTest extends StorefrontSeleniumTest {

    private static final HttpClient VISITOR = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    @BeforeAll
    static void eraseTheCustomerWhenDone() {
        eraseCustomerWhenDone = true;
    }

    /** The base class signs a customer in; crawlers are anonymous, so the head checks start from a browser with no session. */
    @BeforeEach
    void browseAsAVisitor() {
        driver.manage().deleteAllCookies();
        js("try{localStorage.clear();sessionStorage.clear()}catch(e){}");
    }

    private static HttpResponse<String> visit(String path) throws Exception {
        return VISITOR.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String pathOf(String address) {
        return URI.create(address).getRawPath();
    }

    private String meta(String selector) {
        Object value = js("const e=document.querySelector(arguments[0]);return e?e.getAttribute('content'):null", selector);
        return value == null ? null : value.toString();
    }

    private String canonical() {
        Object value = js("const e=document.querySelector('link[rel=canonical]');return e?e.getAttribute('href'):null");
        return value == null ? null : value.toString();
    }

    private JsonNode jsonLd(String type) throws Exception {
        @SuppressWarnings("unchecked")
        List<String> blocks = (List<String>) js("return [...document.querySelectorAll('script[type=\"application/ld+json\"]')].map(s=>s.textContent)");
        for (String block : blocks) {
            JsonNode node = JSON.readTree(block);
            if (type.equals(node.path("@type").asText())) {
                return node;
            }
        }
        throw new AssertionError("no " + type + " JSON-LD on " + driver.getCurrentUrl());
    }

    @Test
    void aVisitorReadsRobotsTxtAndTheSitemapListsEveryProductOnSale() throws Exception {
        HttpResponse<String> robots = visit("/robots.txt");
        assertEquals(200, robots.statusCode(), "robots.txt needs no account");
        assertTrue(robots.headers().firstValue("Content-Type").orElse("").startsWith("text/plain"), robots.headers().toString());
        assertTrue(robots.body().contains("Disallow: /api/"), robots.body());
        assertFalse(robots.body().contains("Disallow: /admin"), "private pages stay crawlable so their noindex is seen");
        assertFalse(robots.body().contains("Disallow: /product"), "products stay open");
        assertTrue(robots.body().contains("Sitemap: "), robots.body());
        assertTrue(pathOf(robots.body().lines().filter(l -> l.startsWith("Sitemap: ")).findFirst().orElseThrow().substring(9).trim()).endsWith("/sitemap.xml"));

        HttpResponse<String> sitemap = visit("/sitemap.xml");
        assertEquals(200, sitemap.statusCode(), "the sitemap needs no account");
        assertTrue(sitemap.headers().firstValue("Content-Type").orElse("").contains("xml"), sitemap.headers().toString());
        Document xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(sitemap.body().getBytes(StandardCharsets.UTF_8)));
        NodeList locs = xml.getElementsByTagName("loc");
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < locs.getLength(); i++) {
            String loc = locs.item(i).getTextContent();
            assertTrue(loc.startsWith("http"), "absolute address: " + loc);
            paths.add(pathOf(loc));
        }
        for (String page : List.of("/", "/products", "/support", "/warranty")) {
            assertTrue(paths.contains(page), page + " in " + paths);
        }
        List<String> slugs = new ArrayList<>();
        for (JsonNode product : listOf(call(api, "GET", "/api/v1/products", null))) {
            slugs.add(product.path("slug").asText());
            assertTrue(paths.contains("/product/" + product.path("slug").asText()), product.path("slug").asText() + " is missing from the sitemap");
        }
        assertEquals(slugs.size() + 4, paths.size(), "the sitemap lists the pages and the products on sale, nothing else: " + paths);
    }

    @Test
    void theBikePageNamesOneAddressAndItsSharePreviewAndStructuredDataMatchThePage() throws Exception {
        driver.get(baseUrl + "/product/" + productSlug);
        String canonical = canonical();
        assertNotNull(canonical, "canonical link");
        assertEquals("/product/" + productSlug, pathOf(canonical));
        assertEquals(canonical, meta("meta[property='og:url']"));
        assertEquals("product", meta("meta[property='og:type']"));
        assertEquals("vi_VN", meta("meta[property='og:locale']"));
        String name = driver.findElement(org.openqa.selenium.By.cssSelector("main.detail h1")).getText();
        assertEquals(name + " | KINETIC", meta("meta[property='og:title']"));
        assertEquals(name + " | KINETIC", meta("meta[name='twitter:title']"));
        assertEquals("summary_large_image", meta("meta[name='twitter:card']"));
        String description = meta("meta[name='description']");
        assertTrue(description != null && !description.isBlank() && description.length() <= 160, "description: " + description);
        assertEquals(description, meta("meta[property='og:description']"));

        String image = meta("meta[property='og:image']");
        assertTrue(image != null && image.matches("https?://.+"), "the share image is an absolute address: " + image);
        HttpResponse<String> photo = VISITOR.send(HttpRequest.newBuilder(URI.create(image.contains("localhost") ? baseUrl + pathOf(image) : image)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, photo.statusCode(), "the share image can be fetched without signing in: " + image);

        String shownPrice = driver.findElement(org.openqa.selenium.By.id("productPage")).getAttribute("data-price");
        int stock = Integer.parseInt(driver.findElement(org.openqa.selenium.By.id("productPage")).getAttribute("data-stock"));
        assertEquals(new java.math.BigDecimal(shownPrice).setScale(0, java.math.RoundingMode.HALF_UP).toPlainString(), meta("meta[property='product:price:amount']"));
        assertEquals("VND", meta("meta[property='product:price:currency']"));
        assertEquals(stock > 0 ? "instock" : "oos", meta("meta[property='product:availability']"));

        JsonNode product = jsonLd("Product");
        assertEquals(name, product.path("name").asText());
        assertEquals(canonical, product.path("url").asText());
        assertEquals("VND", product.path("offers").path("priceCurrency").asText());
        assertEquals(meta("meta[property='product:price:amount']"), product.path("offers").path("price").asText(), "structured price equals the visible price");
        assertEquals(stock > 0 ? "https://schema.org/InStock" : "https://schema.org/OutOfStock", product.path("offers").path("availability").asText());
        assertTrue(product.path("image").size() >= 1);
        JsonNode apiProduct = call(api, "GET", "/api/v1/products/" + productSlug, null);
        int reviews = apiProduct.path("reviewCount").asInt();
        if (reviews > 0) {
            assertEquals(reviews, product.path("aggregateRating").path("reviewCount").asInt(), "rating comes from the real reviews");
        } else {
            assertTrue(product.path("aggregateRating").isMissingNode(), "no reviews, no invented rating");
        }
        JsonNode crumbs = jsonLd("BreadcrumbList");
        assertEquals(3, crumbs.path("itemListElement").size());
        assertEquals(name, crumbs.path("itemListElement").get(2).path("name").asText());
        assertEquals("Trang chủ", crumbs.path("itemListElement").get(0).path("name").asText());
        assertNull(meta("meta[name='robots']"), "a bike page may be indexed");
    }

    private static void assertNull(Object value, String message) {
        org.junit.jupiter.api.Assertions.assertNull(value, message);
    }

    @Test
    void everyAliasOfABikePagePointsAtTheSameCanonicalAddress() {
        String expected = null;
        for (String prefix : List.of("/product/", "/bike/", "/models/")) {
            driver.get(baseUrl + prefix + productSlug);
            String path = pathOf(canonical());
            assertEquals("/product/" + productSlug, path, prefix + " is an alias");
            expected = expected == null ? canonical() : expected;
            assertEquals(expected, canonical());
        }
    }

    @Test
    void thePlainPagesAreIndexedAndPrivateOrNarrowedOnesAskNotToBe() {
        for (String open : List.of("/", "/products", "/support", "/warranty")) {
            driver.get(baseUrl + open);
            assertNull(meta("meta[name='robots']"), open + " may be indexed");
            assertEquals(open, pathOf(canonical()).isEmpty() ? "/" : pathOf(canonical()), open + " is its own canonical address");
        }
        for (String closed : List.of("/login", "/signup", "/cart", "/checkout", "/favorites", "/compare", "/customer")) {
            driver.get(baseUrl + closed);
            Object robots = js("const e=document.querySelector('meta[name=robots]');return e?e.getAttribute('content'):null");
            assertTrue(robots != null && robots.toString().contains("noindex"), closed + " asks not to be indexed (robots=" + robots + ") " + driver.getCurrentUrl());
        }
        for (String narrowed : List.of("/products?q=xe", "/products?inStock=true", "/products?sort=price-asc", "/?q=xe")) {
            driver.get(baseUrl + narrowed);
            assertEquals("noindex,follow", meta("meta[name='robots']"), narrowed);
            String expected = narrowed.startsWith("/products") ? "/products" : "/";
            assertEquals(expected, pathOf(canonical()), narrowed + " points back at the plain page");
        }
    }

    @Test
    void anUnknownBikeIsNotFoundAndNotInTheSitemap() throws Exception {
        assertEquals(404, visit("/product/khong-co-xe-nay-" + System.currentTimeMillis()).statusCode());
        assertFalse(visit("/sitemap.xml").body().contains("khong-co-xe-nay"));
    }

    // The markup sits in <head> and cannot change the layout; a narrow-screen test here only judged the whole page, so it could fail for a reason
    // that has nothing to do with V35. The phone layout of the new box is tested in SavingsCalculatorSeleniumTest.
}
