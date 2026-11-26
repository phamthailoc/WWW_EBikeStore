package com.ebike.qa.selenium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.qa.selenium.pages.WorkspacePage;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

@EnabledIfSystemProperty(named = "qa.baseUrl", matches = ".+")
class ManagerOrdersTest extends QaBase {

    private WorkspacePage ordersPageFor(JsonNode order) {
        loginStaff("manager");
        WorkspacePage w = new WorkspacePage(ui).open("/manager/orders");
        w.search("search", order.get("orderNumber").asText());
        w.waitRow(order.get("orderNumber").asText());
        ui.until(d -> w.rows().size() == 1);
        return w;
    }

    private String statusOf(WorkspacePage w, JsonNode order) {
        String s = w.cellOf(order.get("orderNumber").asText(), "status");
        return s == null ? "" : s;
    }

    private void step(WorkspacePage w, JsonNode order, String status, String expectedText) {
        w.chooseStatus(w.waitRow(order.get("orderNumber").asText()), status);
        ui.until(20, d -> expectedText.equals(statusOf(w, order)));
    }

    @Test
    void statusFlowPendingToDeliveredNeedsFrameAndEngineNumbers() {
        JsonNode o = Flow.guestOrder("PAY_LATER");
        WorkspacePage w = ordersPageFor(o);
        assertEquals("Chờ xử lý", statusOf(w, o));
        step(w, o, "CONFIRMED", "Đã xác nhận");
        step(w, o, "PROCESSING", "Đang xử lý");
        // shipping without frame/engine numbers is refused
        w.chooseStatus(w.rowWith(o.get("orderNumber").asText()), "SHIPPED");
        ui.until(d -> ui.shown(WorkspacePage.NOTICE) && ui.attr(WorkspacePage.NOTICE, "class").contains("error"));
        assertEquals("Đang xử lý", statusOf(w, o), "the order must not move without numbers");
        // enter the numbers on the order page
        long id = o.get("id").asLong();
        w.open("/manager/orders/" + id);
        ui.visible(By.id("unitsForm"));
        ui.type(By.cssSelector("#unitsForm [name=frame]"), "QAFRUI" + System.nanoTime() % 1_000_000_000L);
        ui.type(By.cssSelector("#unitsForm [name=engine]"), "QAENUI" + System.nanoTime() % 1_000_000_000L);
        ui.click(By.cssSelector("#unitsForm button"));
        w.waitNotice("Đã lưu số khung, số máy.");
        // now ship and deliver from the same page
        ui.selectValue(By.cssSelector("#orderStatusForm select[name=status]"), "SHIPPED");
        ui.click(By.cssSelector("#orderStatusForm button"));
        w.waitNotice("Trạng thái đơn hàng đã cập nhật.");
        ui.until(d -> ui.textOf(By.id("orderTimeline")).contains("Đang giao"));
        ui.selectValue(By.cssSelector("#orderStatusForm select[name=status]"), "DELIVERED");
        ui.click(By.cssSelector("#orderStatusForm button"));
        ui.until(d -> !ui.exists(By.id("orderStatusForm")));
        assertTrue(ui.text(By.id("orderDocuments")).contains("Tải phiếu bảo hành"), "delivered order offers the warranty card");
        assertEquals("DELIVERED", Api.staff("manager").get("/api/v1/orders/" + id).json().get("status").asText());
    }

    @Test
    void pickupOrderGoesThroughReadyForPickupAndEndsAsReceived() {
        JsonNode o = Flow.guestPickupOrder();
        Api manager = Api.staff("manager");
        Flow.status(manager, o, "CONFIRMED");
        Flow.status(manager, o, "PROCESSING");
        Flow.units(manager, o);
        WorkspacePage w = ordersPageFor(o);
        w.chooseStatus(w.rowWith(o.get("orderNumber").asText()), "READY_FOR_PICKUP");
        ui.until(20, d -> "Chờ lấy".equals(statusOf(w, o)));
        WebElement select = w.rowWith(o.get("orderNumber").asText()).findElement(By.cssSelector("select[data-action=order-status]"));
        assertTrue(select.getText().contains("Đã nhận"), "the last step of a pickup reads 'Đã nhận': " + select.getText());
        w.chooseStatus(w.rowWith(o.get("orderNumber").asText()), "DELIVERED");
        ui.until(20, d -> "Đã giao".equals(statusOf(w, o)));
    }

    @Test
    void managerCancelNeedsAReason() {
        JsonNode o = Flow.guestOrder("PAY_LATER");
        WorkspacePage w = ordersPageFor(o);
        w.chooseStatus(w.waitRow(o.get("orderNumber").asText()), "CANCELLED");
        assertEquals("Lý do hủy đơn hàng", ui.answerPrompt("   "));
        w.waitNotice("Vui lòng nhập lý do hủy đơn hàng.");
        assertEquals("Chờ xử lý", statusOf(w, o));
        ui.until(20, d -> Boolean.TRUE.equals(ui.js("const s=document.querySelector('select[data-action=order-status]');return !!s&&s.selectedIndex===0;")));
        w.chooseStatus(w.rowWith(o.get("orderNumber").asText()), "CANCELLED");
        ui.answerPrompt("Khách gọi hủy");
        ui.until(20, d -> "Đã hủy".equals(statusOf(w, o)));
        w.open("/manager/orders/" + o.get("id").asLong());
        ui.visible(By.id("cancellationDetails"));
        assertTrue(ui.text(By.id("cancellationDetails")).contains("Khách gọi hủy"));
    }

    @Test
    void orderListSearchFindsByEmailAndShowsNothingForGarbage() {
        JsonNode o = Flow.guestOrder("PAY_LATER");
        loginStaff("manager");
        WorkspacePage w = new WorkspacePage(ui).open("/manager/orders");
        w.search("search", o.get("customerEmail").asText());
        ui.until(d -> w.rows().size() == 1);
        assertEquals(o.get("orderNumber").asText(), w.cell(w.rows().get(0), "orderNumber"));
        w.search("search", "zzzz-no-such-order-" + System.nanoTime());
        ui.until(d -> w.rows().isEmpty());
        ui.click(By.id("clearFilters"));
        ui.until(d -> w.rows().size() > 1);
    }

    @Test
    void orderListHasExpectedColumnsAndDetailLink() {
        JsonNode o = Flow.guestOrder("PAY_LATER");
        WorkspacePage w = ordersPageFor(o);
        List<String> headers = w.headers();
        for (String h : List.of("Mã đơn hàng", "Trạng thái", "Tổng tiền", "Phương thức thanh toán")) assertTrue(headers.contains(h), headers.toString());
        String href = w.rows().get(0).findElement(By.cssSelector("a.table-action")).getDomAttribute("href");
        assertTrue(href.endsWith("/manager/orders/" + o.get("id").asLong()), href);
        ui.open(href);
                new WorkspacePage(ui).waitLoaded();
        assertTrue(ui.text(By.id("orderPricing")).contains("Tổng cộng"));
    }

    @Test
    void pdfButtonsDownloadReceiptAndWarrantyCard() throws Exception {
        JsonNode o = Flow.guestOrder("PAY_LATER");
        Flow.deliver(o);
        loginStaff("manager");
        WorkspacePage w = new WorkspacePage(ui).open("/manager/orders/" + o.get("id").asLong());
        ui.visible(By.id("orderDocuments"));
        ui.click(By.cssSelector("[data-doc-url$='/receipt']"));
        w.waitNotice("Đã tải tệp PDF.");
        waitForDownload("receipt");
        ui.js("document.getElementById('notice').textContent='';");
        ui.click(By.cssSelector("[data-doc-url$='/warranty-card']"));
        w.waitNotice("Đã tải tệp PDF.");
        waitForDownload("warranty");
        assertTrue(ui.exists(By.id("resendDocuments")), "staff can re-send the documents of a delivered order");
    }

    private void waitForDownload(String hint) throws Exception {
        ui.until(20, d -> {
            try (Stream<Path> files = Files.list(downloads)) {
                return files.anyMatch(p -> p.getFileName().toString().toLowerCase().endsWith(".pdf") && Files.exists(p));
            } catch (Exception e) { return false; }
        });
        try (Stream<Path> files = Files.list(downloads)) {
            Path pdf = files.filter(p -> p.getFileName().toString().endsWith(".pdf")).findFirst().orElseThrow();
            byte[] head = Files.readAllBytes(pdf);
            assertTrue(head.length > 500 && new String(head, 0, 5).equals("%PDF-"), "a real PDF: " + pdf);
            Files.delete(pdf);
        }
    }

    @Test
    void paymentOfPayLaterOrderIsConfirmedByManager() {
        JsonNode o = Flow.guestOrder("PAY_LATER");
        loginStaff("manager");
        WorkspacePage w = new WorkspacePage(ui).open("/manager/payments");
        w.search("search", o.get("orderNumber").asText());
        WebElement row = w.waitRow(o.get("orderNumber").asText());
        assertEquals("Chờ thanh toán", w.cell(row, "paymentStatus"));
        ui.click(w.button(row, "confirm-payment"));
        w.waitNotice("Đã cập nhật dữ liệu.");
        ui.until(20, d -> "Đã thanh toán".equals(w.cell(w.rowWith(o.get("orderNumber").asText()), "paymentStatus")));
        assertFalse(w.rowWith(o.get("orderNumber").asText()).getText().contains("Xác nhận đã thu"));
    }

    @Test
    void refundOfACancelledPaidOrderIsRecordedWithANote() {
        JsonNode o = Flow.guestOrder("PAY_LATER");
        Api manager = Api.staff("manager");
        Api.Reply paid = manager.send("PATCH", "/api/v1/manager/payments/" + manager.get("/api/v1/manager/payments?search=" + o.get("orderNumber").asText()).json().get(0).get("id").asLong() + "/confirm", java.util.Map.of());
        assertTrue(paid.status() < 300, paid.body());
        Flow.status(manager, o, "CANCELLED");
        loginStaff("manager");
        WorkspacePage w = new WorkspacePage(ui).open("/manager/payments");
        w.search("search", o.get("orderNumber").asText());
        WebElement row = w.waitRow(o.get("orderNumber").asText());
        ui.click(w.button(row, "refund-payment"));
        assertTrue(ui.answerPrompt("Hoàn qua chuyển khoản QA-" + System.nanoTime()).startsWith("Ghi chú hoàn tiền"));
        w.waitNotice("Đã cập nhật dữ liệu.");
        ui.until(20, d -> "Đã hoàn tiền".equals(w.cell(w.rowWith(o.get("orderNumber").asText()), "paymentStatus")));
    }
}
