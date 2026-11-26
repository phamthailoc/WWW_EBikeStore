package com.ebike.qa.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.qa.selenium.pages.CartPage;
import com.ebike.qa.selenium.pages.Page;
import com.ebike.qa.selenium.pages.ProductPage;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;

@EnabledIfSystemProperty(named = "qa.baseUrl", matches = ".+")
class CartTest extends QaBase {
    private static final String SLUG = Shop.SHOW;
    private static final String OTHER = Shop.OTHER;

    private long unitPrice(String slug) { return Math.round(new Api().product(slug).get("price").asDouble()); }

    @Test
    void emptyCartShowsInvitationAndBadgeZero() {
        CartPage cart = new CartPage(ui).open();
        assertTrue(cart.isEmpty());
        assertTrue(ui.text(CartPage.EMPTY).contains("Giỏ hàng đang trống"));
        assertEquals("0", cart.cartBadge());
        ui.click(By.cssSelector(".commerce-empty a.commerce-button"));
        ui.waitUrlContains("/products");
    }

    @Test
    void addOneBikeShowsLineWithNameColourAndTotal() {
        fillCart(SLUG, 1);
        CartPage cart = new CartPage(ui).open();
        assertEquals(1, cart.items().size());
        assertTrue(ui.textOf(By.cssSelector(".cart-item h3")).contains("Creer E"));
        assertEquals("1", cart.quantityOf(0));
        assertEquals(unitPrice(SLUG), Page.money(ui.text(CartPage.TOTAL)));
        assertEquals("1", cart.cartBadge());
        assertTrue(ui.text(By.cssSelector(".cart-item")).contains("Màu:"));
    }

    @Test
    void sameColourTwiceMergesIntoOneLine() {
        fillCart(SLUG, 1);
        fillCart(SLUG, 1);
        CartPage cart = new CartPage(ui).open();
        assertEquals(1, cart.items().size(), "same bike and colour must merge");
        assertEquals("2", cart.quantityOf(0));
        assertEquals(2 * unitPrice(SLUG), Page.money(ui.text(CartPage.TOTAL)));
        assertEquals("2", cart.cartBadge());
    }

    @Test
    void differentColoursAreSeparateLines() {
        ProductPage page = new ProductPage(ui).open(SLUG);
        page.pickVariant(0);
        page.addToCart();
        page.notice();
        page.pickVariant(1);
        page.addToCart();
        ui.until(d -> page.cartBadge().equals("2"));
        CartPage cart = new CartPage(ui).open();
        assertEquals(2, cart.items().size());
    }

    @Test
    void differentBikesAreSeparateLinesAndTotalsAdd() {
        fillCart(SLUG, 1);
        fillCart(OTHER, 1);
        CartPage cart = new CartPage(ui).open();
        assertEquals(2, cart.items().size());
        assertEquals(unitPrice(SLUG) + unitPrice(OTHER), Page.money(ui.text(CartPage.TOTAL)));
    }

    @Test
    void cannotAddMoreThanTheColourLimit() {
        ProductPage page = new ProductPage(ui).open(SLUG);
        int max = Integer.parseInt(ui.attr(ProductPage.QTY, "max"));
        assertTrue(max >= 1 && max <= 10, "limit is 10 per colour at most, got " + max);
        page.setQuantity(String.valueOf(max));
        page.addToCart();
        assertTrue(page.notice().startsWith("Đã thêm " + max + " xe"), page.notice());
        ui.js("document.querySelector('#productNotice').textContent='';");
        page.setQuantity("1");
        page.addToCart();
        ui.until(d -> ui.textOf(ProductPage.NOTICE).contains("Chỉ có thể mua tối đa"));
        assertTrue(ui.textOf(ProductPage.NOTICE).contains("Chỉ có thể mua tối đa " + max + " xe cho màu đã chọn."));
        assertEquals(String.valueOf(max), page.cartBadge(), "the refused bike must not enter the cart");
    }

    @Test
    void removeEmptiesTheCart() {
        fillCart(SLUG, 1);
        CartPage cart = new CartPage(ui).open();
        cart.remove(0);
        ui.until(d -> cart.isEmpty());
        assertEquals("0", cart.cartBadge());
        ui.open("/cart");
        ui.until(d -> ui.exists(CartPage.EMPTY));
    }

    @Test
    void plusMinusAndTypedQuantityUpdateTheTotal() {
        fillCart(SLUG, 1);
        CartPage cart = new CartPage(ui).open();
        long unit = unitPrice(SLUG);
        cart.plus(0);
        ui.until(d -> Page.money(ui.textOf(CartPage.TOTAL)) == 2 * unit);
        assertEquals("2", cart.quantityOf(0));
        assertEquals("2", cart.cartBadge());
        cart.minus(0);
        ui.until(d -> Page.money(ui.textOf(CartPage.TOTAL)) == unit);
        cart.setQuantity(0, "3");
        ui.until(d -> Page.money(ui.textOf(CartPage.TOTAL)) == 3 * unit);
        assertEquals("3", cart.cartBadge());
    }

    @Test
    void typedQuantityOutOfRangeIsRefused() {
        fillCart(SLUG, 1);
        CartPage cart = new CartPage(ui).open();
        cart.setQuantity(0, "50");
        ui.until(d -> ui.shown(By.id("notice")));
        assertTrue(ui.text(By.id("notice")).startsWith("Số lượng phải từ 1 đến"), ui.text(By.id("notice")));
        assertEquals("1", cart.quantityOf(0), "the line goes back to its old quantity");
        cart.setQuantity(0, "0");
        ui.until(d -> ui.textOf(By.id("notice")).startsWith("Số lượng phải từ 1 đến"));
        assertEquals("1", cart.quantityOf(0));
    }

    @Test
    void cartSurvivesReloadAndOtherPages() {
        fillCart(SLUG, 2);
        ui.open("/products");
        ui.until(d -> new CartPage(ui).cartBadge().equals("2"));
        CartPage cart = new CartPage(ui).open();
        assertEquals("2", cart.quantityOf(0));
        ui.d.navigate().refresh();
        ui.until(d -> ui.exists(CartPage.ITEMS));
        assertEquals("2", cart.quantityOf(0));
        ui.open("/");
        ui.until(d -> new CartPage(ui).cartBadge().equals("2"));
    }

    @Test
    void checkoutButtonLeadsToCheckout() {
        fillCart(SLUG, 1);
        new CartPage(ui).open();
        ui.click(CartPage.CHECKOUT);
        ui.waitUrlContains("/checkout");
        ui.visible(By.id("checkoutForm"));
    }

    @Test
    void checkoutWithEmptyCartShowsEmptyState() {
        ui.open("/checkout");
        ui.until(d -> ui.exists(CartPage.EMPTY));
        assertTrue(ui.text(CartPage.EMPTY).contains("Giỏ hàng đang trống"));
    }

    @Test
    void buyNowGoesStraightToCheckoutWithoutTouchingTheCart() {
        ProductPage page = new ProductPage(ui).open(SLUG);
        ui.click(ProductPage.BUY_NOW);
        ui.waitUrlContains("/checkout?buyNow=1");
        ui.visible(By.id("checkoutForm"));
        ui.open("/cart");
        ui.until(d -> ui.exists(CartPage.EMPTY));
        JsonNode apiCart = new Api().get("/api/v1/cart").json();
        assertEquals(0, apiCart.size());
    }
}
