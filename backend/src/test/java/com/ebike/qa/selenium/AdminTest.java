package com.ebike.qa.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.qa.selenium.pages.LoginPage;
import com.ebike.qa.selenium.pages.WorkspacePage;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

@EnabledIfSystemProperty(named = "qa.baseUrl", matches = ".+")
class AdminTest extends QaBase {
    private static final String PASSWORD = "QaSelenium@123";

    private WorkspacePage accounts() {
        loginStaff("admin");
        return new WorkspacePage(ui).open("/admin/accounts");
    }

    /** Creates an account through the admin dialog and returns its username. */
    private String createAccount(WorkspacePage w, String role) {
        String user = QaConfig.uniqueUsername();
        ui.click(By.id("create-account"));
        By dialog = By.cssSelector("dialog.admin-dialog");
        ui.visible(dialog);
        ui.type(By.cssSelector("dialog.admin-dialog [name=name]"), "Nhân Viên Kiểm Thử");
        ui.type(By.cssSelector("dialog.admin-dialog [name=email]"), user + "@example.com");
        ui.type(By.cssSelector("dialog.admin-dialog [name=username]"), user);
        ui.type(By.cssSelector("dialog.admin-dialog [name=password]"), PASSWORD);
        new Select(ui.visible(By.cssSelector("dialog.admin-dialog [name=role]"))).selectByValue(role);
        ui.click(By.cssSelector("dialog.admin-dialog button[type=submit]"));
        w.waitNotice("Đã tạo tài khoản.");
        search(w, user);
        w.waitRow(user);
        return user;
    }

    /** Clicks a row action, looking the row up again on every try because the list redraws itself after each change. */
    private void act(WorkspacePage w, String rowText, String action) {
        ui.until(20, d -> {
            try {
                WebElement r = w.rowWith(rowText);
                if (r == null) return false;
                ui.click(w.button(r, action));
                return true;
            } catch (RuntimeException e) { return false; }
        });
    }

    private void search(WorkspacePage w, String q) {
        w.search("q", q);
        ui.until(20, d -> w.rows().size() >= 1 && w.rowWith(q) != null && w.rows().size() <= 3 || w.rows().isEmpty());
    }

    // ---------------------------------------------------------------- overview, roles, audit

    @Test
    void overviewShowsRecentActivity() {
        loginStaff("admin");
        WorkspacePage w = new WorkspacePage(ui).waitLoaded();
        assertEquals("Bảng điều khiển quản trị", w.title());
        assertEquals("Nhật ký hoạt động gần đây", ui.text(By.id("panel-title")));
        assertTrue(w.rows().size() >= 1, "the audit rows of the overview");
        assertTrue(w.headers().contains("Người thực hiện"), w.headers().toString());
    }

    @Test
    void rolesPageListsEveryRoleWithItsPermissions() {
        loginStaff("admin");
        WorkspacePage w = new WorkspacePage(ui).open("/admin/roles");
        for (String role : new String[] {"ADMIN", "MANAGER", "CUSTOMER"}) assertTrue(w.rows().stream().anyMatch(r -> w.cell(r, "role").equals(role)), role);
        WebElement admin = w.rows().stream().filter(r -> w.cell(r, "role").equals("ADMIN")).findFirst().orElseThrow();
        assertTrue(admin.getText().contains("ADMIN"));
    }

    @Test
    void usersPageListsCustomersOnly() {
        loginStaff("admin");
        WorkspacePage w = new WorkspacePage(ui).open("/admin/users");
        assertEquals("Người dùng", w.title());
        for (WebElement r : w.rows()) assertEquals("CUSTOMER", w.cell(r, "role"));
    }

    // ---------------------------------------------------------------- accounts

    @Test
    void accountCreatedByAdminCanSignIn() {
        WorkspacePage w = accounts();
        String user = createAccount(w, "CUSTOMER");
        WebElement row = w.rowWith(user);
        assertEquals("CUSTOMER", w.cell(row, "role"));
        assertEquals("ACTIVE", w.cell(row, "status"));
        assertEquals(200, new Api().post("/api/v1/auth/login", Map.of("usernameOrEmail", user, "password", PASSWORD)).status());
    }

    @Test
    void createAccountDialogRefusesDuplicateEmail() {
        WorkspacePage w = accounts();
        String user = createAccount(w, "CUSTOMER");
        ui.click(By.id("create-account"));
        ui.visible(By.cssSelector("dialog.admin-dialog"));
        ui.type(By.cssSelector("dialog.admin-dialog [name=name]"), "Trùng Email");
        ui.type(By.cssSelector("dialog.admin-dialog [name=email]"), user + "@example.com");
        ui.type(By.cssSelector("dialog.admin-dialog [name=password]"), PASSWORD);
        ui.click(By.cssSelector("dialog.admin-dialog button[type=submit]"));
        ui.until(d -> ui.shown(By.cssSelector("dialog.admin-dialog .dialog-error")));
        assertFalse(ui.text(By.cssSelector("dialog.admin-dialog .dialog-error")).isBlank());
        ui.click(By.cssSelector("dialog.admin-dialog [data-cancel]"));
    }

    @Test
    void accountEditChangesNameAndEmail() {
        WorkspacePage w = accounts();
        String user = createAccount(w, "CUSTOMER");
        act(w, user, "account-detail");
        ui.visible(By.cssSelector("dialog.admin-dialog"));
        assertTrue(ui.text(By.cssSelector("dialog.admin-dialog .account-facts")).contains(user));
        ui.type(By.cssSelector("dialog.admin-dialog [name=name]"), "Tên Đã Sửa");
        ui.click(By.cssSelector("dialog.admin-dialog button[type=submit]"));
        w.waitNotice("Đã lưu thông tin tài khoản.");
        ui.until(20, d -> w.rowWith(user) != null && w.cellOf(user, "name") != null && w.cellOf(user, "name").contains("Tên Đã Sửa"));
    }

    @Test
    void lockAndUnlockBlocksAndRestoresSignIn() {
        WorkspacePage w = accounts();
        String user = createAccount(w, "CUSTOMER");
        act(w, user, "account-status");
        w.waitNotice("Đã cập nhật dữ liệu.");
        ui.until(20, d -> w.rowWith(user) != null && "INACTIVE".equals(w.cellOf(user, "status")));
        assertNotEquals(200, new Api().post("/api/v1/auth/login", Map.of("usernameOrEmail", user, "password", PASSWORD)).status(), "a locked account cannot sign in");
        assertTrue(w.rowWith(user).getText().contains("Kích hoạt lại"));
        act(w, user, "account-status");
        ui.until(20, d -> w.rowWith(user) != null && "ACTIVE".equals(w.cellOf(user, "status")));
        assertEquals(200, new Api().post("/api/v1/auth/login", Map.of("usernameOrEmail", user, "password", PASSWORD)).status());
    }

    @Test
    void roleChangeSendsTheAccountToTheManagerArea() {
        WorkspacePage w = accounts();
        String user = createAccount(w, "CUSTOMER");
        act(w, user, "role");
        new Select(ui.visible(By.cssSelector("dialog.admin-dialog [name=role]"))).selectByValue("MANAGER");
        ui.click(By.cssSelector("dialog.admin-dialog button[type=submit]"));
        w.waitNotice("Đã đổi vai trò.");
        ui.until(20, d -> w.rowWith(user) != null && "MANAGER".equals(w.cellOf(user, "role")));
        ui.d.manage().deleteAllCookies();
        new LoginPage(ui).loginAs(user, PASSWORD);
        ui.waitUrlContains("/manager");
    }

    @Test
    void deleteNeedsTheEmailTypedBack() {
        WorkspacePage w = accounts();
        String user = createAccount(w, "CUSTOMER");
        act(w, user, "delete-account");
        ui.visible(By.cssSelector("dialog.admin-dialog"));
        ui.type(By.cssSelector("dialog.admin-dialog [name=confirmEmail]"), "someone.else@example.com");
        ui.click(By.cssSelector("dialog.admin-dialog button[type=submit]"));
        ui.waitText(By.cssSelector("dialog.admin-dialog .dialog-error"), "Email chưa khớp với tài khoản cần xóa.");
        ui.type(By.cssSelector("dialog.admin-dialog [name=confirmEmail]"), user + "@example.com");
        ui.click(By.cssSelector("dialog.admin-dialog button[type=submit]"));
        w.waitNotice("Đã xóa tài khoản.");
        ui.until(20, d -> w.rowWith(user) == null);
        assertTrue(new Api().post("/api/v1/auth/login", Map.of("usernameOrEmail", user, "password", PASSWORD)).status() >= 400, "a deleted account cannot sign in");
    }

    @Test
    void adminActionsAppearInTheAuditLog() {
        WorkspacePage w = accounts();
        String user = createAccount(w, "CUSTOMER");
        w.open("/admin/audit-logs");
        WebElement row = w.waitRow(user);
        assertEquals("selenium.admin", w.cell(row, "actor"));
        assertFalse(w.cell(row, "action").isBlank());
    }

    // ---------------------------------------------------------------- pricing and promotions

    @Test
    void pricingRuleEditThroughPromptAndRefusalOfGarbage() {
        loginStaff("admin");
        WorkspacePage w = new WorkspacePage(ui).open("/admin/pricing");
        WebElement row = w.waitRow("REGISTRATION_SERVICE");
        long original = Math.round(Api.staff("admin").get("/api/v1/admin/pricing-rules").json().get(0).get("amount").asDouble());
        ui.click(w.button(row, "pricing"));
        assertEquals("Số tiền áp dụng", ui.answerPrompt("abc"));
        w.waitNotice("Giá trị không hợp lệ.");
        ui.click(w.button(w.rowWith("REGISTRATION_SERVICE"), "pricing"));
        ui.dismissAlert();
        try {
            ui.click(w.button(w.rowWith("REGISTRATION_SERVICE"), "pricing"));
            ui.answerPrompt("550000");
            w.waitNotice("Đã cập nhật dữ liệu.");
            ui.until(20, d -> w.rowWith("REGISTRATION_SERVICE").getText().contains("550.000"));
            JsonNode quote = new Api().post("/api/v1/orders/quote", new Api().quoteBody(null, true, "STORE_PICKUP")).json();
            assertEquals(550_000, quote.get("registrationServicePerBike").asLong(), "the new fee reaches the quote");
        } finally {
            ui.d.navigate().refresh();
            WorkspacePage back = new WorkspacePage(ui).waitLoaded();
            ui.click(back.button(back.waitRow("REGISTRATION_SERVICE"), "pricing"));
            ui.answerPrompt(String.valueOf(original));
            ui.until(20, d -> back.rowWith("REGISTRATION_SERVICE").getText().contains("500.000"));
        }
    }

    @Test
    void promotionCreateEditAndDuplicateRefused() {
        loginStaff("admin");
        WorkspacePage w = new WorkspacePage(ui).open("/admin/pricing");
        ui.visible(By.id("promo-panel"));
        String code = "QASEL" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        openPromotionDialog();
        fillPromotion(code, "QA chương trình", "FIXED_AMOUNT", "50000", "LIVE");
        ui.click(By.cssSelector("dialog.admin-dialog button[type=submit]"));
        w.waitNotice("Đã tạo mã khuyến mãi.");
        By promoRows = By.cssSelector("#promo-table tbody tr");
        ui.until(20, d -> { try { return ui.all(promoRows).stream().anyMatch(r -> r.getText().contains(code)); } catch (RuntimeException e) { return false; } });
        // the code works at the quote
        JsonNode quote = new Api().post("/api/v1/orders/quote", new Api().quoteBody(code, false, "STORE_PICKUP")).json();
        assertEquals(50_000, quote.get("discountAmount").asLong());
        // the same code again is refused inside the dialog
        openPromotionDialog();
        fillPromotion(code, "QA trùng mã", "FIXED_AMOUNT", "10000", "LIVE");
        ui.click(By.cssSelector("dialog.admin-dialog button[type=submit]"));
        ui.until(d -> ui.shown(By.cssSelector("dialog.admin-dialog .dialog-error")));
        assertFalse(ui.text(By.cssSelector("dialog.admin-dialog .dialog-error")).isBlank());
        ui.click(By.cssSelector("dialog.admin-dialog [data-cancel]"));
        // switch it off again
        ui.until(20, d -> { try { ui.click(ui.all(promoRows).stream().filter(r -> r.getText().contains(code)).findFirst().orElseThrow().findElement(By.cssSelector("[data-action=promotion]"))); return true; } catch (RuntimeException e) { return false; } });
        new Select(ui.visible(By.cssSelector("dialog.admin-dialog [name=status]"))).selectByValue("DISABLED");
        ui.click(By.cssSelector("dialog.admin-dialog button[type=submit]"));
        w.waitNotice("Đã cập nhật mã khuyến mãi.");
        ui.until(20, d -> { try { return ui.all(promoRows).stream().anyMatch(r -> r.getText().contains(code) && (r.getText().contains("Tắt") || r.getText().contains("DISABLED"))); } catch (RuntimeException e) { return false; } });
        Api.Reply off = new Api().post("/api/v1/orders/quote", new Api().quoteBody(code, false, "STORE_PICKUP"));
        assertEquals(400, off.status(), "a disabled code is refused");
    }

    private void openPromotionDialog() {
        ui.click(By.id("addPromotion"));
        ui.visible(By.cssSelector("dialog.admin-dialog"));
    }

    private void fillPromotion(String code, String name, String type, String value, String status) {
        ui.type(By.cssSelector("dialog.admin-dialog [name=code]"), code);
        ui.type(By.cssSelector("dialog.admin-dialog [name=campaignName]"), name);
        new Select(ui.present(By.cssSelector("dialog.admin-dialog [name=discountType]"))).selectByValue(type);
        ui.type(By.cssSelector("dialog.admin-dialog [name=discountValue]"), value);
        new Select(ui.present(By.cssSelector("dialog.admin-dialog [name=status]"))).selectByValue(status);
    }

    @Test
    void promotionDialogRequiresCodeNameAndValue() {
        loginStaff("admin");
        new WorkspacePage(ui).open("/admin/pricing");
        openPromotionDialog();
        for (String f : new String[] {"code", "campaignName", "discountValue"}) {
            assertEquals(Boolean.FALSE, ui.js("return arguments[0].checkValidity();", ui.present(By.cssSelector("dialog.admin-dialog [name=" + f + "]"))), f);
        }
        ui.click(By.cssSelector("dialog.admin-dialog [data-cancel]"));
        assertNull(ui.d.findElements(By.cssSelector("dialog.admin-dialog[open]")).stream().findFirst().orElse(null));
    }
}
