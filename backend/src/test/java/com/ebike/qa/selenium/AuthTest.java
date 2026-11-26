package com.ebike.qa.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.qa.selenium.pages.LoginPage;
import com.ebike.qa.selenium.pages.SignupPage;
import com.ebike.qa.selenium.pages.WorkspacePage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;

@EnabledIfSystemProperty(named = "qa.baseUrl", matches = ".+")
class AuthTest extends QaBase {

    @Test
    void signup_emptyFormIsBlockedByTheBrowser() {
        SignupPage s = new SignupPage(ui).open();
        for (By f : new By[] {SignupPage.FIRST, SignupPage.LAST, SignupPage.USER, SignupPage.EMAIL, SignupPage.PASSWORD}) {
            assertTrue(s.invalid(f), "required: " + f);
        }
        s.submit();
        assertTrue(ui.url().endsWith("/signup"), "nothing is sent");
        assertEquals("", ui.textOf(SignupPage.ERROR));
    }

    @Test
    void signup_emailFormatAndPasswordLengthAreChecked() {
        SignupPage s = new SignupPage(ui).open();
        s.fill("An", "Binh", "qa.sel.val", "abc", "short");
        assertEquals(Boolean.TRUE, ui.js("return arguments[0].validity.typeMismatch;", ui.present(SignupPage.EMAIL)));
        assertEquals(Boolean.TRUE, ui.js("return arguments[0].validity.tooShort;", ui.present(SignupPage.PASSWORD)));
        assertEquals("8", ui.attr(SignupPage.PASSWORD, "minlength"));
    }

    @Test
    void signup_createsAccountAndSendsToLoginWithNotice() {
        Customer c = newCustomer();
        assertTrue(ui.url().contains("/login?registered=1"), ui.url());
        assertEquals("Tạo tài khoản thành công. Bạn có thể đăng nhập ngay.", ui.text(LoginPage.NOTICE));
        assertTrue(c.username().startsWith("qa.sel."));
    }

    @Test
    void signup_duplicateUsernameAndEmailAreRefused() {
        Customer c = newCustomer();
        SignupPage s = new SignupPage(ui).open();
        s.fill("An", "Binh", c.username(), "other." + c.email(), c.password());
        s.submit();
        assertEquals("Tên đăng nhập đã được sử dụng.", s.error());
        s.open().fill("An", "Binh", QaConfig.uniqueUsername(), c.email(), c.password());
        s.submit();
        assertEquals("Email đã được sử dụng.", s.error());
        assertTrue(ui.url().endsWith("/signup"));
    }

    @Test
    void login_wrongPasswordAndUnknownUserShowTheSameMessage() {
        Customer c = newCustomer();
        LoginPage l = new LoginPage(ui).open();
        l.fill(c.username(), "WrongPassword!1").submit();
        assertEquals("Tên đăng nhập hoặc mật khẩu chưa chính xác.", l.error());
        assertTrue(ui.url().contains("/login"));
        ui.js("document.getElementById('error').textContent='';");
        l.fill("no.such.user." + System.nanoTime(), "WrongPassword!1").submit();
        assertEquals("Tên đăng nhập hoặc mật khẩu chưa chính xác.", l.error());
        assertTrue(ui.visible(LoginPage.SUBMIT).isEnabled(), "the button is usable again");
    }

    @Test
    void login_byUsernameAndByEmailBothReachTheCustomerArea() {
        Customer c = newCustomer();
        new LoginPage(ui).loginAs(c.username(), c.password());
        ui.waitUrlContains("/customer");
        new WorkspacePage(ui).waitLoaded();
        ui.open("/api/v1/auth/profile");
        assertTrue(ui.textOf(By.tagName("body")).contains(c.email()));
        new Api().login(c.email(), c.password());
        ui.d.manage().deleteAllCookies();
        new LoginPage(ui).loginAs(c.email(), c.password());
        ui.waitUrlContains("/customer");
    }

    @Test
    void login_showPasswordCheckboxTogglesTheField() {
        LoginPage l = new LoginPage(ui).open();
        assertEquals("password", ui.attr(LoginPage.PASSWORD, "type"));
        ui.click(LoginPage.SHOW_PW);
        assertEquals("text", ui.attr(LoginPage.PASSWORD, "type"));
        ui.click(LoginPage.SHOW_PW);
        assertEquals("password", ui.attr(LoginPage.PASSWORD, "type"));
    }

    @Test
    void login_emptyFieldsAreRequired() {
        LoginPage l = new LoginPage(ui).open();
        assertEquals("true", ui.prop(LoginPage.IDENTITY, "required"));
        assertEquals("true", ui.prop(LoginPage.PASSWORD, "required"));
        l.submit();
        assertTrue(ui.url().endsWith("/login"));
    }

    @Test
    void role_customerGoesToCustomerArea() {
        Customer c = newCustomer();
        new LoginPage(ui).loginAs(c.username(), c.password());
        assertTrue(ui.path().startsWith("/customer"), ui.path());
        assertEquals("Tổng quan tài khoản", new WorkspacePage(ui).waitLoaded().title());
    }

    @Test
    void role_managerGoesToManagerArea() {
        loginStaff("manager");
        assertEquals("/manager", ui.path());
        assertEquals("Tổng quan cửa hàng", new WorkspacePage(ui).waitLoaded().title());
    }

    @Test
    void role_adminGoesToAdminArea() {
        loginStaff("admin");
        assertEquals("/admin", ui.path());
        assertEquals("Bảng điều khiển quản trị", new WorkspacePage(ui).waitLoaded().title());
    }

    @Test
    void logout_endsTheSession() {
        Customer c = newCustomer();
        loginCustomer(c);
        WorkspacePage w = new WorkspacePage(ui).waitLoaded();
        w.logout();
        ui.open("/customer");
        ui.until(d -> d.getCurrentUrl().contains("/login"));
        assertTrue(ui.url().contains("returnTo=%2Fcustomer") || ui.url().contains("returnTo=/customer"), ui.url());
        ui.open("/api/v1/auth/profile");
        assertFalse(ui.textOf(By.tagName("body")).contains(c.email()), "the profile must not be readable after logout");
    }

    @Test
    void returnTo_bringsTheVisitorBackAfterLogin() {
        Customer c = newCustomer();
        ui.open("/customer/orders");
        ui.until(d -> d.getCurrentUrl().contains("/login"));
        assertTrue(ui.url().contains("returnTo="), ui.url());
        new LoginPage(ui).fill(c.username(), c.password()).submit();
        ui.until(d -> d.getCurrentUrl().contains("/customer/orders"));
        assertEquals("Đơn hàng của tôi", new WorkspacePage(ui).waitLoaded().title());
    }

    @Test
    void returnTo_ignoresAnExternalAddress() {
        Customer c = newCustomer();
        ui.open("/login?returnTo=" + java.net.URLEncoder.encode("https://evil.example.com/steal", java.nio.charset.StandardCharsets.UTF_8));
        new LoginPage(ui).fill(c.username(), c.password()).submit();
        ui.until(d -> !d.getCurrentUrl().contains("/login"));
        assertTrue(ui.url().startsWith(QaConfig.baseUrl()), "must stay on the shop: " + ui.url());
    }

    @Test
    void signup_linkKeepsReturnToOnTheLoginPage() {
        ui.open("/login?returnTo=%2Fproduct%2Fkinetic-s-one");
        String href = ui.attr(By.cssSelector("a.register-link"), "href");
        assertTrue(href.startsWith("/signup?returnTo="), href);
    }
}
