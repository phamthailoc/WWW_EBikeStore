package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

/** V6: the admin console's account form, role and audit pages, discount-code form and the safety rules. */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class AdminConsoleSeleniumTest extends StorefrontSeleniumTest {

    private static final By TABLE_ROWS = By.cssSelector("#data-table tbody tr");
    private static final By DIALOG = By.cssSelector("dialog.admin-dialog[open]");

    private String adminUser;
    private String adminPassword;

    private void signInAsAdmin() {
        adminUser = System.getProperty("selenium.adminUser", "");
        adminPassword = System.getProperty("selenium.adminPassword", "");
        assumeTrue(!adminUser.isBlank() && !adminPassword.isBlank(), "selenium.adminUser/selenium.adminPassword not set");
        logIn(adminUser, adminPassword);
    }

    private boolean listed(By rows, String text) {
        return driver.findElements(rows).stream().anyMatch(row -> row.getText().contains(text));
    }

    private WebElement rowOf(String text) {
        return wait.until(d -> d.findElements(TABLE_ROWS).stream().filter(row -> row.getText().contains(text)).findFirst().orElse(null));
    }

    private void type(WebElement dialog, String name, String value) {
        WebElement input = dialog.findElement(By.name(name));
        input.clear();
        input.sendKeys(value);
    }

    @Test
    void adminCreatesAManagerThenRemovesIt() {
        signInAsAdmin();
        driver.get(baseUrl + "/admin/accounts");
        wait.until(ExpectedConditions.presenceOfElementLocated(TABLE_ROWS));
        String email = "ql" + System.currentTimeMillis() + "@example.com";

        click(By.id("create-account"));
        WebElement dialog = wait.until(ExpectedConditions.visibilityOfElementLocated(DIALOG));
        type(dialog, "name", "Quản Lý Thử");
        type(dialog, "email", email);
        type(dialog, "password", "MatKhau@123");
        new Select(dialog.findElement(By.name("role"))).selectByValue("MANAGER");
        dialog.findElement(By.cssSelector("[type=submit]")).click();
        wait.until(ExpectedConditions.invisibilityOfElementLocated(DIALOG));

        WebElement row = rowOf(email);
        assertTrue(row.getText().contains("MANAGER"), row.getText());

        row.findElement(By.cssSelector("[data-action='delete-account']")).click();
        WebElement confirm = wait.until(ExpectedConditions.visibilityOfElementLocated(DIALOG));
        // a wrong email is refused and the account stays
        type(confirm, "confirmEmail", "khong.dung@example.com");
        confirm.findElement(By.cssSelector("[type=submit]")).click();
        wait.until(d -> !confirm.findElement(By.cssSelector(".dialog-error")).getText().isBlank());
        assertTrue(listed(TABLE_ROWS, email));
        // the exact email erases it: the account disappears from the list
        type(confirm, "confirmEmail", email);
        confirm.findElement(By.cssSelector("[type=submit]")).click();
        wait.until(ExpectedConditions.invisibilityOfElementLocated(DIALOG));
        wait.until(d -> !listed(TABLE_ROWS, email));
    }

    @Test
    void anAdminCannotDeleteTheirOwnAccount() throws Exception {
        signInAsAdmin();
        HttpClient admin = newClient();
        call(admin, "POST", "/api/v1/auth/login", Map.of("usernameOrEmail", adminUser, "password", adminPassword));
        long ownId = 0;
        String ownEmail = "";
        for (JsonNode account : listOf(call(admin, "GET", "/api/v1/admin/accounts", null))) {
            if (adminUser.equals(account.path("username").asText())) {
                ownId = account.path("id").asLong();
                ownEmail = account.path("email").asText();
            }
        }
        long id = ownId;
        String email = ownEmail;
        IllegalStateException refused = assertThrows(IllegalStateException.class,
            () -> call(admin, "DELETE", "/api/v1/admin/accounts/" + id + "?confirmEmail=" + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8), null));
        assertTrue(refused.getMessage().contains("400"), refused.getMessage());
    }

    @Test
    void rolesAndAuditLogPagesListTheirData() {
        signInAsAdmin();
        driver.get(baseUrl + "/admin/roles");
        wait.until(ExpectedConditions.presenceOfElementLocated(TABLE_ROWS));
        assertTrue(listed(TABLE_ROWS, "MANAGER") && listed(TABLE_ROWS, "ADMIN"));

        driver.get(baseUrl + "/admin/audit-logs");
        wait.until(ExpectedConditions.presenceOfElementLocated(TABLE_ROWS));
        assertFalse(driver.findElements(TABLE_ROWS).isEmpty());
    }

    @Test
    void discountCodeFormRefusesAnImpossibleCodeAndSavesAGoodOne() {
        signInAsAdmin();
        driver.get(baseUrl + "/admin/pricing");
        wait.until(ExpectedConditions.elementToBeClickable(By.id("addPromotion")));
        String code = "FORM" + System.currentTimeMillis() % 1_000_000;

        click(By.id("addPromotion"));
        WebElement dialog = wait.until(ExpectedConditions.visibilityOfElementLocated(DIALOG));
        type(dialog, "code", code);
        type(dialog, "campaignName", "Mã thử từ biểu mẫu");
        type(dialog, "discountValue", "150"); // 150% is impossible
        dialog.findElement(By.cssSelector("[type=submit]")).click();
        WebElement error = wait.until(ExpectedConditions.visibilityOf(dialog.findElement(By.cssSelector(".dialog-error"))));
        assertTrue(error.getText().contains("100%"), error.getText());

        type(dialog, "discountValue", "15");
        dialog.findElement(By.cssSelector("[type=submit]")).click();
        wait.until(ExpectedConditions.invisibilityOfElementLocated(DIALOG));
        wait.until(d -> d.findElement(By.id("promo-table")).getText().contains(code));
    }
}
