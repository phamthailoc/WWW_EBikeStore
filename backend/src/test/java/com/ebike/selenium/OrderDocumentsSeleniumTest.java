package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * V27: the sales receipt and the warranty card as PDF. A customer places an order and can already download a provisional receipt (no warranty
 * card yet); once the manager has delivered it with frame numbers, the order page offers the receipt and the warranty card, the warranty book
 * offers the card of each bike, the files read back with the right words in both languages, a stranger gets 404 and a visitor 401, the staff
 * can open any order's files (also an admin, and a store pick-up order names the showroom), and a cancelled order has no receipt. The rate limiter is off in Selenium runs, so the 429 notice is not covered here (documents.js has no unit test of its own: these scenarios are its only coverage). The hand-over email needs a mail server and is covered by unit tests only.
 * Needs {@code -Dselenium.managerUser} and {@code -Dselenium.managerPassword}; skipped otherwise.
 *
 * <pre>
 * mvn test -Dtest=OrderDocumentsSeleniumTest -Dselenium.baseUrl=http://localhost:8080 \
 *     -Dselenium.managerUser=... -Dselenium.managerPassword=...
 * </pre>
 */
@Tag("selenium")
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("product-stock")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class OrderDocumentsSeleniumTest extends StorefrontSeleniumTest {

    private static final By PANEL = By.id("orderDocuments");

    @org.junit.jupiter.api.BeforeAll
    static void eraseTheCustomerWhenDone() {
        // the test customer and its delivered orders are erased with the account at the end of the class (the orders stay as anonymous sales records)
        eraseCustomerWhenDone = true;
    }

    // ---- helpers ----

    private static HttpResponse<byte[]> download(HttpClient client, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/pdf, application/json")
            .GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String textOf(byte[] pdf) throws IOException {
        try (PDDocument document = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
    }

    private static int pagesOf(byte[] pdf) throws IOException {
        try (PDDocument document = PDDocument.load(pdf)) {
            return document.getNumberOfPages();
        }
    }

    private static void assertPdf(HttpResponse<byte[]> response) {
        assertEquals(200, response.statusCode(), new String(response.body()));
        assertEquals("application/pdf", response.headers().firstValue("Content-Type").orElse(""));
        assertTrue(response.headers().firstValue("Content-Disposition").orElse("").startsWith("attachment"), "an attachment");
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"), "never cached");
        assertEquals("%PDF", new String(response.body(), 0, 4));
    }

    private void logInAsManager() {
        logIn(System.getProperty("selenium.managerUser"), System.getProperty("selenium.managerPassword"));
    }

    private void openOrder(long orderId) {
        driver.get(baseUrl + "/customer/orders/" + orderId);
        wait.until(ExpectedConditions.visibilityOfElementLocated(PANEL));
    }

    private void clickDownload(String urlPart) {
        js("var n=document.querySelector('#notice');n.hidden=true;n.textContent='';");
        click(By.cssSelector("#orderDocuments [data-doc-url*='" + urlPart + "']"));
    }

    private static Map<Long, Integer> stockOf(String slug) throws Exception {
        Map<Long, Integer> stock = new LinkedHashMap<>();
        for (JsonNode variant : listOf(call(api, "GET", "/api/v1/products/" + slug, null).path("variants"))) {
            stock.put(variant.path("id").asLong(), variant.path("stockQuantity").asInt());
        }
        return stock;
    }

    /**
     * Puts back the stock a test's orders used up, so the next test finds the bike as it was. This is a known drift: a DELIVERED order sold its bike for good,
     * so after this the stock figure is higher than the orders explain, and no cancellation records why. It is accepted for test data in a local database;
     * the stock history of V26 will have to ignore (or the test must stop restoring) such rows.
     */
    private static void restoreStock(HttpClient manager, String slug, Map<Long, Integer> before) {
        try {
            long productId = call(api, "GET", "/api/v1/products/" + slug, null).path("id").asLong();
            Map<Long, Integer> now = stockOf(slug);
            for (Map.Entry<Long, Integer> entry : before.entrySet()) {
                Integer current = now.get(entry.getKey());
                if (current != null && !current.equals(entry.getValue())) {
                    call(manager, "PATCH", "/api/v1/manager/products/" + productId + "/variants/" + entry.getKey() + "/stock",
                        Map.of("stockQuantity", entry.getValue()));
                }
            }
        } catch (Exception e) {
            System.err.println("Could not put the stock of " + slug + " back: " + e.getMessage());
        }
    }

    /** Sends the browser's downloads to a folder of their own, so a test can see that a file was really saved. */
    private Path downloadFolder() throws IOException {
        Path folder = Files.createTempDirectory("selenium-pdf");
        ((ChromeDriver) driver).executeCdpCommand("Browser.setDownloadBehavior", Map.<String, Object>of("behavior", "allow", "downloadPath", folder.toString()));
        return folder;
    }

    /** Waits until a finished PDF whose name starts with {@code prefix} is in the folder (Chrome writes a .crdownload file until it is done). */
    private void assertSaved(Path folder, String prefix) {
        wait.until(d -> {
            try (Stream<Path> files = Files.list(folder)) {
                return files.map(file -> file.getFileName().toString()).anyMatch(name -> name.startsWith(prefix) && name.endsWith(".pdf"));
            } catch (IOException e) {
                return false;
            }
        });
    }

    /** The names of the finished PDFs in the folder now. */
    private static Set<String> pdfNames(Path folder) {
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(".pdf")).collect(Collectors.toSet());
        } catch (IOException e) {
            return Set.of();
        }
    }

    /** Waits for a finished PDF that starts with {@code prefix} and was not in the folder before (so an earlier download of the same name cannot satisfy it), and returns it. */
    private Path newSaved(Path folder, String prefix, Set<String> before) {
        return wait.until(d -> {
            try (Stream<Path> files = Files.list(folder)) {
                return files.filter(file -> {
                    String name = file.getFileName().toString();
                    return name.startsWith(prefix) && name.endsWith(".pdf") && !before.contains(name);
                }).findFirst().orElse(null);
            } catch (IOException e) {
                return null;
            }
        });
    }

    private static void deleteQuietly(Path folder) {
        try (Stream<Path> files = Files.walk(folder)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(file -> file.toFile().delete());
        } catch (IOException | RuntimeException e) {
            System.err.println("Could not remove " + folder + ": " + e.getMessage());
        }
    }

    private static long placePickupOrderOverRest() throws Exception {
        return placePickupOrderOverRest(productSlug);
    }

    private static long placePickupOrderOverRest(String slug) throws Exception {
        JsonNode product = call(api, "GET", "/api/v1/products/" + slug, null);
        JsonNode color = null;
        for (JsonNode variant : listOf(product.path("variants"))) {
            if (color == null || variant.path("stockQuantity").asInt() > color.path("stockQuantity").asInt()) {
                color = variant;
            }
        }
        assumeTrue(color == null || color.path("stockQuantity").asInt() > 0, "every colour of " + slug + " is sold out");
        long showroom = listOf(call(api, "GET", "/api/v1/showrooms", null)).iterator().next().path("id").asLong();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productId", product.path("id").asLong());
        item.put("quantity", 1);
        item.put("productVariantId", color == null ? null : color.path("id").asLong());
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("customerName", "Selenium Tester");
        order.put("phoneNumber", "0901234567");
        order.put("customerEmail", username + "@example.com");
        order.put("deliveryMethod", "STORE_PICKUP");
        order.put("pickupShowroomId", showroom);
        order.put("paymentMethod", "PAY_LATER");
        order.put("items", List.of(item));
        return call(api, "POST", "/api/v1/orders", order).path("id").asLong();
    }

    // ---- scenarios ----

    @Test
    void receiptBeforeAndAfterDeliveryWarrantyCardAndWhoMayOpenThem() throws Exception {
        HttpClient manager = managerApi();
        Map<Long, Integer> stockBefore = stockOf(productSlug);
        Path downloads = downloadFolder();
        try {
            walkThrough(manager, downloads);
        } finally {
            restoreStock(manager, productSlug, stockBefore);
            deleteQuietly(downloads);
        }
    }

    private void walkThrough(HttpClient manager, Path downloads) throws Exception {
        String orderNumber = placePayLaterOrder();
        long orderId = findOrder(orderNumber).path("id").asLong();

        // 1. not handed over yet: a provisional receipt, and no warranty card
        openOrder(orderId);
        WebElement panel = driver.findElement(PANEL);
        assertTrue(panel.getText().contains("Hóa đơn và phiếu bảo hành"), panel.getText());
        assertTrue(panel.getText().contains("Tải phiếu tạm tính"), panel.getText());
        assertTrue(panel.getText().contains("không phải hóa đơn điện tử"), panel.getText());
        assertTrue(driver.findElements(By.cssSelector("#orderDocuments [data-doc-url*='warranty-card']")).isEmpty(), "no card before the hand-over");
        assertTrue(panel.getText().contains("Phiếu bảo hành có sau khi xe được bàn giao"), panel.getText());
        HttpResponse<byte[]> provisional = download(api, "/api/v1/orders/" + orderId + "/receipt");
        assertPdf(provisional);
        assertTrue(textOf(provisional.body()).contains("PHIẾU TẠM TÍNH"), textOf(provisional.body()));
        assertTrue(textOf(provisional.body()).contains("Công ty TNHH"), "the shop heading keeps its accents: " + textOf(provisional.body()));
        HttpResponse<byte[]> tooEarly = download(api, "/api/v1/orders/" + orderId + "/warranty-card");
        assertEquals(409, tooEarly.statusCode(), "the card exists only after the hand-over");

        // 2. the button saves the file and says so (a refusal would show in red instead)
        clickDownload("/receipt");
        assertEquals("Đã tải tệp PDF.", notice());
        assertSaved(downloads, "receipt-" + orderNumber);
        assertFalse(driver.findElement(By.id("notice")).getAttribute("class").contains("error"));

        // 3. deliver it with frame numbers
        for (String status : new String[] {"CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED"}) {
            if (status.equals("SHIPPED")) enterUnits(manager, orderId);
            call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", status));
        }
        JsonNode order = call(manager, "GET", "/api/v1/orders/" + orderId, null);
        JsonNode unit = order.path("items").get(0).path("units").get(0);
        String frame = unit.path("frameNumber").asText();
        long unitId = unit.path("id").asLong();
        int bikes = order.path("items").get(0).path("units").size();

        // 4. the order page now offers the receipt and the warranty card
        openOrder(orderId);
        panel = driver.findElement(PANEL);
        assertTrue(panel.getText().contains("Tải hóa đơn"), panel.getText());
        assertTrue(panel.getText().contains("Tải phiếu bảo hành"), panel.getText());
        HttpResponse<byte[]> receipt = download(api, "/api/v1/orders/" + orderId + "/receipt");
        assertPdf(receipt);
        String receiptText = textOf(receipt.body());
        assertTrue(receiptText.contains("PHIẾU BÁN HÀNG"), receiptText);
        assertTrue(receiptText.contains(orderNumber), receiptText);
        assertTrue(receiptText.contains(frame), "the frame number is on the receipt: " + receiptText);
        assertTrue(receiptText.contains("không phải hóa đơn điện tử"), receiptText);
        assertTrue(receiptText.contains("Công ty TNHH") && receiptText.contains("Nguyễn Văn Linh"), "the shop heading keeps its accents: " + receiptText);
        HttpResponse<byte[]> card = download(api, "/api/v1/orders/" + orderId + "/warranty-card");
        assertPdf(card);
        assertEquals(bikes, pagesOf(card.body()), "one page for each bike");
        assertTrue(textOf(card.body()).contains("PHIẾU BẢO HÀNH") && textOf(card.body()).contains(frame), textOf(card.body()));
        assertTrue(textOf(card.body()).contains("Công ty TNHH"), textOf(card.body()));
        clickDownload("/warranty-card");
        assertEquals("Đã tải tệp PDF.", notice());
        assertSaved(downloads, "warranty-card-" + orderNumber);

        // 5. the warranty book has the card of each bike, one at a time
        driver.get(baseUrl + "/customer/warranty");
        WebElement row = wait.until(d -> d.findElements(By.cssSelector("#data-table tbody tr")).stream()
            .filter(r -> r.getText().contains(frame)).findFirst().orElse(null));
        assertTrue(row.getText().contains("Tải phiếu bảo hành"), row.getText());
        js("var n=document.querySelector('#notice');n.hidden=true;n.textContent='';");
        click(row.findElement(By.cssSelector("[data-doc-url*='unitId=" + unitId + "']")));
        assertEquals("Đã tải tệp PDF.", notice());
        assertSaved(downloads, "warranty-card-" + frame);
        HttpResponse<byte[]> single = download(api, "/api/v1/orders/" + orderId + "/warranty-card?unitId=" + unitId);
        assertPdf(single);
        assertEquals(1, pagesOf(single.body()));
        assertTrue(single.headers().firstValue("Content-Disposition").orElse("").contains("warranty-card-" + frame));
        assertEquals(404, download(api, "/api/v1/orders/" + orderId + "/warranty-card?unitId=999999999").statusCode(), "a bike that is not in the order");

        // 6. English: the page and the file
        try {
            driver.get(baseUrl + "/lang/en?back=/customer/orders/" + orderId);
            wait.until(ExpectedConditions.visibilityOfElementLocated(PANEL));
            panel = driver.findElement(PANEL);
            assertTrue(panel.getText().contains("Receipt and warranty card"), panel.getText());
            assertTrue(panel.getText().contains("Download receipt"), panel.getText());
            assertTrue(panel.getText().contains("Download warranty card"), panel.getText());
            assertTrue(panel.getText().contains("not an electronic invoice"), panel.getText());
            String english = textOf(download(api, "/api/v1/orders/" + orderId + "/receipt?lang=en").body());
            assertTrue(english.contains("SALES RECEIPT") && english.contains("Subtotal") && !english.contains("Tiền hàng"), english);
            assertTrue(textOf(download(api, "/api/v1/orders/" + orderId + "/warranty-card?lang=en").body()).contains("WARRANTY CARD"));
            // the customer's own button sends no lang parameter: the file follows the language of the page (the cookie), so the saved file is English too
            Set<String> savedBefore = pdfNames(downloads);
            clickDownload("/receipt");
            Path savedEnglish = newSaved(downloads, "receipt-" + orderNumber, savedBefore);
            assertFalse(driver.findElement(By.id("notice")).getAttribute("class").contains("error"), notice());
            String savedText = textOf(Files.readAllBytes(savedEnglish));
            assertTrue(savedText.contains("SALES RECEIPT") && !savedText.contains("PHIẾU BÁN HÀNG"), savedText);
        } finally {
            // the language is saved in the profile of the shared test customer: always put Vietnamese back, whatever the asserts above found
            driver.get(baseUrl + "/lang/vi?back=/customer/orders/" + orderId);
        }

        // 7. a phone: the panel fits the screen
        try {
            driver.manage().window().setSize(new org.openqa.selenium.Dimension(375, 812));
            openOrder(orderId);
            assertTrue((Boolean) js("return document.documentElement.scrollWidth <= window.innerWidth + 1"), "no sideways scroll on a phone");
            assertTrue(driver.findElement(PANEL).isDisplayed());
        } finally {
            // a failed phone check must not leave the later steps at phone width
            driver.manage().window().setSize(new org.openqa.selenium.Dimension(1366, 900));
        }

        // 8. a stranger gets the same 404 as a missing order, a visitor 401, the staff may open it
        HttpClient stranger = newClient();
        String other = "selenium" + System.currentTimeMillis() + "x";
        call(stranger, "POST", "/api/v1/auth/register", Map.of("username", other, "email", other + "@example.com", "password", PASSWORD,
            "firstName", "Other", "lastName", "Customer"));
        call(stranger, "POST", "/api/v1/auth/login", Map.of("usernameOrEmail", other, "password", PASSWORD));
        try {
            assertEquals(404, download(stranger, "/api/v1/orders/" + orderId + "/receipt").statusCode());
            assertEquals(404, download(stranger, "/api/v1/orders/" + orderId + "/warranty-card").statusCode());
        } finally {
            // the stranger is a throw-away account: erase it now, the test customer is erased at the end of the class (eraseCustomerWhenDone)
            try {
                call(stranger, "DELETE", "/api/v1/auth/account", Map.of("password", PASSWORD, "confirmEmail", other + "@example.com"));
            } catch (Exception e) {
                System.err.println("Could not erase the account " + other + ": " + e.getMessage());
            }
        }
        assertEquals(401, download(newClient(), "/api/v1/orders/" + orderId + "/receipt").statusCode());
        assertEquals(401, download(newClient(), "/api/v1/orders/" + orderId + "/warranty-card").statusCode());
        assertPdf(download(manager, "/api/v1/orders/" + orderId + "/receipt"));
        assertPdf(download(manager, "/api/v1/orders/" + orderId + "/warranty-card"));

        // 9. the staff see the same panel on the order page
        logInAsManager();
        driver.get(baseUrl + "/manager/orders/" + orderId);
        wait.until(ExpectedConditions.visibilityOfElementLocated(PANEL));
        assertTrue(driver.findElement(PANEL).getText().contains("Tải phiếu bảo hành"), driver.findElement(PANEL).getText());
        // the staff pick the language of the customer: a NEW file is saved, and reading it shows that it is English (the page itself is Vietnamese)
        new org.openqa.selenium.support.ui.Select(driver.findElement(By.id("docLang"))).selectByValue("en");
        Set<String> before = pdfNames(downloads);
        clickDownload("/receipt");
        assertEquals("Đã tải tệp PDF.", notice());
        String staffText = textOf(Files.readAllBytes(newSaved(downloads, "receipt-" + orderNumber, before)));
        assertTrue(staffText.contains("SALES RECEIPT") && !staffText.contains("PHIẾU BÁN HÀNG"), staffText);

        // 10. send the email again: the Selenium app has no mail server, so the notice says why nothing was sent (and it is red); the button is usable again afterwards
        js("var n=document.querySelector('#notice');n.hidden=true;n.textContent='';");
        click(By.id("resendDocuments"));
        String resendNotice = notice();
        assertTrue(resendNotice.contains("Chưa bật email"), "the 409 text of a shop without mail: " + resendNotice);
        assertTrue(driver.findElement(By.id("notice")).getAttribute("class").contains("error"));
        wait.until(d -> d.findElement(By.id("resendDocuments")).isEnabled());
        assertEquals("Gửi lại email phiếu", driver.findElement(By.id("resendDocuments")).getText());

        // 11. the staff panel on a phone: the language select, both downloads and the resend button fit the screen
        try {
            driver.manage().window().setSize(new org.openqa.selenium.Dimension(375, 812));
            driver.get(baseUrl + "/manager/orders/" + orderId);
            wait.until(ExpectedConditions.visibilityOfElementLocated(PANEL));
            assertTrue((Boolean) js("return document.documentElement.scrollWidth <= window.innerWidth + 1"), "no sideways scroll on a phone");
            assertTrue((Boolean) js("return [...document.querySelectorAll('#orderDocuments select, #orderDocuments button')].length === 4 "
                + "&& [...document.querySelectorAll('#orderDocuments select, #orderDocuments button')].every(e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.left >= 0 && r.right <= window.innerWidth + 1; })"),
                "the select and three buttons are all inside the screen");
        } finally {
            driver.manage().window().setSize(new org.openqa.selenium.Dimension(1366, 900));
        }
    }

    @Test
    void aStorePickupOrderNamesTheShowroomAndTheAdminMayOpenTheFiles() throws Exception {
        HttpClient manager = managerApi();
        Map<Long, Integer> stockBefore = stockOf(productSlug);
        try {
            long orderId = placePickupOrderOverRest();
            String showroom = listOf(call(api, "GET", "/api/v1/showrooms", null)).iterator().next().path("name").asText();
            for (String status : new String[] {"CONFIRMED", "PROCESSING"}) {
                call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", status));
            }
            call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", "READY_FOR_PICKUP"));
            enterUnits(manager, orderId);
            call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", "DELIVERED"));

            String receipt = textOf(download(api, "/api/v1/orders/" + orderId + "/receipt").body());
            assertTrue(receipt.contains("Nhận tại cửa hàng") && receipt.contains(showroom), "the pick-up place is the showroom: " + receipt);
            String card = textOf(download(api, "/api/v1/orders/" + orderId + "/warranty-card").body());
            assertTrue(card.contains(showroom), "the card names the showroom: " + card);

            String adminUser = System.getProperty("selenium.adminUser", "");
            String adminPassword = System.getProperty("selenium.adminPassword", "");
            if (!adminUser.isBlank() && !adminPassword.isBlank()) {
                HttpClient admin = newClient();
                call(admin, "POST", "/api/v1/auth/login", Map.of("usernameOrEmail", adminUser, "password", adminPassword));
                assertPdf(download(admin, "/api/v1/orders/" + orderId + "/receipt"));
                assertPdf(download(admin, "/api/v1/orders/" + orderId + "/warranty-card"));
            }
        } finally {
            restoreStock(manager, productSlug, stockBefore);
        }
    }

    @Test
    void aCancelledOrderHasNoReceiptAndShowsNoPanel() throws Exception {
        HttpClient manager = managerApi();
        Map<Long, Integer> stockBefore = stockOf(productSlug);
        try {
            String orderNumber = placePayLaterOrder();
            long orderId = findOrder(orderNumber).path("id").asLong();
            call(api, "POST", "/api/v1/orders/" + orderId + "/cancellation-request", Map.of("reason", "Selenium test"));

            assertEquals(409, download(api, "/api/v1/orders/" + orderId + "/receipt").statusCode());
            driver.get(baseUrl + "/customer/orders/" + orderId);
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("orderTimeline")));
            assertTrue(driver.findElements(PANEL).isEmpty(), "a cancelled order offers no documents");
        } finally {
            restoreStock(manager, productSlug, stockBefore);
        }
    }

    @Test
    void aRefusedDownloadShowsItsReasonInTheNoticeNotAJsonPage() throws Exception {
        HttpClient manager = managerApi();
        Map<Long, Integer> stockBefore = stockOf(productSlug);
        try {
            String orderNumber = placePayLaterOrder();
            long orderId = findOrder(orderNumber).path("id").asLong();
            openOrder(orderId);
            // the order is cancelled behind the page's back, as another tab or the staff could do
            call(api, "POST", "/api/v1/orders/" + orderId + "/cancellation-request", Map.of("reason", "Selenium test"));

            clickDownload("/receipt");

            String message = notice();
            assertTrue(message.contains("đã hủy"), message);
            assertTrue(driver.findElement(By.id("notice")).getAttribute("class").contains("error"));
            assertEquals(List.of(), driver.findElements(By.cssSelector("pre")), "no raw JSON page opened");

            // the words of a server error follow the language of the page: the cookie alone switches them, the page is not reloaded
            driver.manage().addCookie(new org.openqa.selenium.Cookie.Builder("ebike_lang", "en").path("/").build());
            clickDownload("/receipt");
            String english = notice();
            assertTrue(english.contains("cancelled") && !english.contains("đã hủy"), english);
            assertTrue(driver.findElement(By.id("notice")).getAttribute("class").contains("error"));
        } finally {
            driver.manage().deleteCookieNamed("ebike_lang");
            restoreStock(manager, productSlug, stockBefore);
        }
    }

    /** The slug of a product that is sold without frame numbers (a battery or an accessory) and has stock, or null when the shop sells none. */
    private static String productWithoutNumbers() throws Exception {
        for (JsonNode summary : listOf(call(api, "GET", "/api/v1/products", null))) {
            if (summary.path("stockQuantity").asInt() < 1) continue;
            String type = call(api, "GET", "/api/v1/products/" + summary.path("slug").asText(), null).path("specification").path("vehicleType").asText("");
            if (type.equals("BATTERY") || type.equals("ACCESSORY")) return summary.path("slug").asText();
        }
        return null;
    }

    @Test
    void aDeliveredOrderWithNothingToNumberHasTheReceiptAndSaysThereIsNoWarrantyCard() throws Exception {
        String slug = productWithoutNumbers();
        assumeTrue(slug != null, "the shop sells no battery or accessory in stock");
        HttpClient manager = managerApi();
        Map<Long, Integer> stockBefore = stockOf(slug);
        try {
            long orderId = placePickupOrderOverRest(slug);
            for (String status : new String[] {"CONFIRMED", "PROCESSING", "READY_FOR_PICKUP", "DELIVERED"}) {
                call(manager, "PATCH", "/api/v1/orders/" + orderId + "/status", Map.of("status", status));
            }
            openOrder(orderId);
            String text = driver.findElement(PANEL).getText();
            assertTrue(text.contains("Tải hóa đơn"), text);
            assertTrue(text.contains("Đơn này không có xe cần phiếu bảo hành"), "an order with no vehicle is not told that numbers are missing: " + text);
            assertFalse(text.contains("chưa có số khung"), text);
            assertTrue(driver.findElements(By.cssSelector("#orderDocuments [data-doc-url*='warranty-card']")).isEmpty(), "no card button");
            assertEquals(409, download(api, "/api/v1/orders/" + orderId + "/warranty-card").statusCode());
            assertPdf(download(api, "/api/v1/orders/" + orderId + "/receipt"));
        } finally {
            restoreStock(manager, slug, stockBefore);
        }
    }
}
