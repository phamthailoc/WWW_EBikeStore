package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * V19: the Manager and Admin area in English. Signs in as each staff account, switches to EN, opens every page and
 * the admin/stock/image dialogs, and lists any Vietnamese text left in the page chrome (data typed by people, such
 * as product or customer names, is ignored). The full list is written to target/selenium/staff-english-*.txt.
 */
@Tag("selenium")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class StaffEnglishSeleniumTest extends StorefrontSeleniumTest {

    private static final By ROWS = By.cssSelector("#data-table tbody tr");
    private static final By OPEN_DIALOG = By.cssSelector("dialog[open]");

    /**
     * Visible text nodes and labelling attributes with Vietnamese letters. Table cells are skipped except their
     * buttons, since cells hold data (names, addresses, stored notes) that is not translated. So are the order's
     * shipment and cancellation values and the timeline notes (typed reasons; the labels and status names are
     * checked), and the language switch, which names "Tiếng Việt" on purpose.
     */
    private static final String SCAN = """
        const vi=/[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]/i;
        const root=arguments[0]||document.body, out=[];
        const where=el=>{const host=el.closest('[id]');return el.tagName.toLowerCase()+(el.className&&typeof el.className==='string'?'.'+el.className.split(' ')[0]:'')+(host?' in #'+host.id:'')};
        const isData=el=>el.matches('#shipmentDetails p, #cancellationDetails p, #orderTimeline small') || !!el.closest('.lang-switch')
          || !!el.closest('tbody td, .report-list b, legend, .unit-numbers') && !el.closest('button, a.table-action, select, label');
        const walker=document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
        for(let n=walker.nextNode();n;n=walker.nextNode()){
          const el=n.parentElement, text=n.textContent.trim();
          if(!text||!vi.test(text)||!el||['SCRIPT','STYLE','TITLE'].includes(el.tagName))continue;
          if(el.tagName!=='OPTION'&&!el.getClientRects().length)continue;
          if(isData(el))continue;
          out.push(where(el)+': '+text.replace(/\\s+/g,' ').slice(0,160));
        }
        for(const el of root.querySelectorAll('[placeholder],[title],[aria-label]')){
          for(const a of ['placeholder','title','aria-label']){const v=el.getAttribute(a);if(v&&vi.test(v)&&!isData(el))out.push(where(el)+' @'+a+': '+v.slice(0,160))}
        }
        if(root===document.body&&vi.test(document.title))out.push('document.title: '+document.title);
        return [...new Set(out)];
        """;

    private final Map<String, List<String>> leftovers = new LinkedHashMap<>();
    private String signedInAs;

    @AfterEach
    void putTheAccountBackInVietnamese() {
        // /lang/{code} also stores the choice on the account; other tests expect the staff accounts in Vietnamese.
        if (signedInAs != null) {
            driver.get(baseUrl + "/lang/vi?back=/");
        }
    }

    private void signInInEnglish(String userProperty, String passwordProperty, String home) {
        String user = System.getProperty(userProperty, "");
        String password = System.getProperty(passwordProperty, "");
        assumeTrue(!user.isBlank() && !password.isBlank(), userProperty + "/" + passwordProperty + " not set");
        logIn(user, password);
        signedInAs = user;
        driver.get(baseUrl + "/lang/en?back=" + home);
        wait.until(d -> "en".equals(js("return document.documentElement.lang")));
    }

    @SuppressWarnings("unchecked")
    private void scan(String label, WebElement root) {
        List<String> found = (List<String>) js(SCAN, root);
        if (!found.isEmpty()) {
            leftovers.put(label, found);
        }
    }

    /** Opens the page, waits for the table or report to render, then records leftovers. */
    private void visit(String path) {
        driver.get(baseUrl + path);
        wait.until(d -> "en".equals(js("return document.documentElement.lang")));
        // The table is filled by fetch(); #loading disappears once the data (or an error) arrives.
        wait.until(d -> Boolean.TRUE.equals(js("const l=document.getElementById('loading');return document.readyState==='complete'&&(!l||l.hidden||!l.getClientRects().length)")));
        pause(1000); // secondary panels (filters, charts, order detail) render right after
        scan(path, null);
    }

    private void scanDialog(String label, By opener) {
        List<WebElement> buttons = driver.findElements(opener);
        if (buttons.isEmpty()) {
            leftovers.put(label, List.of("(not opened: no " + opener + " on the page)"));
            return;
        }
        click(buttons.get(0));
        WebElement dialog = wait.until(ExpectedConditions.visibilityOfElementLocated(OPEN_DIALOG));
        pause(800); // dialogs fill their lists (roles, colours, images) after opening
        scan(label, dialog);
        dialog.sendKeys(Keys.ESCAPE);
        js("document.querySelectorAll('dialog[open]').forEach(d=>d.close())");
        wait.until(ExpectedConditions.invisibilityOfElementLocated(OPEN_DIALOG));
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void report(String who) throws Exception {
        List<String> lines = new ArrayList<>();
        leftovers.forEach((page, texts) -> {
            lines.add("== " + page);
            texts.forEach(text -> lines.add("   " + text));
        });
        Path dir = Files.createDirectories(Path.of("target", "selenium"));
        Files.write(dir.resolve("staff-english-" + who + ".txt"), lines, StandardCharsets.UTF_8);
        assertTrue(leftovers.isEmpty(), "[" + who + "] Vietnamese left in EN mode:\n" + String.join("\n", lines));
    }

    private void assertMenuAndTitleInEnglish(String title, List<String> menu) {
        assertEquals(title, driver.findElement(By.cssSelector("main h1")).getText().trim());
        String nav = driver.findElement(By.cssSelector(".work-sidebar")).getText();
        for (String link : menu) {
            assertTrue(nav.contains(link), "menu has " + link + ": " + nav);
        }
    }

    @Test
    void managerAreaIsInEnglish() throws Exception {
        HttpClient manager = managerApi();
        signInInEnglish("selenium.managerUser", "selenium.managerPassword", "/manager");
        visit("/manager");
        assertMenuAndTitleInEnglish("Store overview",
            List.of("Overview", "Orders", "Payments", "Customers", "Support", "Inventory", "Products", "Reports"));

        for (String path : List.of("/manager/orders", "/manager/payments", "/manager/support", "/manager/customers",
                "/manager/inventory", "/manager/products", "/manager/reports")) {
            visit(path);
        }
        Long orderId = null;
        for (JsonNode order : listOf(call(manager, "GET", "/api/v1/orders", null))) {
            orderId = order.path("id").asLong();
            break;
        }
        if (orderId != null) {
            visit("/manager/orders/" + orderId);
        }

        visit("/manager/inventory");
        scanDialog("/manager/inventory stock dialog", By.cssSelector("#data-table [data-action='stock'][data-slug]"));
        scanDialog("/manager/inventory image dialog", By.cssSelector("#data-table [data-action='images']"));
        report("manager");
    }

    @Test
    void adminAreaIsInEnglish() throws Exception {
        signInInEnglish("selenium.adminUser", "selenium.adminPassword", "/admin");
        visit("/admin");
        assertMenuAndTitleInEnglish("Administration dashboard",
            List.of("Overview", "Products", "Inventory", "Pricing & promotions", "Accounts", "Roles", "Log", "Users"));

        for (String path : List.of("/admin/products", "/admin/inventory", "/admin/pricing", "/admin/roles",
                "/admin/audit-logs", "/admin/users")) {
            visit(path);
        }

        visit("/admin/accounts");
        assertTrue(driver.findElement(By.cssSelector(".theme-toggle")).getAttribute("title").startsWith("Appearance"),
            "theme button tooltip is in English");
        scanDialog("/admin/accounts create dialog", By.id("create-account"));
        scanDialog("/admin/accounts role dialog", By.cssSelector("#data-table [data-action='role']"));
        scanDialog("/admin/accounts delete dialog", By.cssSelector("#data-table [data-action='delete-account']"));

        visit("/admin/pricing");
        scanDialog("/admin/pricing new code dialog", By.id("addPromotion"));
        scanDialog("/admin/pricing edit code dialog", By.cssSelector("[data-action='promotion']"));
        report("admin");
    }
}
