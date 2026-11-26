package com.ebike.qa.selenium;

import com.ebike.qa.selenium.pages.LoginPage;
import com.ebike.qa.selenium.pages.SignupPage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.UnexpectedAlertBehaviour;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Base of every QA test: one fresh Chrome per test (so one fresh session), screenshots on failure.
 * Concrete classes carry @EnabledIfSystemProperty(named="qa.baseUrl", matches=".+"); annotations on this class are not inherited.
 */
public abstract class QaBase {
    public record Customer(String username, String email, String password) { }

    protected WebDriver driver;
    protected Ui ui;
    protected Path downloads;

    @RegisterExtension
    final ShotOnFailure shot = new ShotOnFailure();

    final class ShotOnFailure implements AfterTestExecutionCallback {
        @Override
        public void afterTestExecution(ExtensionContext ctx) {
            if (ctx.getExecutionException().isPresent() && ui != null) {
                ui.screenshot(ctx.getRequiredTestClass().getSimpleName() + "." + ctx.getRequiredTestMethod().getName());
            }
        }
    }

    public static WebDriver newDriver(Path downloadDir, int width, int height) {
        ChromeOptions o = new ChromeOptions();
        if (QaConfig.headless()) o.addArguments("--headless=new");
        o.addArguments("--window-size=" + width + "," + height, "--lang=vi", "--disable-gpu", "--no-sandbox", "--remote-allow-origins=*",
            "--disable-dev-shm-usage", "--disable-notifications", "--disable-search-engine-choice-screen");
        o.setPageLoadStrategy(PageLoadStrategy.NORMAL);
        o.setUnhandledPromptBehaviour(UnexpectedAlertBehaviour.IGNORE);
        o.setExperimentalOption("prefs", Map.of(
            "download.default_directory", downloadDir.toAbsolutePath().toString(),
            "download.prompt_for_download", false,
            "intl.accept_languages", "vi"));
        WebDriver d = new ChromeDriver(o);
        d.manage().window().setSize(new Dimension(width, height));
        return d;
    }

    @BeforeEach
    void startBrowser() throws Exception {
        downloads = Files.createTempDirectory("qa-dl");
        driver = newDriver(downloads, 1440, 900);
        ui = new Ui(driver);
        driver.manage().timeouts().pageLoadTimeout(java.time.Duration.ofSeconds(45));
    }

    @AfterEach
    void stopBrowser() {
        if (driver != null) {
            try { driver.quit(); } catch (RuntimeException ignored) { /* already gone */ }
        }
    }

    // ---- shared flows ----

    /** Creates a customer through the signup page (a throw-away browser, so the current session stays as it is). */
    protected Customer newCustomer() { return newCustomer(ui); }

    protected static Customer newCustomer(Ui browser) {
        String user = QaConfig.uniqueUsername();
        Customer c = new Customer(user, user + "@" + QaConfig.get("customer.emailDomain"), QaConfig.get("customer.password"));
        SignupPage signup = new SignupPage(browser).open();
        signup.fill("Kiểm", "Thử", c.username(), c.email(), c.password());
        signup.submit();
        browser.waitUrlContains("/login");
        return c;
    }

    /** Signs a customer in through the login page; waits for the customer area. */
    protected void loginCustomer(Customer c) {
        new LoginPage(ui).loginAs(c.username(), c.password());
        ui.waitUrlContains("/customer");
    }

    protected void loginStaff(String role) {
        new LoginPage(ui).loginAs(QaConfig.get("staff." + role + ".username"), QaConfig.get("staff." + role + ".password"));
        ui.waitUrlContains("/" + role);
    }

    protected Api customerApi(Customer c) { return new Api().login(c.username(), c.password()); }

    /** Puts bikes into the cart through the product page (the way a visitor does). */
    protected void fillCart(String slug, int quantity) {
        com.ebike.qa.selenium.pages.ProductPage page = new com.ebike.qa.selenium.pages.ProductPage(ui).open(slug);
        if (quantity > 1) page.setQuantity(String.valueOf(quantity));
        page.addToCart();
        page.notice();
        ui.until(d -> Integer.parseInt(page.cartBadge()) >= quantity);
    }

    protected static List<String> labels(List<org.openqa.selenium.WebElement> els) {
        return els.stream().map(e -> e.getText().trim()).toList();
    }
}
