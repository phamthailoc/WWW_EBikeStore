package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * V19: helpers for tests that open pages in English and fail on Vietnamese text left in the interface. Data people
 * typed or the shop stored (product names and descriptions, addresses, notes) is not translated, so it is skipped.
 */
abstract class EnglishPageSeleniumTest extends StorefrontSeleniumTest {

    protected static final By OPEN_DIALOG = By.cssSelector("dialog[open]");

    /**
     * Visible text nodes and labelling attributes with Vietnamese letters. Table cells are skipped except their
     * buttons, since cells hold data (names, addresses, stored notes). So are the order's shipment and cancellation
     * values and the timeline notes (typed reasons; the labels and status names are checked), the language switch,
     * which names "Tiếng Việt" on purpose, whatever {@link #dataSelector()} adds, and the names in {@link #dataTexts()}.
     */
    private static final String SCAN = """
        const marks=/[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]/i;
        // Vietnamese words without accents that a missed t() would leave behind ("12 xe", "Pin", "Phanh", "5 tr").
        const words=/(^|[^\\p{L}])(xe|Xe|Xem|Pin|Phanh|tr)(?=$|[^\\p{L}])/u;
        const vi={test:s=>marks.test(s)||words.test(s)};
        const root=arguments[0]||document.body, extra=arguments[1], out=[];
        // Names the shop stored (products, categories) are cut out of a text before it is checked.
        const known=(arguments[2]||[]).slice().sort((a,b)=>b.length-a.length);
        const strip=s=>known.reduce((t,k)=>t.split(k).join(' '),s);
        const where=el=>{const host=el.closest('[id]');return el.tagName.toLowerCase()+(el.className&&typeof el.className==='string'?'.'+el.className.split(' ')[0]:'')+(host?' in #'+host.id:'')};
        const isData=el=>el.matches('#shipmentDetails p, #cancellationDetails p, #orderTimeline small') || !!el.closest('.lang-switch')
          || (extra && !!el.closest(extra))
          || !!el.closest('tbody td, .report-list b, legend, .unit-numbers') && !el.closest('button, a.table-action, select, label');
        const walker=document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
        for(let n=walker.nextNode();n;n=walker.nextNode()){
          const el=n.parentElement, text=n.textContent.trim();
          if(!text||!vi.test(strip(text))||!el||['SCRIPT','STYLE','TITLE'].includes(el.tagName))continue;
          if(el.tagName!=='OPTION'&&!el.getClientRects().length)continue;
          if(isData(el))continue;
          out.push(where(el)+': '+text.replace(/\\s+/g,' ').slice(0,160));
        }
        for(const el of root.querySelectorAll('[placeholder],[title],[aria-label]')){
          for(const a of ['placeholder','title','aria-label']){const v=el.getAttribute(a);if(v&&vi.test(strip(v))&&!isData(el))out.push(where(el)+' @'+a+': '+v.slice(0,160))}
        }
        if(root===document.body&&vi.test(strip(document.title)))out.push('document.title: '+document.title);
        return [...new Set(out)];
        """;

    private final Map<String, List<String>> leftovers = new LinkedHashMap<>();

    /** Extra CSS selector for elements whose text is data on these pages; empty when there are none. */
    protected String dataSelector() {
        return "";
    }

    private final List<String> shopTexts = new ArrayList<>();

    /** Shop-stored names (e.g. product and category names) to ignore wherever they appear inside a text. */
    protected List<String> dataTexts() {
        return shopTexts;
    }

    /**
     * Reads what the shop stores in Vietnamese by design: product names, descriptions, categories and colour names,
     * plus the province list. Call before scanning pages that show them.
     */
    protected void rememberShopData() throws Exception {
        for (JsonNode product : listOf(call(api, "GET", "/api/v1/products", null))) {
            shopTexts.add(product.path("name").asText());
            shopTexts.add(product.path("description").asText());
            shopTexts.add(product.path("category").path("name").asText());
            JsonNode detail = call(api, "GET", "/api/v1/products/" + product.path("slug").asText(), null);
            for (JsonNode variant : detail.path("variants")) {
                shopTexts.add(variant.path("colorName").asText());
            }
        }
        for (JsonNode province : call(api, "GET", "/data/vietnam-provinces.json", null)) {
            shopTexts.add(province.asText());
        }
        shopTexts.removeIf(String::isBlank);
    }

    /** Switches the browser (and the signed-in account, if any) to English and lands on {@code path}. */
    protected void switchToEnglish(String path) {
        driver.get(baseUrl + "/lang/en?back=" + path);
        wait.until(d -> "en".equals(js("return document.documentElement.lang")));
    }

    @SuppressWarnings("unchecked")
    protected void scan(String label, WebElement root) {
        String extra = dataSelector();
        List<String> found = (List<String>) js(SCAN, root, extra.isBlank() ? null : extra, dataTexts());
        if (!found.isEmpty()) {
            leftovers.computeIfAbsent(label, key -> new ArrayList<>()).addAll(found);
        }
    }

    /** Opens the page, waits for its data to render, then records leftovers. */
    protected void visit(String path) {
        driver.get(baseUrl + path);
        scanCurrentPage(path);
    }

    /** Records leftovers on the page the browser is on now (e.g. after a redirect). */
    protected void scanCurrentPage(String label) {
        wait.until(d -> "en".equals(js("return document.documentElement.lang")));
        // Tables are filled by fetch(); #loading disappears once the data (or an error) arrives.
        wait.until(d -> Boolean.TRUE.equals(js("const l=document.getElementById('loading');return document.readyState==='complete'&&(!l||l.hidden||!l.getClientRects().length)")));
        pause(1200); // secondary panels (filters, charts, order detail, quotes) render right after
        scan(label, null);
    }

    protected void scanDialog(String label, By opener) {
        List<WebElement> buttons = driver.findElements(opener);
        if (buttons.isEmpty()) {
            leftovers.put(label, List.of("(not opened: no " + opener + " on the page)"));
            return;
        }
        // Closing the previous dialog may reload the table, so the button found a moment ago can go stale.
        for (int attempt = 1; ; attempt++) {
            try {
                click(driver.findElements(opener).get(0));
                break;
            } catch (org.openqa.selenium.StaleElementReferenceException | IndexOutOfBoundsException e) {
                if (attempt == 5) {
                    throw e;
                }
                pause(500);
            }
        }
        WebElement dialog = wait.until(ExpectedConditions.visibilityOfElementLocated(OPEN_DIALOG));
        pause(800); // dialogs fill their lists (roles, colours, images) after opening
        scan(label, dialog);
        dialog.sendKeys(Keys.ESCAPE);
        js("document.querySelectorAll('dialog[open]').forEach(d=>d.close())");
        wait.until(ExpectedConditions.invisibilityOfElementLocated(OPEN_DIALOG));
    }

    protected static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Writes target/selenium/{file}.txt and fails if any page still had Vietnamese interface text. */
    protected void report(String file) throws Exception {
        List<String> lines = new ArrayList<>();
        leftovers.forEach((page, texts) -> {
            lines.add("== " + page);
            texts.forEach(text -> lines.add("   " + text));
        });
        Path dir = Files.createDirectories(Path.of("target", "selenium"));
        Files.write(dir.resolve(file + ".txt"), lines, StandardCharsets.UTF_8);
        assertTrue(leftovers.isEmpty(), "[" + file + "] Vietnamese left in EN mode:\n" + String.join("\n", lines));
    }
}
