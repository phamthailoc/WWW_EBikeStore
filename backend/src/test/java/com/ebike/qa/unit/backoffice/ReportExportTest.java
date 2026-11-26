package com.ebike.qa.unit.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.manager.response.ManagerRevenuePeriodPointResponse;
import com.ebike.dto.manager.response.ManagerRevenueReportResponse;
import com.ebike.dto.manager.response.ManagerTopProductResponse;
import com.ebike.dto.order.response.OrderItemResponse;
import com.ebike.dto.order.response.OrderItemUnitResponse;
import com.ebike.dto.order.response.OrderResponse;
import com.ebike.dto.order.response.ShipmentResponse;
import com.ebike.service.admin.AuditLogService;
import com.ebike.service.manager.ManagerService;
import com.ebike.service.order.OrderService;
import com.ebike.service.report.ReportExportService;
import com.ebike.service.report.ReportWorkbook;
import com.ebike.service.report.RevenueReportExportData;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ReportExportTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 5, 10, 30);
    ManagerService manager = mock(ManagerService.class);
    OrderService orderService = mock(OrderService.class);
    AuditLogService audit = mock(AuditLogService.class);
    ReportExportService service;

    @BeforeEach
    void setUp() {
        service = new ReportExportService(manager, orderService, audit);
    }

    private static Workbook open(byte[] bytes) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private static String text(Sheet sheet, int row, int col) {
        Row r = sheet.getRow(row);
        return r == null || r.getCell(col) == null ? null : r.getCell(col).getStringCellValue();
    }

    private static ManagerRevenueReportResponse report(List<ManagerRevenuePeriodPointResponse> points, List<ManagerTopProductResponse> top) {
        return new ManagerRevenueReportResponse("DAY", "2026-03-01", "2026-03-05", 4, 2, new BigDecimal("1234567.50"), points, top, List.of());
    }

    static OrderResponse order(String number, String customerName, String note, List<OrderItemResponse> items) {
        ShipmentResponse shipment = new ShipmentResponse(1L, "PENDING", customerName, "0901234567", "r@x.com", "Q1", "1 Le Loi", null, null, null, null, null);
        return new OrderResponse(1L, null, number, "PENDING", new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false,
            new BigDecimal("100"), "VNPAY", "PAID", note, "c@x.com", null, null, null, null, null, null, NOW, NOW, null, items, shipment);
    }

    // ------------------------------------------------------------------ service

    @Test
    void revenueExport_nameCarriesTheRange_andTheAuditLineHoldsNoPersonalData() {
        var data = new RevenueReportExportData(report(List.of(), List.of()), 5, 1);
        when(manager.getRevenueReportForExport("day", null, null)).thenReturn(data);
        var file = service.revenueReport("day", null, null);
        assertThat(file.filename()).isEqualTo("revenue-report_2026-03-01_2026-03-05.xlsx").matches("[A-Za-z0-9_.-]+");
        assertThat(file.content()).isNotEmpty();
        verify(audit).record(eq("xuất Excel báo cáo doanh thu"), anyString());
    }

    @Test
    void revenueExport_invalidRange_propagates400_andWritesNoAudit() {
        when(manager.getRevenueReportForExport(any(), any(), any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "x"));
        assertThatThrownBy(() -> service.revenueReport("custom", "2026-03-05", "2026-03-01")).isInstanceOf(ResponseStatusException.class);
        verify(audit, never()).record(any(), any());
    }

    @Test
    void ordersExport_auditNeverContainsTheSearchText() {
        when(orderService.getOrders(any(), any(), any(), any(), any(), any())).thenReturn(List.of(order("A1", "Nguyen", null, List.of())));
        service.orders(Fx.auth("mgr"), "PENDING", "PAID", "secret@customer.com", 3L);
        var action = org.mockito.ArgumentCaptor.forClass(String.class);
        var target = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(audit).record(action.capture(), target.capture());
        assertThat(action.getValue() + target.getValue()).doesNotContain("secret@customer.com");
        assertThat(target.getValue()).isEqualTo("1 đơn");
        verify(orderService).getOrders(any(), eq(null), eq("PENDING"), eq("PAID"), eq("secret@customer.com"), eq(3L));
    }

    @Test
    void ordersExport_filenameIsTimestampedAscii() {
        when(orderService.getOrders(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        assertThat(service.orders(Fx.auth("mgr"), null, null, null, null).filename()).matches("orders_\\d{8}-\\d{4}\\.xlsx");
    }

    @Test
    void ordersExport_cap_10000Allowed_10001Refused_withoutBuildingAWorkbook() {
        OrderResponse o = order("A", "N", null, List.of());
        List<OrderResponse> over = new ArrayList<>();
        for (int i = 0; i <= ReportWorkbook.MAX_ORDER_ROWS; i++) {
            over.add(o);
        }
        when(orderService.getOrders(any(), any(), any(), any(), any(), any())).thenReturn(over);
        assertThatThrownBy(() -> service.orders(Fx.auth("mgr"), null, null, null, null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(400));
        verify(audit, never()).record(any(), any());
        assertThat(ReportWorkbook.MAX_ORDER_ROWS).isEqualTo(10_000);
    }

    // ------------------------------------------------------------------ workbook: revenue

    @Test
    void revenueWorkbook_hasFourSheets_summaryNumbersAreNumbers_breakdownTotalMatches() throws Exception {
        var points = List.of(new ManagerRevenuePeriodPointResponse("2026-03-01", 2, new BigDecimal("100.10")),
            new ManagerRevenuePeriodPointResponse("2026-03-02", 1, new BigDecimal("200.20")));
        var top = List.of(new ManagerTopProductResponse(1L, "Amy", 3, new BigDecimal("900")));
        byte[] bytes = ReportWorkbook.revenueReport(new RevenueReportExportData(report(points, top), 4, 1), NOW);
        try (Workbook wb = open(bytes)) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(4);
            Sheet breakdown = wb.getSheetAt(1);
            assertThat(breakdown.getRow(1).getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(breakdown.getRow(3).getCell(1).getNumericCellValue()).isEqualTo(3.0);
            assertThat(breakdown.getRow(3).getCell(2).getNumericCellValue()).isEqualTo(300.30);
            Sheet topSheet = wb.getSheetAt(2);
            assertThat(topSheet.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(1.0);
            assertThat(text(topSheet, 1, 1)).isEqualTo("Amy");
        }
    }

    @Test
    void revenueWorkbook_summaryHoldsCancellationRateAsFraction() throws Exception {
        byte[] bytes = ReportWorkbook.revenueReport(new RevenueReportExportData(report(List.of(), List.of()), 4, 1), NOW);
        try (Workbook wb = open(bytes)) {
            Sheet s = wb.getSheetAt(0);
            boolean found = false;
            for (int i = 0; i < s.getLastRowNum(); i++) {
                Row r = s.getRow(i);
                if (r != null && r.getCell(0) != null && "Tỉ lệ hủy".equals(r.getCell(0).getStringCellValue())) {
                    assertThat(r.getCell(1).getNumericCellValue()).isEqualTo(0.25);
                    found = true;
                }
            }
            assertThat(found).isTrue();
        }
    }

    @Test
    void revenueWorkbook_emptyReport_showsAPlaceholderRow_notACrash() throws Exception {
        byte[] bytes = ReportWorkbook.revenueReport(new RevenueReportExportData(report(List.of(), List.of()), 0, 0), NOW);
        try (Workbook wb = open(bytes)) {
            assertThat(text(wb.getSheetAt(1), 1, 0)).isEqualTo("Chưa có dữ liệu trong kỳ này.");
        }
    }

    @Test
    void revenueWorkbook_nullListsAndNullMoney_areTolerated() throws Exception {
        var r = new ManagerRevenueReportResponse("DAY", "a", "b", 0, 0, null, null, null, null);
        byte[] bytes = ReportWorkbook.revenueReport(new RevenueReportExportData(r, 0, 0), NOW);
        assertThat(bytes).isNotEmpty();
    }

    // ------------------------------------------------------------------ workbook: orders

    @Test
    void ordersWorkbook_formulaLookingTextStaysText_neverAFormula() throws Exception {
        var evil = order("=1+1", "=HYPERLINK(\"http://evil\",\"x\")", "+SUM(A1:A9)", List.of());
        byte[] bytes = ReportWorkbook.orders(List.of(evil), new ReportWorkbook.OrderFilters("@cmd", "PENDING", null, null), NOW);
        try (Workbook wb = open(bytes)) {
            Sheet s = wb.getSheetAt(0);
            for (Cell c : s.getRow(1)) {
                assertThat(c.getCellType()).as("col " + c.getColumnIndex()).isNotEqualTo(CellType.FORMULA);
            }
            assertThat(text(s, 1, 0)).isEqualTo("=1+1");
            assertThat(text(s, 1, 4)).startsWith("=HYPERLINK");
        }
    }

    @Test
    void ordersWorkbook_moneyIsNumeric_datesAreDates_recipientAndFramesListed() throws Exception {
        var unit = new OrderItemUnitResponse(1L, "FRAME-1", "ENG-1");
        var item = new OrderItemResponse(1L, 9L, "Amy", new BigDecimal("50"), 2, new BigDecimal("100"), 3L, "Đỏ", true, List.of(unit));
        byte[] bytes = ReportWorkbook.orders(List.of(order("A1", "Nguyen", "note", List.of(item))), null, NOW);
        try (Workbook wb = open(bytes)) {
            Sheet s = wb.getSheetAt(0);
            assertThat(s.getRow(1).getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(s.getRow(1).getCell(1).getLocalDateTimeCellValue()).isEqualTo(NOW);
            assertThat(s.getRow(1).getCell(15).getNumericCellValue()).isEqualTo(100.0);
            assertThat(text(s, 1, 9)).isEqualTo("Amy (Đỏ) x 2");
            assertThat(text(s, 1, 10)).isEqualTo("FRAME-1");
            assertThat(text(s, 1, 4)).isEqualTo("Nguyen");
        }
    }

    @Test
    void ordersWorkbook_noOrders_hasPlaceholder_andFiltersSheetRecordsTheCriteria() throws Exception {
        byte[] bytes = ReportWorkbook.orders(List.of(), new ReportWorkbook.OrderFilters(" abc ", "pending", "paid", 7L), NOW);
        try (Workbook wb = open(bytes)) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(2);
            Sheet info = wb.getSheetAt(1);
            List<String> all = new ArrayList<>();
            for (int i = 1; i <= info.getLastRowNum(); i++) {
                for (Cell c : info.getRow(i)) {
                    if (c.getCellType() == CellType.STRING) {
                        all.add(c.getStringCellValue());
                    }
                }
            }
            assertThat(all).contains("abc", "7", "0");
        }
    }

    @Test
    void ordersWorkbook_cellOverExcelLimit_isTruncatedNotCorrupted() throws Exception {
        var o = order("A1", "N", "x".repeat(40_000), List.of());
        byte[] bytes = ReportWorkbook.orders(List.of(o), null, NOW);
        try (Workbook wb = open(bytes)) {
            assertThat(text(wb.getSheetAt(0), 1, 19)).hasSize(32_000);
        }
    }

    @Test
    void ordersWorkbook_nullShipmentAndItems_areTolerated() throws Exception {
        var o = new OrderResponse(1L, 5L, "A1", "CANCELLED", null, null, null, null, null, null, null, null, null, null, "changed my mind", null, null,
            null, null, null, NOW, NOW, null, null, null);
        byte[] bytes = ReportWorkbook.orders(List.of(o), null, NOW);
        try (Workbook wb = open(bytes)) {
            assertThat(text(wb.getSheetAt(0), 1, 18)).isEqualTo("changed my mind");
        }
    }
}
