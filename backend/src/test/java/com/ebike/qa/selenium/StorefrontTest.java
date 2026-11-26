package com.ebike.qa.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.qa.selenium.pages.CatalogPage;
import com.ebike.qa.selenium.pages.HomePage;
import com.ebike.qa.selenium.pages.Page;
import com.ebike.qa.selenium.pages.ProductPage;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

@EnabledIfSystemProperty(named = "qa.baseUrl", matches = ".+")
class StorefrontTest extends QaBase {
    private static final String SLUG = Shop.SHOW; // 6 colours, rideable, needs registration; no test buys it
    private final Api api = new Api();

    private int totalProducts() { return api.get("/api/v1/products").json().size(); }

    // ---------------------------------------------------------------- home

    @Test
    void home_showsEverySection() {
        HomePage home = new HomePage(ui).open();
        assertTrue(ui.text(By.cssSelector(".hero h1")).contains("Khai phá kỷ nguyên"));
        assertTrue(ui.textOf(By.cssSelector("section.intro h2")).contains("Chuyển động"));
        List<WebElement> featured = ui.all(HomePage.FEATURED);
        assertTrue(featured.size() >= 1 && featured.size() <= 4, "featured cards: " + featured.size());
        assertTrue(ui.all(HomePage.CATALOG_CARDS).size() >= 1 && ui.all(HomePage.CATALOG_CARDS).size() <= 4);
        assertEquals(3, ui.all(HomePage.PRINCIPLES).size());
        assertTrue(ui.exists(By.cssSelector("section.manifesto a.button")));
        assertTrue(ui.exists(By.cssSelector("footer#contact")));
        assertEquals(totalProducts() + " mẫu xe", ui.text(HomePage.CATALOG_COUNT), "the catalogue counter must equal the API's product count");
        for (WebElement card : featured) {
            assertFalse(card.findElement(By.cssSelector("h3")).getDomProperty("textContent").isBlank());
            assertTrue(Page.money(card.findElement(By.cssSelector(".price")).getDomProperty("textContent")) > 0);
        }
        assertEquals("0", home.cartBadge());
    }

    @Test
    void home_navigationLinks() {
        new HomePage(ui).open();
        List<String> hrefs = new ArrayList<>();
        for (WebElement a : ui.all(HomePage.NAV)) hrefs.add(a.getDomAttribute("href"));
        assertEquals(List.of("/shop", "#technology", "#contact", "/warranty"), hrefs);
        ui.click(By.cssSelector("header.top nav a[href='/warranty']"));
        ui.waitUrlContains("/warranty");
        assertTrue(ui.text(By.cssSelector("h1")).contains("Tra cứu bảo hành"));
        ui.open("/");
        ui.click(By.cssSelector("a.login"));
        ui.waitUrlContains("/login");
        ui.open("/");
        ui.click(By.cssSelector("a.register-link"));
        ui.waitUrlContains("/signup");
    }

    @Test
    void home_searchFiltersTheShowcase() {
        HomePage home = new HomePage(ui).open();
        home.search("Amy");
        ui.waitUrlContains("q=Amy");
        assertEquals("Amy", ui.prop(HomePage.SEARCH, "value"));
        List<WebElement> cards = ui.all(HomePage.FEATURED);
        assertFalse(cards.isEmpty());
        for (WebElement c : cards) assertTrue(c.findElement(By.cssSelector("h3")).getDomProperty("textContent").contains("Amy"));
    }

    @Test
    void home_newsletterAcceptsEmailAndRefusesGarbage() {
        new HomePage(ui).open();
        ui.type(HomePage.NEWSLETTER_EMAIL, "abc");
        ui.click(By.cssSelector("#newsletter button.button"));
        assertEquals("", ui.textOf(HomePage.NEWSLETTER_MESSAGE), "an invalid email must not be accepted");
        ui.type(HomePage.NEWSLETTER_EMAIL, "qa.news@example.com");
        ui.click(By.cssSelector("#newsletter button.button"));
        ui.waitText(HomePage.NEWSLETTER_MESSAGE, "Cảm ơn bạn đã đăng ký nhận tin.");
    }

    @Test
    void home_productCardOpensDetailPage() {
        new HomePage(ui).open();
        WebElement first = ui.all(HomePage.FEATURED).get(0);
        String name = first.findElement(By.cssSelector("h3")).getDomProperty("textContent").trim();
        ui.click(first.findElement(By.cssSelector("a.product-image")));
        ui.waitUrlContains("/bike/");
        assertEquals(name, ui.text(By.cssSelector("h1")));
    }

    // ---------------------------------------------------------------- catalogue

    @Test
    void catalogue_firstPageHasNineCardsAndCountsMatchTheApi() {
        CatalogPage c = new CatalogPage(ui).open();
        int total = totalProducts();
        assertEquals(total, c.productCount());
        assertEquals(1, c.page());
        assertEquals((int) Math.ceil(total / 9.0), c.pageCount());
        assertEquals(Math.min(9, total), c.cardsShown());
    }

    @Test
    void catalogue_searchByName() {
        CatalogPage c = new CatalogPage(ui).open();
        ui.type(CatalogPage.FILTER_Q, "Amy");
        c.apply();
        ui.waitUrlContains("q=Amy");
        assertTrue(c.productCount() >= 1);
        for (String n : c.names()) assertTrue(n.toLowerCase().contains("amy"), n);
    }

    @Test
    void catalogue_searchWithoutResultShowsEmptyMessage() {
        CatalogPage c = new CatalogPage(ui).open("q=zzzzqqqq");
        assertEquals(0, c.productCount());
        assertEquals(0, c.cardsShown());
        assertTrue(ui.text(CatalogPage.EMPTY).contains("Không tìm thấy mẫu xe phù hợp"));
    }

    @Test
    void catalogue_categoryFilterMatchesApi() {
        JsonNode all = api.get("/api/v1/products").json();
        long inCategory1 = 0;
        for (JsonNode p : all) if (p.get("category").get("id").asInt() == 1) inCategory1++;
        CatalogPage c = new CatalogPage(ui).open();
        c.setFilter("categoryId", "1");
        c.apply();
        ui.waitUrlContains("categoryId=1");
        assertEquals(inCategory1, c.productCount());
    }

    @Test
    void catalogue_priceRangeFilters() {
        CatalogPage c = new CatalogPage(ui).open();
        ui.selectValue(By.id("priceFilter"), "under20");
        c.apply();
        ui.waitUrlContains("maxPrice=20000000");
        assertTrue(c.cardsShown() > 0);
        for (long p : c.prices()) assertTrue(p <= 20_000_000L, "price above 20M: " + p);
        assertEquals("under20", ui.prop(By.id("priceFilter"), "value"), "the chosen range must stay selected after reload");

        c.open();
        ui.selectValue(By.id("priceFilter"), "20-50");
        c.apply();
        ui.waitUrlContains("minPrice=20000000");
        for (long p : c.prices()) assertTrue(p >= 20_000_000L && p <= 50_000_000L, "price outside 20-50M: " + p);

        c.open();
        ui.selectValue(By.id("priceFilter"), "over50");
        c.apply();
        ui.waitUrlContains("minPrice=50000000");
        for (long p : c.prices()) assertTrue(p >= 50_000_000L);
    }

    @Test
    void catalogue_sortByPrice() {
        CatalogPage c = new CatalogPage(ui).open();
        c.setFilter("sort", "price-asc");
        c.apply();
        ui.waitUrlContains("sort=price-asc");
        List<Long> asc = c.prices();
        List<Long> sorted = new ArrayList<>(asc);
        sorted.sort(Comparator.naturalOrder());
        assertEquals(sorted, asc, "price-asc");
        c.open("sort=price-desc");
        List<Long> desc = c.prices();
        List<Long> sortedDesc = new ArrayList<>(desc);
        sortedDesc.sort(Comparator.reverseOrder());
        assertEquals(sortedDesc, desc, "price-desc");
        assertEquals("price-desc", ui.prop(By.cssSelector(".filter-form select[name=sort]"), "value"));
    }

    @Test
    void catalogue_sortByNameAscending() {
        CatalogPage c = new CatalogPage(ui).open("sort=name-asc");
        List<String> names = c.names();
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        assertEquals(sorted, names);
    }

    @Test
    void catalogue_pagingAndClamping() {
        int total = totalProducts();
        int pages = (int) Math.ceil(total / 9.0);
        CatalogPage c = new CatalogPage(ui).open();
        assertTrue(pages > 1, "the QA data should span several pages");
        ui.click(By.cssSelector("nav.pagination a[href*='page=2']"));
        ui.waitUrlContains("page=2");
        assertEquals(2, c.page());
        assertEquals(Math.min(9, total - 9), c.cardsShown());
        ui.click(By.xpath("//nav[@class='pagination']//a[contains(.,'Tiếp')]"));
        ui.waitUrlContains("page=3");
        c.open("page=" + pages);
        assertEquals(total - 9 * (pages - 1), c.cardsShown(), "last page holds the remainder");
        assertFalse(ui.exists(By.xpath("//nav[@class='pagination']//a[contains(.,'Tiếp')]")), "no next link on the last page");
        c.open("page=999");
        assertEquals(pages, c.page(), "a page past the end is clamped to the last page");
        c.open("page=0");
        assertEquals(1, c.page(), "page 0 is clamped to 1");
        c.open("page=-7");
        assertEquals(1, c.page(), "a negative page is clamped to 1");
    }

    @Test
    void catalogue_pagingKeepsTheFilters() {
        CatalogPage c = new CatalogPage(ui).open("sort=price-desc");
        ui.click(By.cssSelector("nav.pagination a[href*='page=2']"));
        ui.waitUrlContains("page=2");
        assertTrue(ui.url().contains("sort=price-desc"), ui.url());
    }

    @Test
    void catalogue_inStockAndRangeFilters() {
        CatalogPage c = new CatalogPage(ui).open("inStock=true");
        assertTrue(c.productCount() >= 1);
        assertTrue(ui.present(By.cssSelector("input[name=inStock]")).isSelected());
        c.open("minRangeKm=99999");
        assertEquals(0, c.productCount(), "no bike has a 99999 km range");
        c.open("maxRangeKm=0");
        assertTrue(c.productCount() <= totalProducts());
    }

    @Test
    void catalogue_clearFiltersLinkResets() {
        CatalogPage c = new CatalogPage(ui).open("q=Amy&sort=price-asc");
        ui.click(By.cssSelector(".filter-title a"));
        ui.until(d -> d.getCurrentUrl().endsWith("/products"));
        assertEquals(totalProducts(), c.productCount());
    }

    @Test
    void catalogue_compareButtonStoresTheBike() {
        new CatalogPage(ui).open();
        WebElement button = ui.all(By.cssSelector(".compare-button")).get(0);
        String slug = button.getDomAttribute("data-slug");
        ui.click(button);
        assertEquals("Đã thêm ✓", button.getText().trim());
        String stored = String.valueOf(ui.js("return localStorage.getItem('kinetic_compare');"));
        assertTrue(stored.contains(slug), stored);
        ui.open("/compare");
        ui.waitText(By.cssSelector(".compare-table"), slug.isEmpty() ? "" : "Giá bán");
    }

    // ---------------------------------------------------------------- product page

    @Test
    void product_pageShowsNamePriceAndColours() {
        JsonNode p = api.product(SLUG);
        ProductPage page = new ProductPage(ui).open(SLUG);
        assertEquals(p.get("name").asText(), ui.text(By.cssSelector("h1")));
        assertEquals(Math.round(p.get("price").asDouble()), Page.money(ui.text(ProductPage.PRICE)));
        assertEquals(p.get("variants").size(), page.variants().size());
        assertTrue(ui.text(By.cssSelector(".specs")).contains("km/h"));
        assertTrue(ui.exists(By.cssSelector(".registration-required")), "this bike needs registration and says so");
    }

    @Test
    void product_switchingColourChangesNamePhotoAndStockHint() {
        ProductPage page = new ProductPage(ui).open(SLUG);
        String firstColour = ui.text(ProductPage.SELECTED_COLOR);
        String firstImage = ui.attr(ProductPage.MAIN_IMAGE, "src");
        page.pickVariant(2);
        String second = page.variants().get(2).getDomAttribute("data-color");
        assertNotEquals(firstColour, ui.text(ProductPage.SELECTED_COLOR));
        assertEquals(second, ui.text(ProductPage.SELECTED_COLOR));
        assertNotEquals(firstImage, ui.attr(ProductPage.MAIN_IMAGE, "src"));
        assertTrue(ui.text(ProductPage.CAPTION).startsWith(second), ui.text(ProductPage.CAPTION));
        assertTrue(ui.text(ProductPage.STOCK_HINT).startsWith("Còn "), ui.text(ProductPage.STOCK_HINT));
    }

    @Test
    void product_quantityIsClampedBetweenOneAndStock() {
        ProductPage page = new ProductPage(ui).open(SLUG);
        int max = Math.min(10, Integer.parseInt(ui.attr(ProductPage.QTY, "max")));
        assertTrue(max >= 2);
        assertEquals("1", page.quantity());
        assertFalse(ui.visible(ProductPage.MINUS).isEnabled(), "minus is disabled at 1");
        page.setQuantity("99");
        assertEquals(String.valueOf(max), page.quantity(), "over the limit is clamped down");
        assertFalse(ui.visible(ProductPage.PLUS).isEnabled(), "plus is disabled at the maximum");
        page.setQuantity("0");
        assertEquals("1", page.quantity(), "zero is clamped up to 1");
        page.setQuantity("-3");
        assertEquals("1", page.quantity());
        ui.click(ProductPage.PLUS);
        assertEquals("2", page.quantity());
        ui.click(ProductPage.MINUS);
        assertEquals("1", page.quantity());
        assertTrue(ui.text(ProductPage.STOCK_HINT).contains("Tối đa " + max));
    }

    @Test
    void product_installmentCalculatorMatchesTheFlatRateFormula() {
        ProductPage page = new ProductPage(ui).open(SLUG);
        long price = Page.money(ui.text(ProductPage.PRICE));
        By down = By.cssSelector("#installmentBox [name=down]");
        ui.visible(By.id("installmentBox"));
        JsonNode cfg = api.get("/api/v1/installments/config").json();
        JsonNode term = cfg.get("terms").get(0);
        int months = term.get("termMonths").asInt();
        double rate = term.get("annualRatePercent").asDouble();
        ui.setValue(down, "50");
        ui.click(By.cssSelector("#installmentBox input[name=term][value='" + months + "']"));
        long downPay = Math.round(price * 50 / 100.0);
        long financed = price - downPay;
        long interest = Math.round(financed * rate * months / 1200.0);
        long monthly = (long) Math.ceil((financed + interest) / (double) months);
        By monthlyField = By.cssSelector("#installmentBox [data-field=monthly]");
        ui.until(d -> { try { return Page.money(ui.textOf(monthlyField)) == monthly; } catch (RuntimeException e) { return false; } });
        assertEquals(downPay, Page.money(ui.text(By.cssSelector("#installmentBox [data-field=down]"))));
        assertEquals(downPay + monthly * months, Page.money(ui.text(By.cssSelector("#installmentBox [data-field=total]"))));
        assertTrue(ui.text(By.cssSelector("#installmentBox")).contains("công cụ mô phỏng"));
        // a bigger quantity raises the financed amount
        page.setQuantity("2");
        long price2 = price * 2;
        long down2 = Math.round(price2 * 50 / 100.0);
        long fin2 = price2 - down2;
        long int2 = Math.round(fin2 * rate * months / 1200.0);
        long monthly2 = (long) Math.ceil((fin2 + int2) / (double) months);
        ui.until(d -> { try { return Page.money(ui.textOf(monthlyField)) == monthly2; } catch (RuntimeException e) { return false; } });
    }

    @Test
    void product_savingsCalculatorMatchesTheApi() {
        new ProductPage(ui).open(SLUG);
        By saving = By.cssSelector("#savingsBox [data-field=monthly-saving]");
        ui.visible(By.id("savingsBox"));
        JsonNode r = api.get("/api/v1/products/" + SLUG + "/savings?kmPerDay=20&daysPerMonth=26").json();
        assertEquals(Math.round(r.get("monthlySaving").asDouble()), Page.money(ui.text(saving)));
        ui.type(By.cssSelector("#savingsBox [name=kmPerDay]"), "40");
        JsonNode r2 = api.get("/api/v1/products/" + SLUG + "/savings?kmPerDay=40&daysPerMonth=26").json();
        ui.until(d -> Page.money(ui.textOf(saving)) == Math.round(r2.get("monthlySaving").asDouble()));
        assertTrue(Math.round(r2.get("monthlySaving").asDouble()) > Math.round(r.get("monthlySaving").asDouble()), "more km, more saving");
        ui.type(By.cssSelector("#savingsBox [name=kmPerDay]"), "0");
        ui.waitText(By.cssSelector("#savingsBox [data-result]"), "Nhập số km mỗi ngày là số nguyên từ 1 đến");
    }

    @Test
    void product_compareAddsTheBikeAndListsIt() {
        new ProductPage(ui).open(SLUG);
        ui.click(ProductPage.COMPARE);
        ui.waitUrlContains("/compare");
        ui.waitText(By.cssSelector(".compare-table"), "Giá bán");
        assertTrue(ui.text(By.cssSelector(".compare-table thead")).contains("DIBAO Creer E"));
        ui.click(By.cssSelector("[data-remove-product]"));
        ui.waitText(By.cssSelector("#flow-extra"), "Chưa có mẫu xe để so sánh");
    }

    @Test
    void product_relatedBikesAreOtherBikes() {
        new ProductPage(ui).open(SLUG);
        List<WebElement> related = ui.all(ProductPage.RELATED);
        assertTrue(related.size() >= 1 && related.size() <= 4);
        for (WebElement r : related) {
            assertFalse(r.findElement(By.cssSelector("a.product-image")).getDomAttribute("href").endsWith("/" + SLUG));
        }
        ui.click(related.get(0).findElement(By.cssSelector("a.product-image")));
        ui.waitUrlContains("/product/");
        assertFalse(ui.url().endsWith("/" + SLUG));
    }

    @Test
    void product_addToCartShowsNoticeAndBadge() {
        ProductPage page = new ProductPage(ui).open(SLUG);
        page.setQuantity("2");
        page.addToCart();
        assertTrue(page.notice().startsWith("Đã thêm 2 xe"), page.notice());
        ui.until(d -> page.cartBadge().equals("2"));
    }

    @Test
    void product_unknownLinkedImagesLoad() {
        new ProductPage(ui).open(SLUG);
        Object broken = ui.js("return [...document.images].filter(i=>i.complete && i.naturalWidth===0).map(i=>i.src);");
        assertEquals("[]", String.valueOf(broken), "broken images: " + broken);
    }
}
