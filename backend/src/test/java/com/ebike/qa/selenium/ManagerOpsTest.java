package com.ebike.qa.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.qa.selenium.pages.Page;
import com.ebike.qa.selenium.pages.WorkspacePage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;

@EnabledIfSystemProperty(named = "qa.baseUrl", matches = ".+")
class ManagerOpsTest extends QaBase {

    // ---------------------------------------------------------------- dashboard

    @Test
    void dashboardNumbersMatchTheApi() {
        Api manager = Api.staff("manager");
        JsonNode before = manager.get("/api/v1/manager/dashboard").json();
        loginStaff("manager");
        WorkspacePage w = new WorkspacePage(ui).waitLoaded();
        JsonNode after = manager.get("/api/v1/manager/dashboard").json();
        assertEquals("Tổng quan cửa hàng", w.title());
        long shown = metric(w, "Tổng đơn hàng");
        assertTrue(shown >= before.get("totalOrders").asLong() && shown <= after.get("totalOrders").asLong(), "total orders " + shown);
        long pending = metric(w, "Đơn đang chờ");
        assertTrue(pending >= Math.min(before.get("pendingOrders").asLong(), after.get("pendingOrders").asLong()) && pending <= Math.max(before.get("pendingOrders").asLong(), after.get("pendingOrders").asLong()), "pending " + pending);
        assertTrue(w.metricTexts().size() >= 5, w.metricTexts().toString());
        assertTrue(w.metricTexts().stream().anyMatch(t -> t.contains("₫")), "revenue figures are in dong");
        assertTrue(w.rows().size() <= 10 && w.rows().size() >= 1, "recent orders");
    }

    private long metric(WorkspacePage w, String label) {
        return w.metricTexts().stream().filter(t -> t.startsWith(label)).map(t -> Page.money(t.substring(label.length()))).findFirst().orElseThrow(() -> new AssertionError("metric missing: " + label + " in " + w.metricTexts()));
    }

    // ---------------------------------------------------------------- customers

    @Test
    void customerSearchFindsANewCustomer() {
        Customer c = newCustomer();
        loginStaff("manager");
        WorkspacePage w = new WorkspacePage(ui).open("/manager/customers");
        w.search("search", c.username());
        WebElement row = w.waitRow(c.username());
        assertEquals("Kiểm Thử", w.cell(row, "customerName"));
        assertEquals(c.email(), w.cell(row, "email"));
        assertEquals("0", w.cell(row, "orderCount"));
        w.search("search", "zzzz-nobody-" + System.nanoTime());
        ui.until(d -> w.rows().isEmpty());
    }

    // ---------------------------------------------------------------- inventory

    @Test
    void stockChangeThroughTheColourDialogIsLoggedInTheHistory() {
        loginStaff("manager");
        WorkspacePage w = new WorkspacePage(ui).open("/manager/inventory");
        WebElement row = w.waitRow("DIBAO R1");
        ui.click(w.button(row, "stock"));
        By input = By.cssSelector("dialog.stock-dialog tr[data-variant] input");
        int current = Integer.parseInt(ui.prop(input, "value"));
        String note = "QA kiểm kê " + System.nanoTime();
        ui.type(By.cssSelector("dialog.stock-dialog [data-note]"), note);
        ui.type(input, String.valueOf(current + 1));
        ui.click(By.cssSelector("dialog.stock-dialog [data-save]"));
        w.waitNotice("Đã cập nhật tồn kho.");
        ui.click(By.cssSelector("dialog.stock-dialog [data-history]"));
        ui.visible(By.cssSelector("dialog.stock-history-dialog [data-lines]"));
        ui.waitText(By.cssSelector("dialog.stock-history-dialog [data-lines]"), note);
        String lines = ui.text(By.cssSelector("dialog.stock-history-dialog [data-lines]"));
        assertTrue(lines.contains("Điều chỉnh") && lines.contains("+1"), lines);
        ui.click(By.cssSelector("dialog.stock-history-dialog [data-close]"));
        // put the stock back so that the shop's numbers stay as they were
        ui.type(input, String.valueOf(current));
        ui.click(By.cssSelector("dialog.stock-dialog [data-save]"));
        ui.until(d -> ui.textOf(WorkspacePage.NOTICE).contains("Đã cập nhật tồn kho."));
    }

    @Test
    void stockDialogRefusesNegativeNumbers() {
        loginStaff("manager");
        WorkspacePage w = new WorkspacePage(ui).open("/manager/inventory");
        ui.click(w.button(w.waitRow("DIBAO R1"), "stock"));
        ui.type(By.cssSelector("dialog.stock-dialog tr[data-variant] input"), "-4");
        ui.click(By.cssSelector("dialog.stock-dialog [data-save]"));
        w.waitNotice("Số lượng tồn kho phải là số nguyên không âm.");
    }

    @Test
    void stockFilterShowsOnlyMatchingRows() {
        loginStaff("manager");
        WorkspacePage w = new WorkspacePage(ui).open("/manager/inventory");
        int all = w.rows().size();
        assertTrue(all >= 30, "all products listed: " + all);
        ui.selectValue(By.cssSelector("#filters [name=stockStatus]"), "OUT");
        ui.until(d -> w.rows().size() < all || ui.shown(WorkspacePage.EMPTY));
        ui.until(20, d -> w.rows().stream().allMatch(r -> "Hết hàng".equals(w.cell(r, "stockStatus"))));
        ui.selectValue(By.cssSelector("#filters [name=stockStatus]"), "OK");
        ui.until(d -> !w.rows().isEmpty());
        ui.until(20, d -> w.rows().stream().allMatch(r -> "Còn hàng".equals(w.cell(r, "stockStatus"))));
    }

    @Test
    void stockReceiptCreatesALineInTheList() {
        loginStaff("manager");
        WorkspacePage w = new WorkspacePage(ui).open("/manager/stock-receipts");
        assertTrue(w.sidebar().contains("/manager/stock-receipts"));
        String supplier = "QA Supplier " + System.nanoTime();
        ui.click(By.xpath("//button[contains(.,'Tạo phiếu nhập')]"));
        ui.type(By.cssSelector("dialog.receipt-form [name=supplier]"), supplier);
        new Select(ui.visible(By.cssSelector("dialog.receipt-form [data-product]"))).selectByVisibleText("DIBAO R1");
        By colour = By.cssSelector("dialog.receipt-form [data-colour]");
        ui.until(d -> ui.present(colour).isEnabled());
        new Select(ui.present(colour)).selectByIndex(1);
        ui.type(By.cssSelector("dialog.receipt-form [data-qty]"), "1");
        ui.until(d -> ui.present(By.cssSelector("dialog.receipt-form [data-submit]")).isEnabled());
        ui.click(By.cssSelector("dialog.receipt-form [data-submit]"));
        ui.until(20, d -> ui.exists(By.cssSelector("#data-table tbody")) && ui.textOf(By.cssSelector("#data-table tbody")).contains(supplier));
        assertTrue(ui.textOf(By.cssSelector("#data-table tbody")).contains("PN-"), "receipts carry a PN- number");
    }

    // ---------------------------------------------------------------- support

    @Test
    void supportRequestIsTakenAndResolvedWithNotes() {
        String subject = "Hỏi bảo dưỡng " + System.nanoTime();
        ObjectNode body = Api.JSON.createObjectNode().put("customerName", "Khách Hỏi").put("email", "ask@example.com").put("phone", "0901234567").put("subject", subject).put("message", "Xe cần bảo dưỡng định kỳ ở đâu, mất bao lâu?");
        Api.Reply sent = new Api().post("/api/v1/support-requests", body);
        assertTrue(sent.status() < 300, sent.body());
        loginStaff("manager");
        WorkspacePage w = new WorkspacePage(ui).open("/manager/support");
        WebElement row = w.waitRow(subject);
        assertEquals("Mới", w.cell(row, "status"));
        ui.click(w.button(row, "support-status"));
        assertTrue(ui.answerPrompt("Đã nhận, sẽ gọi lại").startsWith("Ghi chú xử lý"));
        ui.until(20, d -> "Đang xử lý".equals(w.cellOf(subject, "status")));
        assertEquals("Đã nhận, sẽ gọi lại", w.cellOf(subject, "staffNote"));
        WebElement resolve = w.rowWith(subject).findElement(By.cssSelector("[data-action=support-status][data-status=RESOLVED]"));
        ui.click(resolve);
        ui.answerPrompt("Đã trả lời qua email");
        ui.until(20, d -> "Đã giải quyết".equals(w.cellOf(subject, "status")));
        assertTrue(w.rowWith(subject).getText().contains("Mở lại"), "a resolved request can be re-opened");
    }

    @Test
    void supportListDoesNotExecuteHtmlInTheSubject() {
        loginStaff("manager");
        new WorkspacePage(ui).open("/manager/support");
        assertNotNull(ui.js("return document.title;"));
        assertFalse(String.valueOf(ui.js("return document.title;")).contains("XSS-FIRED"), "markup typed by a customer must be shown as text");
        assertEquals(Boolean.FALSE, ui.js("return window.__xss === 1;"));
    }

    // ---------------------------------------------------------------- warranty

    @Test
    void warrantyClaimTravelsFromCustomerToManagerQueue() {
        Customer c = newCustomer();
        Api api = customerApi(c);
        JsonNode order = api.placeOrder("Kiểm Thử", c.email(), Shop.buySlug(), 1, "PAY_LATER");
        String frame = Flow.deliver(order);
        loginCustomer(c);
        WorkspacePage w = new WorkspacePage(ui).open("/customer/warranty");
        WebElement unit = w.waitRow(frame);
        assertEquals("Còn bảo hành", w.cell(unit, "coverage"));
        ui.click(w.button(unit, "warranty-claim"));
        // a description shorter than 10 characters is refused inside the dialog
        ui.dialogAnswer("lỗi");
        ui.waitText(By.cssSelector("dialog[open] .support-message"), "Cần nhập ít nhất 10 ký tự.");
        ui.dialogAnswer("Đèn xi nhan chập chờn khi trời mưa");
        w.waitNotice("Đã gửi yêu cầu bảo hành.");
        ui.waitText(By.id("warrantyClaims"), frame);

        ui.d.manage().deleteAllCookies();
        loginStaff("manager");
        WorkspacePage q = new WorkspacePage(ui).open("/manager/warranty");
        WebElement claim = q.waitRow(frame);
        assertEquals("Mới gửi", q.cell(claim, "status"));
        advance(q, frame, "RECEIVED", "Đã tiếp nhận", "Đã nhận xe vào xưởng");
        advance(q, frame, "REPAIRING", "Đang sửa", "");
        advance(q, frame, "DONE", "Đã hoàn tất", "Đã thay bộ nháy");
        q.open("/manager/warranty");
        ui.selectValue(By.cssSelector("#filters [name=status]"), "ALL");
        assertEquals("Đã hoàn tất", q.cell(q.waitRow(frame), "status"));
    }

    private void advance(WorkspacePage q, String frame, String status, String shown, String note) {
        ui.click(q.rowWith(frame).findElement(By.cssSelector("[data-action=warranty-status][data-status=" + status + "]")));
        ui.dialogAnswer(note);
        q.waitNotice("Đã cập nhật yêu cầu bảo hành.");
        ui.until(20, d -> q.rowWith(frame) == null || shown.equals(q.cellOf(frame, "status")));
        ui.js("document.getElementById('notice').hidden=true;document.getElementById('notice').textContent='';");
    }

    @Test
    void warrantyRejectNeedsAReason() {
        Customer c = newCustomer();
        Api api = customerApi(c);
        String frame = Flow.deliver(api.placeOrder("Kiểm Thử", c.email(), Shop.buySlug(), 1, "PAY_LATER"));
        api.post("/api/v1/warranty/claims", Map.of("unitId", api.get("/api/v1/warranty/units").json().get(0).get("id").asLong(), "description", "Pin sạc không vào điện"));
        loginStaff("manager");
        WorkspacePage q = new WorkspacePage(ui).open("/manager/warranty");
        WebElement claim = q.waitRow(frame);
        ui.click(claim.findElement(By.cssSelector("[data-action=warranty-status][data-status=REJECTED]")));
        ui.dialogAnswer("");
        ui.waitText(By.cssSelector("dialog[open] .support-message"), "Cần nhập ít nhất 1 ký tự.");
        ui.dialogAnswer("Hỏng do ngấm nước");
        q.waitNotice("Đã cập nhật yêu cầu bảo hành.");
    }

    // ---------------------------------------------------------------- installments

    private JsonNode installmentOrder(Api customer, String email) {
        ObjectNode body = Api.orderBody("Kiểm Thử", email, "0901234567", customer.product(Shop.buySlug()), 1, "INSTALLMENT");
        body.set("installment", Api.JSON.createObjectNode().put("idNumber", "079201234567").put("monthlyIncome", 30_000_000).put("downPaymentPercent", 30).put("termMonths", 6));
        Api.Reply r = customer.post("/api/v1/orders", body);
        assertTrue(r.status() < 300, r.body());
        return r.json();
    }

    @Test
    void installmentApplicationIsApprovedFromTheQueue() {
        Customer c = newCustomer();
        JsonNode o = installmentOrder(customerApi(c), c.email());
        loginStaff("manager");
        WorkspacePage w = new WorkspacePage(ui).open("/manager/installments");
        WebElement row = w.waitRow(o.get("orderNumber").asText());
        assertEquals("Chờ duyệt", w.cell(row, "status"));
        assertEquals("6 tháng", w.cell(row, "termMonths"));
        ui.click(row.findElement(By.cssSelector("[data-action=installment-decide][data-decision=APPROVE]")));
        ui.dialogAnswer("Hồ sơ đủ điều kiện");
        w.waitNotice("Đã duyệt hồ sơ trả góp.");
        ui.until(20, d -> w.rowWith(o.get("orderNumber").asText()) == null);
        ui.selectValue(By.cssSelector("#filters [name=status]"), "APPROVED");
        assertEquals("Đã duyệt", w.cell(w.waitRow(o.get("orderNumber").asText()), "status"));
    }

    @Test
    void installmentRejectNeedsAReasonAndCancelsTheOrder() {
        Customer c = newCustomer();
        JsonNode o = installmentOrder(customerApi(c), c.email());
        loginStaff("manager");
        WorkspacePage w = new WorkspacePage(ui).open("/manager/installments");
        WebElement row = w.waitRow(o.get("orderNumber").asText());
        ui.click(row.findElement(By.cssSelector("[data-action=installment-decide][data-decision=REJECT]")));
        ui.dialogAnswer("");
        ui.waitText(By.cssSelector("dialog[open] .support-message"), "Vui lòng nhập nội dung.");
        ui.dialogAnswer("Thu nhập chưa đủ");
        w.waitNotice("Đã từ chối hồ sơ trả góp và hủy đơn hàng.");
        assertEquals("CANCELLED", Api.staff("manager").get("/api/v1/orders/" + o.get("id").asLong()).json().get("status").asText());
    }

    // ---------------------------------------------------------------- test rides and reports

    @Test
    void revenueReportRangeAndExcelExport() throws Exception {
        loginStaff("manager");
        WorkspacePage w = new WorkspacePage(ui).open("/manager/reports");
        LocalDate today = LocalDate.now();
        ui.setValue(By.cssSelector("#filters [name=from]"), today.minusDays(2).toString());
        ui.setValue(By.cssSelector("#filters [name=to]"), today.toString());
        ui.until(20, d -> w.rows().size() == 3);
        assertEquals(today.minusDays(2).toString(), w.cell(w.rows().get(0), "label"));
        assertEquals(today.toString(), w.cell(w.rows().get(2), "label"));
        assertTrue(ui.exists(By.cssSelector("#report-chart svg.revenue-chart")) || ui.textOf(By.id("report-chart")).contains("Chưa có doanh thu"));
        assertTrue(ui.shown(By.id("report-products")));
        ui.selectValue(By.cssSelector("#filters [name=period]"), "month");
        ui.until(20, d -> w.rows().size() <= 2 && !w.rows().isEmpty());
        assertTrue(ui.shown(By.id("export-excel")), "export button is offered to staff with the report permission");
        ui.click(By.id("export-excel"));
        w.waitNotice("Đã tải tệp Excel.");
        ui.until(20, d -> {
            try (Stream<Path> f = Files.list(downloads)) { return f.anyMatch(p -> p.getFileName().toString().endsWith(".xlsx")); } catch (Exception e) { return false; }
        });
        try (Stream<Path> f = Files.list(downloads)) {
            Path x = f.filter(p -> p.getFileName().toString().endsWith(".xlsx")).findFirst().orElseThrow();
            byte[] head = Files.readAllBytes(x);
            assertTrue(head.length > 1000 && head[0] == 'P' && head[1] == 'K', "an xlsx is a zip");
        }
    }

    @Test
    void orderListExcelExportDownloads() throws Exception {
        loginStaff("manager");
        WorkspacePage w = new WorkspacePage(ui).open("/manager/orders");
        ui.click(By.id("export-excel"));
        w.waitNotice("Đã tải tệp Excel.");
        ui.until(20, d -> {
            try (Stream<Path> f = Files.list(downloads)) { return f.anyMatch(p -> p.getFileName().toString().endsWith(".xlsx")); } catch (Exception e) { return false; }
        });
    }

    @Test
    void managerPagesAllOpenWithoutErrors() {
        loginStaff("manager");
        WorkspacePage w = new WorkspacePage(ui);
        List<String> pages = List.of("/manager", "/manager/orders", "/manager/payments", "/manager/customers", "/manager/support", "/manager/warranty", "/manager/test-rides", "/manager/installments", "/manager/inventory", "/manager/products", "/manager/reports", "/manager/stock-receipts");
        for (String p : pages) {
            w.open(p);
            assertFalse(ui.shown(WorkspacePage.NOTICE) && ui.attr(WorkspacePage.NOTICE, "class").contains("error"), p + " shows an error: " + ui.textOf(WorkspacePage.NOTICE));
            assertFalse(w.title().isBlank(), p);
        }
    }
}
