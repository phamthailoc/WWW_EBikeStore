package com.ebike.service.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebike.dto.manager.response.ManagerRevenuePeriodPointResponse;
import com.ebike.dto.manager.response.ManagerRevenueReportResponse;
import com.ebike.dto.manager.response.ManagerTopProductResponse;
import com.ebike.dto.order.response.OrderItemResponse;
import com.ebike.dto.order.response.OrderItemUnitResponse;
import com.ebike.dto.order.response.OrderResponse;
import com.ebike.dto.order.response.ShipmentResponse;
import com.ebike.dto.order.response.ShowroomResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ReportWorkbookTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 24, 9, 30);

    private static Workbook open(byte[] bytes) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private static String text(Sheet sheet, int row, int column) {
        Row r = sheet.getRow(row);
        Cell c = r == null ? null : r.getCell(column);
        return c == null ? null : c.getStringCellValue();
    }

    private static double number(Sheet sheet, int row, int column) {
        return sheet.getRow(row).getCell(column).getNumericCellValue();
    }

    private static RevenueReportExportData report(long placed, long cancelled) {
        ManagerRevenueReportResponse report = new ManagerRevenueReportResponse(
            "MONTH", "2026-10-01", "2026-11-24", placed - cancelled, 6, new BigDecimal("150000000"),
            List.of(
                new ManagerRevenuePeriodPointResponse("2026-10", 4, new BigDecimal("90000000")),
                new ManagerRevenuePeriodPointResponse("2026-11", 5, new BigDecimal("60000000"))),
            List.of(new ManagerTopProductResponse(1L, "KINETIC S1", 7, new BigDecimal("140000000")),
                new ManagerTopProductResponse(2L, "=HYPERLINK(\"http://evil\")", 1, new BigDecimal("10000000"))),
            List.of(new ManagerTopProductResponse(3L, "KINETIC Z9", 0, BigDecimal.ZERO)));
        return new RevenueReportExportData(report, placed, cancelled);
    }

    @Test
    void theRevenueWorkbookHasFourSheetsWithTheFiguresOfThePage() throws IOException {
        try (Workbook workbook = open(ReportWorkbook.revenueReport(report(12, 3), NOW))) {
            assertEquals(4, workbook.getNumberOfSheets());
            assertEquals("Tổng quan", workbook.getSheetName(0));
            assertEquals("Doanh thu theo kỳ", workbook.getSheetName(1));
            Sheet summary = workbook.getSheetAt(0);
            assertEquals("Tháng", text(summary, 2, 1));
            assertEquals("2026-10-01", text(summary, 3, 1));
            assertEquals(12, number(summary, 7, 1));
            assertEquals(9, number(summary, 8, 1));
            assertEquals(3, number(summary, 10, 1));
            assertEquals(0.25, number(summary, 11, 1), 1e-9);
            assertEquals(150000000d, number(summary, 12, 1), 0.001);

            Sheet breakdown = workbook.getSheetAt(1);
            assertEquals("2026-10", text(breakdown, 1, 0));
            assertEquals(90000000d, number(breakdown, 1, 2), 0.001);
            assertEquals("Tổng cộng", text(breakdown, 3, 0));
            assertEquals(9, number(breakdown, 3, 1));
            assertEquals(150000000d, number(breakdown, 3, 2), 0.001);
        }
    }

    @Test
    void aProductNameThatLooksLikeAFormulaStaysPlainText() throws IOException {
        try (Workbook workbook = open(ReportWorkbook.revenueReport(report(12, 3), NOW))) {
            Cell cell = workbook.getSheetAt(2).getRow(2).getCell(1);
            assertEquals(CellType.STRING, cell.getCellType());
            assertTrue(cell.getStringCellValue().startsWith("="));
        }
    }

    @Test
    void moneyIsANumberSoTheManagerCanAddItUp() throws IOException {
        try (Workbook workbook = open(ReportWorkbook.revenueReport(report(12, 3), NOW))) {
            Cell revenue = workbook.getSheetAt(1).getRow(1).getCell(2);
            assertEquals(CellType.NUMERIC, revenue.getCellType());
            assertEquals("#,##0", revenue.getCellStyle().getDataFormatString());
        }
    }

    @Test
    void anEmptyPeriodStillGivesAReadableFile() throws IOException {
        ManagerRevenueReportResponse empty = new ManagerRevenueReportResponse("DAY", "2026-11-24", "2026-11-24", 0, 0, BigDecimal.ZERO, List.of(), List.of(), List.of());
        try (Workbook workbook = open(ReportWorkbook.revenueReport(new RevenueReportExportData(empty, 0, 0), NOW))) {
            Sheet summary = workbook.getSheetAt(0);
            assertEquals(0d, number(summary, 11, 1), 0d);
            assertEquals("Chưa có dữ liệu trong kỳ này.", text(workbook.getSheetAt(1), 1, 0));
            assertEquals("Chưa có dữ liệu trong kỳ này.", text(workbook.getSheetAt(2), 1, 0));
        }
    }

    @Test
    void theCancellationRateIsZeroWhenNothingWasPlaced() {
        assertEquals(0d, new RevenueReportExportData(null, 0, 0).cancellationRate(), 0d);
        assertEquals(0.5, new RevenueReportExportData(null, 4, 2).cancellationRate(), 1e-9);
    }

    // ------------------------------------------------------------------ orders

    private static OrderResponse order(String number, Long userId, String status, ShipmentResponse shipment, String notes) {
        OrderItemResponse item = new OrderItemResponse(1L, 10L, "KINETIC S1", new BigDecimal("30000000"), 2, new BigDecimal("60000000"),
            5L, "Đỏ", true, List.of(new OrderItemUnitResponse(1L, "RLH123456789", "E1"), new OrderItemUnitResponse(2L, "RLH123456790", "E2")));
        return new OrderResponse(1L, userId, number, status, new BigDecimal("60000000"), new BigDecimal("500000"), new BigDecimal("1000000"),
            new BigDecimal("300000"), true, new BigDecimal("59800000"), "VNPAY", "PAID", notes, "khach@example.com", "CANCELLED".equals(status) ? "Đổi ý" : null,
            null, null, null, null, null, LocalDateTime.of(2026, 11, 20, 14, 5), NOW, null, List.of(item), shipment);
    }

    private static ShipmentResponse home() {
        return new ShipmentResponse(1L, "PENDING", "Nguyễn Văn A", "0901234567", "khach@example.com", "Quận 1", "12 Lê Lợi", "12 Lê Lợi, Quận 1", null, null, null, null);
    }

    private static ShipmentResponse pickup() {
        ShowroomResponse showroom = new ShowroomResponse(3L, "KINETIC Quận 7", "Hồ Chí Minh", "Quận 7", "1 Nguyễn Thị Thập", "0281234567", "08:00 - 21:00", true);
        return new ShipmentResponse(2L, "PENDING", "Trần Thị B", "0912345678", null, null, null, null, null, null, null, showroom);
    }

    @Test
    void theOrderSheetHasOneRowPerOrderWithReadableNames() throws IOException {
        List<OrderResponse> orders = List.of(order("ORD-1", 7L, "DELIVERED", home(), null), order("ORD-2", null, "CANCELLED", pickup(), "gọi trước"));
        try (Workbook workbook = open(ReportWorkbook.orders(orders, new ReportWorkbook.OrderFilters("ORD", "DELIVERED", null, null), NOW))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("Đơn hàng", sheet.getSheetName());
            assertEquals(3, sheet.getPhysicalNumberOfRows());
            assertEquals("Mã đơn", text(sheet, 0, 0));
            assertEquals("ORD-1", text(sheet, 1, 0));
            assertEquals(LocalDateTime.of(2026, 11, 20, 14, 5), sheet.getRow(1).getCell(1).getLocalDateTimeCellValue());
            assertEquals("Đã giao", text(sheet, 1, 2));
            assertEquals("Có tài khoản", text(sheet, 1, 3));
            assertEquals("Giao tại nhà", text(sheet, 1, 7));
            assertEquals("12 Lê Lợi, Quận 1", text(sheet, 1, 8));
            assertEquals("KINETIC S1 (Đỏ) x 2", text(sheet, 1, 9));
            assertEquals("RLH123456789\nRLH123456790", text(sheet, 1, 10));
            assertEquals(59800000d, number(sheet, 1, 15), 0.001);
            assertEquals("VNPay", text(sheet, 1, 16));
            assertEquals("Đã thanh toán", text(sheet, 1, 17));

            assertEquals("Khách vãng lai", text(sheet, 2, 3));
            assertEquals("Nhận tại cửa hàng", text(sheet, 2, 7));
            assertTrue(text(sheet, 2, 8).startsWith("KINETIC Quận 7"));
            assertEquals("Đã hủy", text(sheet, 2, 2));
            assertEquals("Đổi ý", text(sheet, 2, 18));
            assertEquals("gọi trước", text(sheet, 2, 19));
            assertNotNull(((org.apache.poi.xssf.usermodel.XSSFSheet) sheet).getCTWorksheet().getAutoFilter());
        }
    }

    @Test
    void noFilterReadsAsAll() throws IOException {
        try (Workbook workbook = open(ReportWorkbook.orders(List.of(), null, NOW))) {
            Sheet info = workbook.getSheetAt(1);
            assertEquals("Tất cả", text(info, 1, 1));
            assertEquals("Tất cả", text(info, 2, 1));
            assertEquals("0", text(info, 5, 1));
        }
    }

    @Test
    void anOrderWithoutShipmentOrItemsDoesNotBreakTheFile() throws IOException {
        OrderResponse bare = new OrderResponse(9L, null, "ORD-9", "PENDING", null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null);
        try (Workbook workbook = open(ReportWorkbook.orders(List.of(bare), null, NOW))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("ORD-9", text(sheet, 1, 0));
            assertEquals("Chờ xử lý", text(sheet, 1, 2));
            assertEquals("—", text(sheet, 1, 7));
            assertEquals("—", text(sheet, 1, 16));
            assertEquals(0d, number(sheet, 1, 15), 0d);
        }
    }

    @Test
    void aNoteThatLooksLikeAFormulaIsStoredAsTextAndAHugeNoteIsCut() throws IOException {
        String huge = "=" + "x".repeat(40_000);
        try (Workbook workbook = open(ReportWorkbook.orders(List.of(order("ORD-1", 7L, "DELIVERED", home(), huge)), null, NOW))) {
            Cell cell = workbook.getSheetAt(0).getRow(1).getCell(19);
            assertEquals(CellType.STRING, cell.getCellType());
            assertTrue(cell.getStringCellValue().startsWith("="));
            assertTrue(cell.getStringCellValue().length() <= 32_000);
        }
    }

    @Test
    void sheetNamesFitExcelsLimitInBothLanguages() throws IOException {
        for (String language : List.of("vi", "en")) {
            try (Workbook workbook = open(ReportWorkbook.revenueReport(report(1, 0), NOW))) {
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    assertTrue(workbook.getSheetName(i).length() <= 31);
                }
            }
            try (Workbook workbook = open(ReportWorkbook.orders(List.of(), null, NOW))) {
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    assertFalse(workbook.getSheetName(i).isBlank());
                }
            }
        }
    }
}
