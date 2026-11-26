package com.ebike.qa.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.qa.selenium.pages.LoginPage;
import com.ebike.qa.selenium.pages.ProductPage;
import com.ebike.qa.selenium.pages.WorkspacePage;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

@EnabledIfSystemProperty(named = "qa.baseUrl", matches = ".+")
class CustomerAreaTest extends QaBase {
    private static final String SLUG = Shop.buySlug();

    private JsonNode orderFor(Api api, Customer c) { return api.placeOrder("Kiểm Thử", c.email(), SLUG, 1, "PAY_LATER"); }

    private long userId(Api api) { return api.get("/api/v1/auth/profile").json().get("userId").asLong(); }

    @Test
    void ordersListShowsTheCustomersOrdersWithStatus() {
        Customer c = newCustomer();
        Api api = customerApi(c);
        JsonNode o1 = orderFor(api, c);
        JsonNode o2 = orderFor(api, c);
        loginCustomer(c);
        WorkspacePage w = new WorkspacePage(ui).open("/customer/orders");
        assertEquals("Đơn hàng của tôi", w.title());
        assertNotNull(w.waitRow(o1.get("orderNumber").asText()));
        WebElement row = w.waitRow(o2.get("orderNumber").asText());
        assertEquals("Chờ xử lý", w.cell(row, "status"));
        assertTrue(w.cell(row, "paymentMethod").contains("Thanh toán khi nhận xe"));
        assertEquals(2, w.rows().size());
    }

    @Test
    void orderDetailShowsTimelineItemsAndPricing() {
        Customer c = newCustomer();
        JsonNode o = orderFor(customerApi(c), c);
        loginCustomer(c);
        WorkspacePage w = new WorkspacePage(ui).open("/customer/orders/" + o.get("id").asLong());
        ui.visible(By.id("orderTimeline"));
        assertTrue(ui.text(By.id("orderTimeline")).contains("Chờ xử lý"));
        assertTrue(ui.text(By.id("orderItems")).contains(Shop.buyName()));
        long total = Math.round(o.get("totalAmount").asDouble());
        assertEquals(total, com.ebike.qa.selenium.pages.Page.money(ui.text(By.cssSelector("#orderPricing td[data-price='<strong>Tổng cộng</strong>']"))));
        assertTrue(ui.exists(By.id("shipmentDetails")));
        assertTrue(ui.text(By.id("orderDocuments")).contains("Tải phiếu tạm tính"));
    }

    @Test
    void cancelOrderWithReasonThroughPrompt() {
        Customer c = newCustomer();
        JsonNode o = orderFor(customerApi(c), c);
        loginCustomer(c);
        WorkspacePage w = new WorkspacePage(ui).open("/customer/orders");
        WebElement row = w.waitRow(o.get("orderNumber").asText());
        ui.click(w.button(row, "cancel-order"));
        assertEquals("Lý do hủy đơn hàng", ui.answerPrompt("Đổi ý, không mua nữa"));
        w.waitNotice("Đã hủy đơn hàng.");
        ui.until(d -> "Đã hủy".equals(w.cell(w.rowWith(o.get("orderNumber").asText()), "status")));
        assertFalse(w.rowWith(o.get("orderNumber").asText()).getText().contains("Hủy đơn"), "a cancelled order has no cancel button");
        assertEquals("CANCELLED", new Api().login(c.username(), c.password()).get("/api/v1/orders/" + o.get("id").asLong()).json().get("status").asText());
    }

    @Test
    void cancelOrderWithBlankReasonIsRefused() {
        Customer c = newCustomer();
        JsonNode o = orderFor(customerApi(c), c);
        loginCustomer(c);
        WorkspacePage w = new WorkspacePage(ui).open("/customer/orders");
        WebElement row = w.waitRow(o.get("orderNumber").asText());
        ui.click(w.button(row, "cancel-order"));
        ui.answerPrompt("   ");
        w.waitNotice("Vui lòng nhập lý do hủy đơn hàng.");
        assertEquals("Chờ xử lý", w.cell(w.rowWith(o.get("orderNumber").asText()), "status"));
        // dismissing the prompt does nothing either
        ui.click(w.button(w.rowWith(o.get("orderNumber").asText()), "cancel-order"));
        ui.dismissAlert();
        assertEquals("Chờ xử lý", w.cell(w.rowWith(o.get("orderNumber").asText()), "status"));
    }

    @Test
    void profileUpdatePersists() {
        Customer c = newCustomer();
        loginCustomer(c);
        WorkspacePage w = new WorkspacePage(ui).open("/customer/profile");
        ui.visible(By.id("profileForm"));
        assertEquals("Kiểm", ui.prop(By.cssSelector("#profileForm [name=firstName]"), "value"));
        ui.type(By.cssSelector("#profileForm [name=firstName]"), "Đổi Tên");
        ui.click(By.cssSelector("#profileForm button.button"));
        w.waitNotice("Hồ sơ đã được cập nhật.");
        ui.d.navigate().refresh();
        ui.visible(By.id("profileForm"));
        assertEquals("Đổi Tên", ui.prop(By.cssSelector("#profileForm [name=firstName]"), "value"));
        assertEquals(c.email(), ui.prop(By.cssSelector("#profileForm [name=email]"), "value"));
    }

    @Test
    void changePasswordThenOldPasswordStopsWorking() {
        Customer c = newCustomer();
        loginCustomer(c);
        WorkspacePage w = new WorkspacePage(ui).open("/customer/profile");
        String next = QaConfig.get("customer.newPassword");
        ui.type(By.cssSelector("#passwordForm [name=currentPassword]"), c.password());
        ui.type(By.cssSelector("#passwordForm [name=newPassword]"), next);
        ui.type(By.cssSelector("#passwordForm [name=confirmPassword]"), next);
        ui.click(By.cssSelector("#passwordForm button"));
        w.waitNotice("Đã đổi mật khẩu.");
        w.logout();
        new LoginPage(ui).open().fill(c.username(), c.password()).submit();
        assertEquals("Tên đăng nhập hoặc mật khẩu chưa chính xác.", new LoginPage(ui).error());
        new LoginPage(ui).fill(c.username(), next).submit();
        ui.waitUrlContains("/customer");
    }

    @Test
    void changePasswordRefusesMismatchAndWrongCurrent() {
        Customer c = newCustomer();
        loginCustomer(c);
        WorkspacePage w = new WorkspacePage(ui).open("/customer/profile");
        ui.type(By.cssSelector("#passwordForm [name=currentPassword]"), c.password());
        ui.type(By.cssSelector("#passwordForm [name=newPassword]"), "Another@12345");
        ui.type(By.cssSelector("#passwordForm [name=confirmPassword]"), "Different@12345");
        ui.click(By.cssSelector("#passwordForm button"));
        w.waitNotice("Mật khẩu nhập lại không khớp.");
        ui.type(By.cssSelector("#passwordForm [name=currentPassword]"), "NotMyPassword1");
        ui.type(By.cssSelector("#passwordForm [name=confirmPassword]"), "Another@12345");
        ui.click(By.cssSelector("#passwordForm button"));
        w.waitNotice("Mật khẩu hiện tại không đúng.");
    }

    @Test
    void addressBookAddDefaultAndDelete() {
        Customer c = newCustomer();
        loginCustomer(c);
        WorkspacePage w = new WorkspacePage(ui).open("/customer/profile");
        ui.visible(By.id("addressForm"));
        assertTrue(ui.text(By.id("addressList")).contains("Bạn chưa lưu địa chỉ nào."));
        addAddress("Hồ Chí Minh", "12 Nguyễn Trãi, Phường Bến Thành", false, w);
        addAddress("Hà Nội", "34 Phố Huế, Phường Phạm Đình Hổ", false, w);
        assertEquals(2, ui.all(By.cssSelector("#addressList li[data-id]")).size());
        // make the second one the default
        WebElement second = ui.all(By.cssSelector("#addressList li[data-id]")).stream().filter(li -> li.getText().contains("Hà Nội")).findFirst().orElseThrow();
        ui.click(second.findElement(By.cssSelector("[data-do=default]")));
        w.waitNotice("Đã đặt làm địa chỉ mặc định.");
        ui.until(d -> ui.all(By.cssSelector("#addressList li[data-id]")).stream().anyMatch(li -> li.getText().contains("Hà Nội") && li.getText().contains("Mặc định")));
        // delete the first one (confirm accepted)
        WebElement first = ui.all(By.cssSelector("#addressList li[data-id]")).stream().filter(li -> li.getText().contains("Hồ Chí Minh")).findFirst().orElseThrow();
        ui.click(first.findElement(By.cssSelector("[data-do=delete]")));
        assertEquals("Xóa địa chỉ này?", ui.acceptAlert());
        w.waitNotice("Đã xóa địa chỉ.");
        ui.until(d -> ui.all(By.cssSelector("#addressList li[data-id]")).size() == 1);
        // declining the confirm keeps it
        ui.click(By.cssSelector("#addressList [data-do=delete]"));
        ui.dismissAlert();
        assertEquals(1, ui.all(By.cssSelector("#addressList li[data-id]")).size());
    }

    private void addAddress(String city, String street, boolean makeDefault, WorkspacePage w) {
        ui.selectValue(By.cssSelector("#addressForm [name=city]"), city);
        ui.type(By.cssSelector("#addressForm [name=street]"), street);
        if (makeDefault) ui.click(By.cssSelector("#addressForm [name=isDefault]"));
        int before = ui.all(By.cssSelector("#addressList li[data-id]")).size();
        ui.click(By.id("addressSubmit"));
        w.waitNotice("Đã lưu địa chỉ.");
        ui.until(d -> ui.all(By.cssSelector("#addressList li[data-id]")).size() == before + 1);
    }

    @Test
    void addressBookIsLimitedToTen() {
        Customer c = newCustomer();
        Api api = customerApi(c);
        long id = userId(api);
        for (int i = 1; i <= 10; i++) {
            Api.Reply r = api.post("/api/v1/users/" + id + "/addresses", Map.of("addressType", "SHIPPING", "city", "Hà Nội", "street", "Số " + i + " Phố Huế, Phường Phạm Đình Hổ", "country", "Việt Nam", "isDefault", i == 1));
            assertEquals(200, r.status() == 201 ? 200 : r.status(), r.body());
        }
        loginCustomer(c);
        WorkspacePage w = new WorkspacePage(ui).open("/customer/profile");
        ui.until(d -> ui.all(By.cssSelector("#addressList li[data-id]")).size() == 10);
        ui.selectValue(By.cssSelector("#addressForm [name=city]"), "Hà Nội");
        ui.type(By.cssSelector("#addressForm [name=street]"), "Số 11 Phố Huế, Phường Phạm Đình Hổ");
        ui.click(By.id("addressSubmit"));
        ui.until(d -> ui.shown(WorkspacePage.NOTICE) && ui.textOf(WorkspacePage.NOTICE).length() > 0);
        assertTrue(ui.attr(WorkspacePage.NOTICE, "class").contains("error"), "the eleventh address must be refused: " + ui.textOf(WorkspacePage.NOTICE));
        assertEquals(10, ui.all(By.cssSelector("#addressList li[data-id]")).size());
    }

    @Test
    void themePreferenceIsSavedToTheAccount() {
        Customer c = newCustomer();
        loginCustomer(c);
        new WorkspacePage(ui).open("/customer/profile");
        ui.visible(By.id("themeChoice"));
        ui.selectValue(By.id("themeChoice"), "dark");
        ui.until(d -> "dark".equals(ui.js("return document.documentElement.getAttribute('data-theme');")));
        Api api = customerApi(c);
        long id = userId(api);
        ui.until(d -> "dark".equals(api.get("/api/v1/users/" + id + "/preferences").json().path("theme").asText()));
        ui.d.manage().deleteAllCookies();
        ui.js("localStorage.clear();");
        ui.open("/login");
        new LoginPage(ui).fill(c.username(), c.password()).submit();
        ui.waitUrlContains("/customer");
        assertEquals("dark", ui.js("return document.documentElement.getAttribute('data-theme');"), "a fresh sign-in shows the saved theme");
    }

    @Test
    void notificationSwitchIsSaved() {
        Customer c = newCustomer();
        loginCustomer(c);
        WorkspacePage w = new WorkspacePage(ui).open("/customer/profile");
        ui.until(d -> ui.present(By.id("notificationsEnabled")).isSelected());
        ui.click(By.id("notificationsEnabled"));
        w.waitNotice("Đã tắt thông báo.");
        Api api = customerApi(c);
        assertEquals(false, api.get("/api/v1/users/" + userId(api) + "/preferences").json().path("notificationsEnabled").asBoolean(true));
    }

    @Test
    void favouriteAddedOnProductPageAppearsAndCanBeRemoved() {
        Customer c = newCustomer();
        loginCustomer(c);
        ProductPage p = new ProductPage(ui).open(Shop.SHOW);
        ui.click(ProductPage.FAVOURITE);
        assertEquals("Đã thêm xe vào danh sách yêu thích.", p.notice());
        ui.open("/favorites");
        ui.until(d -> ui.exists(By.cssSelector(".favorite-card")));
        assertTrue(ui.text(By.cssSelector(".favorite-card h2")).contains(Shop.SHOW_NAME));
        ui.click(By.cssSelector("[data-remove-product]"));
        ui.until(d -> ui.exists(By.cssSelector("#flow-extra .empty")) && ui.textOf(By.cssSelector("#flow-extra")).contains("Bạn chưa lưu mẫu xe yêu thích nào."));
    }

    @Test
    void favouriteAsGuestSendsToLogin() {
        new ProductPage(ui).open(SLUG);
        ui.click(ProductPage.FAVOURITE);
        ui.until(d -> d.getCurrentUrl().contains("/login"));
        assertTrue(ui.url().contains("returnTo="));
    }

    @Test
    void statusChangeCreatesANotificationAndBadge() {
        Customer c = newCustomer();
        Api api = customerApi(c);
        JsonNode o = orderFor(api, c);
        Api.Reply r = Api.staff("manager").send("PATCH", "/api/v1/orders/" + o.get("id").asLong() + "/status", Map.of("status", "CONFIRMED"));
        assertEquals(200, r.status(), r.body());
        loginCustomer(c);
        ui.until(d -> ui.exists(By.id("notifBadge")) && !ui.textOf(By.id("notifBadge")).isEmpty() && ui.shown(By.id("notifBadge")));
        WorkspacePage w = new WorkspacePage(ui).open("/customer/notifications");
        w.waitRow(o.get("orderNumber").asText());
        WebElement row = w.waitRow("đã chuyển sang trạng thái đã xác nhận");
        assertNotNull(row);
        assertTrue(row.getText().contains("Cập nhật đơn hàng"), row.getText());
        assertEquals("-", w.cell(row, "readAt"), "unread notifications have no read date");
        ui.click(w.button(row, "read"));
        ui.until(d -> !"-".equals(w.cellOf("đã chuyển sang trạng thái đã xác nhận", "readAt")));
    }

    @Test
    void notificationsPageHasMarkAllRead() {
        Customer c = newCustomer();
        Api api = customerApi(c);
        JsonNode o = orderFor(api, c);
        Api.staff("manager").send("PATCH", "/api/v1/orders/" + o.get("id").asLong() + "/status", Map.of("status", "CONFIRMED"));
        loginCustomer(c);
        new WorkspacePage(ui).open("/customer/notifications");
        ui.click(By.xpath("//button[normalize-space()='Đánh dấu tất cả đã đọc']"));
        ui.waitText(By.id("notice"), "Đã đánh dấu tất cả thông báo là đã đọc.");
        ui.until(d -> !ui.exists(By.cssSelector("#data-table [data-action=read]")));
    }

    @Test
    void guestOrderIsLinkedWithAnEmailCode() {
        Customer c = newCustomer();
        JsonNode guestOrder = new Api().placeOrder("Khách Vãng Lai", c.email(), SLUG, 1, "PAY_LATER");
        loginCustomer(c);
        WorkspacePage w = new WorkspacePage(ui).open("/customer/orders");
        assertEquals(0, w.rows().size());
        long mailId = Mail.lastId();
        ui.click(By.id("link-guest-orders"));
        String ask = ui.acceptAlert();
        assertTrue(ask.contains(c.email()), ask);
        String code = Mail.waitForOtp(c.email(), mailId);
        assertEquals(6, code.length());
        String question = ui.answerPrompt(code);
        assertTrue(question.contains("6 chữ số"), question);
        w.waitNotice("Đã liên kết 1 đơn hàng vào tài khoản.");
        assertNotNull(w.waitRow(guestOrder.get("orderNumber").asText()));
    }

    @Test
    void guestOrderLinkRefusesAMalformedCode() {
        Customer c = newCustomer();
        loginCustomer(c);
        WorkspacePage w = new WorkspacePage(ui).open("/customer/orders");
        ui.click(By.id("link-guest-orders"));
        ui.acceptAlert();
        ui.answerPrompt("12ab");
        w.waitNotice("Mã xác minh gồm 6 chữ số.");
    }

    @Test
    void guestOrderLinkWithNoMatchingOrderSaysSo() {
        Customer c = newCustomer();
        loginCustomer(c);
        WorkspacePage w = new WorkspacePage(ui).open("/customer/orders");
        long mailId = Mail.lastId();
        ui.click(By.id("link-guest-orders"));
        ui.acceptAlert();
        String code = Mail.waitForOtp(c.email(), mailId);
        ui.answerPrompt(code);
        w.waitNotice("Không tìm thấy đơn nào đặt bằng email này khi chưa đăng nhập.");
    }

    @Test
    void emptyCustomerPagesExplainThemselves() {
        Customer c = newCustomer();
        loginCustomer(c);
        WorkspacePage w = new WorkspacePage(ui).open("/customer/warranty");
        assertTrue(ui.text(WorkspacePage.EMPTY).startsWith("Chưa có xe nào được giao."));
        w.open("/customer/test-rides");
        assertTrue(ui.text(WorkspacePage.EMPTY).startsWith("Bạn chưa đặt lịch lái thử nào."));
        w.open("/customer/payments");
        assertEquals("Lịch sử thanh toán", w.title());
        w.open("/customer");
        assertEquals("Tổng quan tài khoản", w.title());
    }

    @Test
    void deleteAccountNeedsPasswordEmailAndConfirm() {
        Customer c = newCustomer();
        loginCustomer(c);
        new WorkspacePage(ui).open("/customer/profile");
        ui.visible(By.id("deleteForm"));
        ui.type(By.cssSelector("#deleteForm [name=password]"), c.password());
        ui.type(By.cssSelector("#deleteForm [name=confirmEmail]"), c.email());
        ui.click(By.cssSelector("#deleteForm button"));
        assertEquals("Xóa vĩnh viễn tài khoản của bạn?", ui.dismissAlert());
        assertTrue(ui.url().contains("/customer/profile"), "dismissing the confirm keeps the account");
        ui.click(By.cssSelector("#deleteForm button"));
        ui.acceptAlert();
        ui.until(d -> d.getCurrentUrl().replaceAll("/+$", "").equals(QaConfig.baseUrl()));
        assertTrue(new Api().post("/api/v1/auth/login", Map.of("usernameOrEmail", c.username(), "password", c.password())).status() >= 400, "an erased account cannot sign in");
    }
}
