package com.ebike.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * V28: Excel export of the Manager's order list and revenue report. The manager opens the order list, searches for an order and exports: the file holds only
 * that order and the Filters sheet records the search; on the revenue report the chosen dates reach the file, a wrong range shows a red notice and no file;
 * the cancellation figures of the summary include a cancelled order; the English page gives an English file; a customer and a visitor are refused (403 and
 * 401) and a customer's own order page has no such button; on a phone the button fits the screen. The rate limiter is off in Selenium runs, so the 429 notice
 * is not covered here.
 * Needs {@code -Dselenium.managerUser} and {@code -Dselenium.managerPassword}; skipped otherwise.
 *
 * <pre>
 * mvn test -Dtest=ExcelExportSeleniumTest -Dselenium.baseUrl=http://localhost:8080 \
 *     -Dselenium.managerUser=... -Dselenium.managerPassword=...
 * </pre>
 */
@Tag("selenium")
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("product-stock")
@EnabledIfSystemProperty(named = "selenium.baseUrl", matches = "https?://.+")
class ExcelExportSeleniumTest extends StorefrontSeleniumTest {

    private static final By EXPORT = By.id("export-excel");

    // ---- helpers ----

    private void logInAsManager() {
        logIn(System.getProperty("selenium.managerUser"), System.getProperty("selenium.managerPassword"));
    }

    private Path downloadFolder() throws IOException {
        Path folder = Files.createTempDirectory("selenium-xlsx");
        ((ChromeDriver) driver).executeCdpCommand("Browser.setDownloadBehavior", Map.<String, Object>of("behavior", "allow", "downloadPath", folder.toString()));
        return folder;
    }

    /** Waits for a finished .xlsx that starts with {@code prefix} (Chrome writes a .crdownload file until it is done) and returns it. */
    private Path savedFile(Path folder, String prefix) {
        try {
            return wait.until(d -> {
                try (Stream<Path> files = Files.list(folder)) {
                    return files.filter(file -> file.getFileName().toString().startsWith(prefix) && file.getFileName().toString().endsWith(".xlsx")).findFirst().orElse(null);
                } catch (IOException e) {
                    return null;
                }
            });
        } catch (org.openqa.selenium.TimeoutException e) {
            throw new AssertionError("no " + prefix + "*.xlsx was saved; notice: " + driver.findElement(By.id("notice")).getText(), e);
        }
    }

    private static Workbook read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return new XSSFWorkbook(in);
        }
    }

    private static Workbook read(byte[] bytes) throws IOException {
        return new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes));
    }

    private static String cell(Sheet sheet, int row, int column) {
        return sheet.getRow(row).getCell(column).getStringCellValue();
    }

    private static HttpResponse<byte[]> fetch(HttpClient client, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(30))
            .header("Accept", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, application/json").GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private void clearNotice() {
        js("var n=document.querySelector('#notice');n.hidden=true;n.textContent='';n.classList.remove('error');");
    }

    private void setFilter(String name, String value, String event) {
        js("var i=document.querySelector('#filters [name=" + name + "]');i.value=arguments[0];i.dispatchEvent(new Event('" + event + "',{bubbles:true}));", value);
    }

    // ---- scenarios ----

    @Test
    void theManagerExportsTheOrderListThatIsOnScreen() throws Exception {
        String orderNumber = placePayLaterOrder();
        placePayLaterOrder(); // a second order, so that the search has something to leave out
        Path folder = downloadFolder();
        logInAsManager();
        driver.get(baseUrl + "/manager/orders");
        wait.until(ExpectedConditions.visibilityOfElementLocated(EXPORT));
        assertEquals("Xuất Excel", driver.findElement(EXPORT).getText());

        driver.findElement(By.cssSelector("#filters [name=search]")).sendKeys(orderNumber);
        wait.until(d -> d.findElements(By.cssSelector("#data-table tbody tr")).size() == 1
            && d.findElement(By.cssSelector("#data-table tbody")).getText().contains(orderNumber));

        clearNotice();
        click(EXPORT);
        Path file = savedFile(folder, "orders_");
        assertEquals("Đã tải tệp Excel.", notice());
        assertTrue(driver.findElement(EXPORT).isEnabled(), "the button is usable again");
        assertEquals("Xuất Excel", driver.findElement(EXPORT).getText());

        try (Workbook workbook = read(file)) {
            Sheet orders = workbook.getSheetAt(0);
            assertEquals("Đơn hàng", orders.getSheetName());
            assertEquals(2, orders.getPhysicalNumberOfRows(), "the header and the one order that matches the search");
            assertEquals(orderNumber, cell(orders, 1, 0));
            assertEquals("Có tài khoản", cell(orders, 1, 3));
            assertEquals(orderNumber, cell(workbook.getSheetAt(1), 1, 1), "the Filters sheet records the search");
        }
    }

    @Test
    void anOrderListWithNoMatchStillExportsAFileWithAMessage() throws Exception {
        Path folder = downloadFolder();
        logInAsManager();
        driver.get(baseUrl + "/manager/orders");
        wait.until(ExpectedConditions.visibilityOfElementLocated(EXPORT));
        driver.findElement(By.cssSelector("#filters [name=search]")).sendKeys("no-such-order-" + System.currentTimeMillis());
        wait.until(d -> d.findElement(By.id("empty")).isDisplayed());

        click(EXPORT);
        Path file = savedFile(folder, "orders_");
        try (Workbook workbook = read(file)) {
            assertEquals("Mã đơn", cell(workbook.getSheetAt(0), 0, 0));
            assertEquals("Không có đơn hàng nào khớp với bộ lọc này.", cell(workbook.getSheetAt(0), 1, 0));
        }
    }

    @Test
    void theRevenueReportExportFollowsTheDatesOnScreenAndRefusesAWrongRange() throws Exception {
        Path folder = downloadFolder();
        logInAsManager();
        driver.get(baseUrl + "/manager/reports");
        wait.until(ExpectedConditions.visibilityOfElementLocated(EXPORT));
        wait.until(d -> d.findElement(By.id("metrics")).getText().length() > 0);

        setFilter("from", "2026-01-01", "change");
        setFilter("to", "2026-01-31", "change");
        wait.until(d -> d.findElement(By.id("metrics")).isDisplayed());
        clearNotice();
        click(EXPORT);
        Path file = savedFile(folder, "revenue-report_2026-01-01_2026-01-31");
        try (Workbook workbook = read(file)) {
            assertEquals(4, workbook.getNumberOfSheets());
            assertEquals("Tổng quan", workbook.getSheetName(0));
            assertEquals("2026-01-01", cell(workbook.getSheetAt(0), 3, 1));
            assertEquals("2026-01-31", cell(workbook.getSheetAt(0), 4, 1));
        }

        // from after to: the server refuses, the notice is red and no second file appears
        setFilter("from", "2026-03-01", "change");
        setFilter("to", "2026-02-01", "change");
        clearNotice();
        click(EXPORT);
        String refusal = notice();
        assertTrue(driver.findElement(By.id("notice")).getAttribute("class").contains("error"), "the refusal is red: " + refusal);
        assertTrue(driver.findElement(EXPORT).isEnabled(), "the button is usable again after a refusal");
        try (Stream<Path> files = Files.list(folder)) {
            assertEquals(1, files.filter(f -> f.getFileName().toString().endsWith(".xlsx")).count(), "the refused request saved nothing");
        }
    }

    @Test
    void theSummaryCountsACancelledOrder() throws Exception {
        HttpClient manager = managerApi();
        placePayLaterOrder();
        String cancelled = placePayLaterOrder();
        call(api, "POST", "/api/v1/orders/" + findOrder(cancelled).path("id").asLong() + "/cancellation-request", Map.of("reason", "Selenium export test"));

        String today = LocalDate.now().toString();
        HttpResponse<byte[]> response = fetch(manager, "/api/v1/manager/revenue-report/export?period=day&from=" + today + "&to=" + today + "&lang=vi");
        assertEquals(200, response.statusCode(), new String(response.body()));
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", response.headers().firstValue("Content-Type").orElse(""));
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"), "never cached");
        try (Workbook workbook = read(response.body())) {
            Sheet summary = workbook.getSheetAt(0);
            double placed = summary.getRow(7).getCell(1).getNumericCellValue();
            double cancelledCount = summary.getRow(10).getCell(1).getNumericCellValue();
            double rate = summary.getRow(11).getCell(1).getNumericCellValue();
            assertTrue(cancelledCount >= 1, "the cancelled order is counted");
            assertTrue(placed >= cancelledCount + 1, "the other order of this test is not cancelled");
            assertEquals(cancelledCount / placed, rate, 1e-9);
        }
    }

    @Test
    void theEnglishPageGivesAnEnglishFile() throws Exception {
        Path folder = downloadFolder();
        logInAsManager();
        try {
            driver.get(baseUrl + "/lang/en?back=/manager/reports");
            wait.until(ExpectedConditions.visibilityOfElementLocated(EXPORT));
            assertEquals("Export to Excel", driver.findElement(EXPORT).getText());
            wait.until(d -> d.findElement(By.id("metrics")).getText().length() > 0);

            clearNotice();
            click(EXPORT);
            Path file = savedFile(folder, "revenue-report_");
            assertEquals("Excel file downloaded.", notice());
            try (Workbook workbook = read(file)) {
                assertEquals("Summary", workbook.getSheetName(0));
                assertEquals("Best sellers", workbook.getSheetName(2));
                assertEquals("KINETIC revenue report", cell(workbook.getSheetAt(0), 0, 0));
            }
        } finally {
            driver.get(baseUrl + "/lang/vi?back=/");
        }
    }

    @Test
    void aCustomerAndAVisitorCannotExportAndSeeNoButton() throws Exception {
        assertEquals(403, fetch(api, "/api/v1/manager/orders/export").statusCode());
        assertEquals(403, fetch(api, "/api/v1/manager/revenue-report/export").statusCode());
        assertEquals(401, fetch(newClient(), "/api/v1/manager/orders/export").statusCode());
        assertEquals(401, fetch(newClient(), "/api/v1/manager/revenue-report/export").statusCode());

        driver.get(baseUrl + "/customer/orders");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("refresh")));
        assertFalse(driver.findElement(EXPORT).isDisplayed(), "a customer's order list has no export button");
    }

    @Test
    void theButtonFitsAPhoneScreen() {
        logInAsManager();
        try {
            driver.manage().window().setSize(new org.openqa.selenium.Dimension(375, 812));
            for (String page : new String[] {"/manager/orders", "/manager/reports"}) {
                driver.get(baseUrl + page);
                wait.until(ExpectedConditions.visibilityOfElementLocated(EXPORT));
                assertTrue((Boolean) js("return document.documentElement.scrollWidth <= window.innerWidth + 1"), "no sideways scroll on a phone: " + page);
                assertTrue((Boolean) js("var r=document.querySelector('#export-excel').getBoundingClientRect();return r.width>0&&r.left>=0&&r.right<=window.innerWidth+1"),
                    "the export button is inside the screen: " + page);
            }
        } finally {
            driver.manage().window().setSize(new org.openqa.selenium.Dimension(1366, 900));
        }
    }
}
