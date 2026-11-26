package com.ebike.qa.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.qa.selenium.pages.CartPage;
import com.ebike.qa.selenium.pages.CheckoutPage;
import com.ebike.qa.selenium.pages.Page;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;

@EnabledIfSystemProperty(named = "qa.baseUrl", matches = ".+")
class CheckoutGuestTest extends QaBase {
    private static final String SLUG = Shop.buySlug();
    private static final String SAME_CITY = "Hồ Chí Minh";   // has showrooms: cheaper delivery
    private static final String OTHER_PROVINCE = "Hà Nội";
    private final Api api = new Api();

    private long price() { return Math.round(api.product(SLUG).get("price").asDouble()); }

    private CheckoutPage checkout(int quantity) {
        fillCart(SLUG, quantity);
        return new CheckoutPage(ui).open().waitQuote();
    }

    private String guestEmail() { return QaConfig.uniqueUsername() + "@example.com"; }

    private void fillValid(CheckoutPage c, String email) {
        c.contact("Nguyễn Văn Kiểm", "0901234567", email);
        c.home(SAME_CITY, "12 Nguyễn Trãi, Phường Bến Thành");
    }

    @Test
    void html5RequiredFieldsBlockAnEmptySubmit() {
        CheckoutPage c = checkout(1);
        for (By field : new By[] {CheckoutPage.NAME, CheckoutPage.PHONE, CheckoutPage.EMAIL, CheckoutPage.PROVINCE, CheckoutPage.ADDRESS}) {
            assertEquals("true", ui.prop(field, "required"), "required: " + field);
            assertEquals(Boolean.FALSE, ui.js("return arguments[0].checkValidity();", ui.present(field)), "empty must be invalid: " + field);
        }
        ui.js("document.querySelector('#checkoutForm').requestSubmit();");
        assertFalse(ui.shown(CheckoutPage.ERROR), "the browser blocks the submit before the server is asked");
        assertTrue(ui.url().contains("/checkout") && !ui.url().contains("success"));
    }

    @Test
    void html5PhoneAndEmailFormatsAreChecked() {
        CheckoutPage c = checkout(1);
        ui.type(CheckoutPage.PHONE, "12345");
        assertEquals(Boolean.TRUE, ui.js("return arguments[0].validity.patternMismatch;", ui.present(CheckoutPage.PHONE)));
        ui.type(CheckoutPage.PHONE, "0901234567");
        assertEquals(Boolean.TRUE, ui.js("return arguments[0].checkValidity();", ui.present(CheckoutPage.PHONE)));
        ui.type(CheckoutPage.EMAIL, "abc");
        assertEquals(Boolean.TRUE, ui.js("return arguments[0].validity.typeMismatch;", ui.present(CheckoutPage.EMAIL)));
        ui.type(CheckoutPage.ADDRESS, "ngắn");
        assertEquals(Boolean.TRUE, ui.js("return arguments[0].validity.tooShort;", ui.present(CheckoutPage.ADDRESS)));
    }

    @Test
    void serverRefusesNameWithDigits() {
        CheckoutPage c = checkout(1);
        fillValid(c, guestEmail());
        ui.type(CheckoutPage.NAME, "Nguyen Van 123");
        c.place();
        assertEquals("Họ và tên không được chứa số.", c.error());
    }

    @Test
    void serverRefusesBadPhone() {
        CheckoutPage c = checkout(1);
        fillValid(c, guestEmail());
        ui.type(CheckoutPage.PHONE, "0123");
        c.dropClientValidation();
        c.place();
        assertTrue(c.error().startsWith("Số điện thoại chưa hợp lệ."), c.error());
    }

    @Test
    void serverRefusesShortAddress() {
        CheckoutPage c = checkout(1);
        fillValid(c, guestEmail());
        ui.type(CheckoutPage.ADDRESS, "abc");
        c.dropClientValidation();
        c.place();
        assertTrue(c.error().startsWith("Địa chỉ cụ thể quá ngắn."), c.error());
    }

    @Test
    void homeDeliveryFeeDependsOnTheProvince() {
        CheckoutPage c = checkout(1);
        assertEquals("Chọn tỉnh để tính phí", c.quote("Phí giao xe"), "no province yet");
        ui.selectValue(CheckoutPage.PROVINCE, SAME_CITY);
        ui.until(d -> c.quoteHas("Phí giao xe", "200.000"));
        assertEquals(price() + 200_000, c.quoteMoney("Tổng thanh toán"));
        ui.selectValue(CheckoutPage.PROVINCE, OTHER_PROVINCE);
        ui.until(d -> c.quoteHas("Phí giao xe", "800.000"));
        assertEquals(price() + 800_000, c.quoteMoney("Tổng thanh toán"));
    }

    @Test
    void validPromoEbike100TakesOffOneHundredThousand() {
        CheckoutPage c = checkout(1);
        ui.selectValue(CheckoutPage.PROVINCE, SAME_CITY);
        ui.until(d -> c.quoteHas("Phí giao xe", "200.000"));
        long before = c.quoteMoney("Tổng thanh toán");
        c.applyPromo("EBIKE100");
        ui.until(d -> c.quoteHas("Giảm giá", "100.000"));
        assertTrue(c.quote("Giảm giá").startsWith("-"), c.quote("Giảm giá"));
        assertEquals(before - 100_000, c.quoteMoney("Tổng thanh toán"));
        assertTrue(ui.text(CheckoutPage.QUOTE).contains("Đã áp dụng mã EBIKE100."));
    }

    @Test
    void expiredPromoKinetic20IsRefused() {
        CheckoutPage c = checkout(1);
        c.applyPromo("KINETIC20");
        ui.until(d -> ui.exists(By.cssSelector("#checkoutQuote .commerce-error")));
        assertEquals("Mã giảm giá không hợp lệ hoặc đã hết hạn.", ui.text(By.cssSelector("#checkoutQuote .commerce-error")));
    }

    @Test
    void waitingPromoWeekendIsRefused() {
        CheckoutPage c = checkout(1);
        c.applyPromo("WEEKEND");
        ui.until(d -> ui.exists(By.cssSelector("#checkoutQuote .commerce-error")));
        assertEquals("Mã giảm giá không hợp lệ hoặc đã hết hạn.", ui.text(By.cssSelector("#checkoutQuote .commerce-error")));
    }

    @Test
    void storePickupHasNoDeliveryFeeAndOffersRegistrationService() {
        CheckoutPage c = checkout(2);
        long subtotal = 2 * price();
        c.pickup();
        ui.until(d -> c.quoteHas("Phí giao xe", "0"));
        assertEquals(0, c.quoteMoney("Phí giao xe"));
        assertTrue(ui.shown(By.cssSelector(".registration-option")), "pickup of a bike that needs a plate offers the service");
        assertTrue(ui.text(By.id("registrationServicePrice")).contains("500.000"), ui.text(By.id("registrationServicePrice")));
        ui.click(By.cssSelector(".registration-option"));
        ui.until(d -> c.quoteIs("Phí hồ sơ", 1_000_000));
        assertEquals(subtotal + 1_000_000, c.quoteMoney("Tổng thanh toán"), "500,000 per bike for two bikes");
        ui.click(By.cssSelector(".registration-option"));
        ui.until(d -> c.quoteIs("Phí hồ sơ", 0));
        assertEquals(subtotal, c.quoteMoney("Tổng thanh toán"));
    }

    @Test
    void homeDeliveryDoesNotOfferTheRegistrationService() {
        CheckoutPage c = checkout(1);
        ui.selectValue(CheckoutPage.PROVINCE, SAME_CITY);
        ui.until(d -> c.quoteHas("Phí giao xe", "200.000"));
        assertFalse(ui.shown(By.cssSelector(".registration-option")));
        assertTrue(ui.shown(By.id("registrationNote")), "but the plate note is shown");
    }

    @Test
    void pickupRequiresChoosingAStore() {
        CheckoutPage c = checkout(1);
        c.contact("Nguyễn Văn Kiểm", "0901234567", guestEmail());
        ui.jsClick(By.cssSelector("input[name=deliveryMethod][value=STORE_PICKUP]"));
        ui.visible(By.cssSelector(".showroom-option"));
        c.place();
        assertEquals(Boolean.TRUE, ui.js("return document.querySelector('input[name=pickupShowroomId]').validity.valueMissing;"), "a store must be chosen: the browser blocks the submit");
        assertTrue(ui.url().contains("/checkout") && !ui.url().contains("success"));
    }

    @Test
    void guestPlacesHomeDeliveryOrderAndCartIsEmptied() {
        CheckoutPage c = checkout(1);
        String email = guestEmail();
        fillValid(c, email);
        ui.until(d -> c.quoteHas("Phí giao xe", "200.000"));
        long total = c.quoteMoney("Tổng thanh toán");
        c.place();
        ui.waitUrlContains("/checkout/success");
        assertEquals("Đặt hàng thành công", ui.text(By.cssSelector(".order-success h2")));
        assertTrue(ui.text(By.cssSelector(".order-number")).matches(".*\\d.*"));
        assertTrue(ui.text(By.cssSelector(".order-success-details")).contains("Giao hàng tại nhà"));
        assertEquals(total, Page.money(ui.text(By.xpath("//p[span[contains(.,'Tổng thanh toán')]]/strong"))));
        assertTrue(ui.text(By.cssSelector(".order-success")).contains("Đơn hàng đang chờ xác nhận."));
        ui.open("/cart");
        ui.until(d -> ui.exists(CartPage.EMPTY));
        assertEquals("0", new CartPage(ui).cartBadge());
        JsonNode cartOnServer = api.get("/api/v1/cart").json();
        assertEquals(0, cartOnServer.size());
    }

    @Test
    void guestPlacesPickupOrderWithRegistrationService() {
        CheckoutPage c = checkout(1);
        c.contact("Trần Thị Nhận", "0912345678", guestEmail());
        c.pickup();
        ui.click(By.cssSelector(".registration-option"));
        ui.until(d -> c.quoteIs("Phí hồ sơ", 500_000));
        long total = c.quoteMoney("Tổng thanh toán");
        assertEquals(price() + 500_000, total);
        c.place();
        ui.waitUrlContains("/checkout/success");
        assertTrue(ui.text(By.cssSelector(".order-success-details")).contains("Nhận tại cửa hàng"));
        assertEquals(total, Page.money(ui.text(By.xpath("//p[span[contains(.,'Tổng thanh toán')]]/strong"))));
    }

    @Test
    void paymentMethodsOfferedAreCodVnpayAndInstallment() {
        checkout(1);
        String options = String.valueOf(ui.js("return [...document.querySelectorAll('select[name=paymentMethod] option')].map(o=>o.value).join(',');"));
        assertEquals("PAY_LATER,VNPAY,INSTALLMENT", options);
    }

    @Test
    void successPageWithoutOrderSaysSo() {
        ui.open("/checkout/success");
        ui.until(d -> ui.exists(By.cssSelector(".commerce-empty")));
        assertEquals("Chưa có đơn hàng mới", ui.text(By.cssSelector(".commerce-empty h2")));
    }
}
