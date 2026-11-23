package com.ebike.service.report;

import static com.ebike.service.report.ReportText.code;
import static com.ebike.service.report.ReportText.t;

import com.ebike.dto.manager.response.ManagerRevenuePeriodPointResponse;
import com.ebike.dto.manager.response.ManagerRevenueReportResponse;
import com.ebike.dto.manager.response.ManagerTopProductResponse;
import com.ebike.dto.order.response.OrderItemResponse;
import com.ebike.dto.order.response.OrderItemUnitResponse;
import com.ebike.dto.order.response.OrderResponse;
import com.ebike.dto.order.response.ShipmentResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * V28: builds the .xlsx files of the revenue report and of the order list. Pure (no database, no request), so a test can read the cells back.
 *
 * <p>Every text goes into the file as a text cell and is never read as a formula, so a customer name or a note that starts with "=" cannot run anything
 * when the file is opened. Money is a real number with a thousands format (so the manager can add it up), dates are real dates, and the width of each column
 * is set by hand because measuring text needs a screen font, which a server may not have.
 */
public final class ReportWorkbook {

    /** The most orders one file may hold: a workbook is built in memory, and an Excel sheet is meant for a manager to read, not for a data dump. */
    public static final int MAX_ORDER_ROWS = 10_000;

    private static final int EXCEL_CELL_LIMIT = 32_000; // Excel keeps at most 32,767 characters in a cell

    private ReportWorkbook() {
    }

    /** The criteria of the order list, written on the "Filters" sheet so a file can be understood without the screen it came from. */
    public record OrderFilters(String search, String status, String paymentStatus, Long showroomId) {
    }

    // ------------------------------------------------------------------ revenue report

    public static byte[] revenueReport(RevenueReportExportData data, String language, LocalDateTime generatedAt) {
        String lang = ReportText.language(language);
        ManagerRevenueReportResponse report = data.report();
        try (Workbook workbook = new XSSFWorkbook()) {
            Styles styles = new Styles(workbook);

            Sheet summary = workbook.createSheet(t("sheet.summary", lang));
            int row = 0;
            Cell title = summary.createRow(row++).createCell(0);
            title.setCellValue(t("summary.title", lang));
            title.setCellStyle(styles.title);
            row++;
            row = labelled(summary, row, t("summary.groupBy", lang), code("period", report.periodType(), lang), styles);
            row = labelled(summary, row, t("summary.from", lang), report.fromDate(), styles);
            row = labelled(summary, row, t("summary.to", lang), report.toDate(), styles);
            row = labelledDateTime(summary, row, t("summary.generatedAt", lang), generatedAt, styles);
            row++;
            row = labelledNumber(summary, row, t("summary.placedOrders", lang), data.placedOrders(), styles.integer, styles);
            row = labelledNumber(summary, row, t("summary.totalOrders", lang), report.totalOrders(), styles.integer, styles);
            row = labelledNumber(summary, row, t("summary.successfulOrders", lang), report.successfulOrders(), styles.integer, styles);
            row = labelledNumber(summary, row, t("summary.cancelledOrders", lang), data.cancelledOrders(), styles.integer, styles);
            row = labelledNumber(summary, row, t("summary.cancellationRate", lang), data.cancellationRate(), styles.percent, styles);
            row = labelledNumber(summary, row, t("summary.totalRevenue", lang), money(report.totalRevenue()), styles.money, styles);
            row++;
            Cell note = summary.createRow(row).createCell(0);
            note.setCellValue(t("summary.note", lang));
            note.setCellStyle(styles.note);
            summary.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(row, row, 0, 3));
            summary.getRow(row).setHeightInPoints(48);
            summary.setColumnWidth(0, width(40));
            summary.setColumnWidth(1, width(24));
            summary.setColumnWidth(2, width(14));
            summary.setColumnWidth(3, width(14));

            Sheet breakdown = workbook.createSheet(t("sheet.breakdown", lang));
            header(breakdown, styles, t("col.period", lang), t("col.orderCount", lang), t("col.revenue", lang));
            int r = 1;
            long orderSum = 0;
            BigDecimal revenueSum = BigDecimal.ZERO;
            List<ManagerRevenuePeriodPointResponse> points = report.breakdown() == null ? List.of() : report.breakdown();
            for (ManagerRevenuePeriodPointResponse point : points) {
                Row line = breakdown.createRow(r++);
                text(line, 0, point.label(), styles.cell);
                number(line, 1, point.orderCount(), styles.integer);
                number(line, 2, money(point.revenue()), styles.money);
                orderSum += point.orderCount();
                revenueSum = revenueSum.add(point.revenue() == null ? BigDecimal.ZERO : point.revenue());
            }
            if (points.isEmpty()) {
                r = emptyLine(breakdown, r, t("empty.rows", lang), 3, styles);
            } else {
                Row total = breakdown.createRow(r);
                text(total, 0, t("row.total", lang), styles.totalLabel);
                number(total, 1, orderSum, styles.totalInteger);
                number(total, 2, money(revenueSum), styles.totalMoney);
            }
            breakdown.setColumnWidth(0, width(22));
            breakdown.setColumnWidth(1, width(12));
            breakdown.setColumnWidth(2, width(20));
            breakdown.createFreezePane(0, 1);

            productSheet(workbook.createSheet(t("sheet.top", lang)), report.topProducts(), lang, styles);
            productSheet(workbook.createSheet(t("sheet.slow", lang)), report.slowProducts(), lang, styles);

            return toBytes(workbook);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void productSheet(Sheet sheet, List<ManagerTopProductResponse> products, String lang, Styles styles) {
        header(sheet, styles, t("col.rank", lang), t("col.product", lang), t("col.quantity", lang), t("col.revenue", lang));
        int r = 1;
        int rank = 1;
        if (products != null) {
            for (ManagerTopProductResponse product : products) {
                Row line = sheet.createRow(r++);
                number(line, 0, rank++, styles.integer);
                text(line, 1, product.productName(), styles.cell);
                number(line, 2, product.quantitySold(), styles.integer);
                number(line, 3, money(product.revenue()), styles.money);
            }
        }
        if (r == 1) {
            emptyLine(sheet, r, t("empty.rows", lang), 4, styles);
        }
        sheet.setColumnWidth(0, width(8));
        sheet.setColumnWidth(1, width(40));
        sheet.setColumnWidth(2, width(14));
        sheet.setColumnWidth(3, width(20));
        sheet.createFreezePane(0, 1);
    }

    // ------------------------------------------------------------------ order list

    public static byte[] orders(List<OrderResponse> orders, OrderFilters filters, String language, LocalDateTime generatedAt) {
        String lang = ReportText.language(language);
        try (Workbook workbook = new XSSFWorkbook()) {
            Styles styles = new Styles(workbook);
            Sheet sheet = workbook.createSheet(t("sheet.orders", lang));
            String[] titles = {
                t("order.number", lang), t("order.createdAt", lang), t("order.status", lang), t("order.customerType", lang), t("order.recipient", lang),
                t("order.email", lang), t("order.phone", lang), t("order.handover", lang), t("order.place", lang), t("order.items", lang),
                t("order.frames", lang), t("order.subtotal", lang), t("order.shipping", lang), t("order.registration", lang), t("order.discount", lang),
                t("order.total", lang), t("order.method", lang), t("order.paymentStatus", lang), t("order.cancelReason", lang), t("order.notes", lang)
            };
            header(sheet, styles, titles);
            int r = 1;
            for (OrderResponse order : orders) {
                Row line = sheet.createRow(r++);
                ShipmentResponse shipment = order.shipment();
                boolean pickup = shipment != null && shipment.pickupShowroom() != null;
                int c = 0;
                text(line, c++, order.orderNumber(), styles.cell);
                dateTime(line, c++, order.createdAt(), styles);
                text(line, c++, code("OrderStatus", order.status(), lang), styles.cell);
                text(line, c++, order.userId() == null ? t("order.customerGuest", lang) : t("order.customerAccount", lang), styles.cell);
                text(line, c++, shipment == null ? null : shipment.recipientName(), styles.cell);
                text(line, c++, firstNonBlank(order.customerEmail(), shipment == null ? null : shipment.recipientEmail()), styles.cell);
                text(line, c++, shipment == null ? null : shipment.phoneNumber(), styles.cell);
                text(line, c++, shipment == null ? "—" : pickup ? t("order.pickup", lang) : t("order.delivery", lang), styles.cell);
                text(line, c++, place(shipment, pickup), styles.wrap);
                text(line, c++, items(order.items()), styles.wrap);
                text(line, c++, frameNumbers(order.items()), styles.wrap);
                number(line, c++, money(order.subtotal()), styles.money);
                number(line, c++, money(order.shippingFee()), styles.money);
                number(line, c++, money(order.registrationFee()), styles.money);
                number(line, c++, money(order.discountAmount()), styles.money);
                number(line, c++, money(order.totalAmount()), styles.money);
                text(line, c++, code("PaymentMethod", order.paymentMethod(), lang), styles.cell);
                text(line, c++, code("PaymentStatus", order.paymentStatus(), lang), styles.cell);
                text(line, c++, order.cancellationReason(), styles.wrap);
                text(line, c, order.notes(), styles.wrap);
            }
            if (orders.isEmpty()) {
                emptyLine(sheet, r, t("order.none", lang), titles.length, styles);
            }
            int[] widths = {18, 17, 16, 15, 22, 28, 15, 18, 36, 40, 26, 15, 15, 15, 15, 16, 18, 18, 28, 30};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, width(widths[i]));
            }
            sheet.createFreezePane(1, 1);
            if (!orders.isEmpty()) {
                sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, orders.size(), 0, titles.length - 1));
            }

            Sheet info = workbook.createSheet(t("sheet.filters", lang));
            header(info, styles, t("filter.name", lang), t("filter.value", lang));
            Map<String, String> rows = new LinkedHashMap<>();
            OrderFilters applied = filters == null ? new OrderFilters(null, null, null, null) : filters;
            rows.put(t("filter.search", lang), blankToAll(applied.search(), lang));
            rows.put(t("filter.status", lang), applied.status() == null || applied.status().isBlank() ? t("filter.all", lang) : code("OrderStatus", applied.status().trim().toUpperCase(java.util.Locale.ROOT), lang));
            rows.put(t("filter.paymentStatus", lang), applied.paymentStatus() == null || applied.paymentStatus().isBlank() ? t("filter.all", lang) : code("PaymentStatus", applied.paymentStatus().trim().toUpperCase(java.util.Locale.ROOT), lang));
            rows.put(t("filter.showroom", lang), applied.showroomId() == null ? t("filter.all", lang) : String.valueOf(applied.showroomId()));
            rows.put(t("filter.rows", lang), String.valueOf(orders.size()));
            int ir = 1;
            for (Map.Entry<String, String> entry : rows.entrySet()) {
                Row line = info.createRow(ir++);
                text(line, 0, entry.getKey(), styles.cell);
                text(line, 1, entry.getValue(), styles.cell);
            }
            Row stamp = info.createRow(ir);
            text(stamp, 0, t("filter.generatedAt", lang), styles.cell);
            dateTime(stamp, 1, generatedAt, styles);
            info.setColumnWidth(0, width(26));
            info.setColumnWidth(1, width(30));

            return toBytes(workbook);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    // ------------------------------------------------------------------ pieces

    private static String place(ShipmentResponse shipment, boolean pickup) {
        if (shipment == null) {
            return null;
        }
        if (pickup) {
            var showroom = shipment.pickupShowroom();
            return joinNonBlank(", ", showroom.name(), showroom.address(), showroom.district(), showroom.city());
        }
        String address = firstNonBlank(shipment.shippingAddress(), joinNonBlank(", ", shipment.detailedAddress(), shipment.pickupDistrict()));
        return address;
    }

    private static String items(List<OrderItemResponse> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        for (OrderItemResponse item : items) {
            String colour = item.colorName() == null || item.colorName().isBlank() ? "" : " (" + item.colorName().trim() + ")";
            lines.add(item.productName() + colour + " x " + (item.quantity() == null ? 0 : item.quantity()));
        }
        return String.join("\n", lines);
    }

    private static String frameNumbers(List<OrderItemResponse> items) {
        if (items == null) {
            return null;
        }
        List<String> frames = new ArrayList<>();
        for (OrderItemResponse item : items) {
            if (item.units() == null) {
                continue;
            }
            for (OrderItemUnitResponse unit : item.units()) {
                if (unit.frameNumber() != null && !unit.frameNumber().isBlank()) {
                    frames.add(unit.frameNumber());
                }
            }
        }
        return frames.isEmpty() ? null : String.join("\n", frames);
    }

    private static String blankToAll(String value, String lang) {
        return value == null || value.isBlank() ? t("filter.all", lang) : value.trim();
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : (second != null && !second.isBlank() ? second : null);
    }

    private static String joinNonBlank(String separator, String... parts) {
        List<String> kept = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                kept.add(part.trim());
            }
        }
        return kept.isEmpty() ? null : String.join(separator, kept);
    }

    private static double money(BigDecimal amount) {
        return amount == null ? 0d : amount.doubleValue();
    }

    private static int width(int characters) {
        return Math.min(255, characters + 2) * 256;
    }

    private static void header(Sheet sheet, Styles styles, String... titles) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(24);
        for (int i = 0; i < titles.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(titles[i]);
            cell.setCellStyle(styles.header);
        }
    }

    private static int emptyLine(Sheet sheet, int rowIndex, String message, int columns, Styles styles) {
        Row row = sheet.createRow(rowIndex);
        Cell cell = row.createCell(0);
        cell.setCellValue(message);
        cell.setCellStyle(styles.note);
        if (columns > 1) {
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(rowIndex, rowIndex, 0, columns - 1));
        }
        return rowIndex + 1;
    }

    private static int labelled(Sheet sheet, int rowIndex, String label, String value, Styles styles) {
        Row row = sheet.createRow(rowIndex);
        text(row, 0, label, styles.label);
        text(row, 1, value, styles.cell);
        return rowIndex + 1;
    }

    private static int labelledDateTime(Sheet sheet, int rowIndex, String label, LocalDateTime value, Styles styles) {
        Row row = sheet.createRow(rowIndex);
        text(row, 0, label, styles.label);
        dateTime(row, 1, value, styles);
        return rowIndex + 1;
    }

    private static int labelledNumber(Sheet sheet, int rowIndex, String label, double value, CellStyle style, Styles styles) {
        Row row = sheet.createRow(rowIndex);
        text(row, 0, label, styles.label);
        number(row, 1, value, style);
        return rowIndex + 1;
    }

    /** A text cell. Never a formula: the value is stored as a string whatever it starts with. */
    private static void text(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        String safe = value == null ? "" : value;
        if (safe.length() > EXCEL_CELL_LIMIT) {
            safe = safe.substring(0, EXCEL_CELL_LIMIT);
        }
        cell.setCellValue(safe);
        cell.setCellStyle(style);
    }

    private static void number(Row row, int column, double value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void dateTime(Row row, int column, LocalDateTime value, Styles styles) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value);
        }
        cell.setCellStyle(styles.dateTime);
    }

    private static byte[] toBytes(Workbook workbook) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return out.toByteArray();
    }

    private static final class Styles {
        final CellStyle title;
        final CellStyle header;
        final CellStyle label;
        final CellStyle cell;
        final CellStyle wrap;
        final CellStyle note;
        final CellStyle integer;
        final CellStyle money;
        final CellStyle percent;
        final CellStyle dateTime;
        final CellStyle totalLabel;
        final CellStyle totalInteger;
        final CellStyle totalMoney;

        Styles(Workbook workbook) {
            Font bold = workbook.createFont();
            bold.setBold(true);
            Font big = workbook.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 14);
            Font light = workbook.createFont();
            light.setBold(true);
            light.setColor(IndexedColors.WHITE.getIndex());

            title = workbook.createCellStyle();
            title.setFont(big);

            header = workbook.createCellStyle();
            header.setFont(light);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setWrapText(true);

            label = workbook.createCellStyle();
            label.setFont(bold);

            cell = workbook.createCellStyle();
            cell.setVerticalAlignment(VerticalAlignment.TOP);

            wrap = workbook.createCellStyle();
            wrap.setWrapText(true);
            wrap.setVerticalAlignment(VerticalAlignment.TOP);

            note = workbook.createCellStyle();
            note.setWrapText(true);
            note.setVerticalAlignment(VerticalAlignment.TOP);

            integer = numberStyle(workbook, "#,##0", false, bold);
            money = numberStyle(workbook, "#,##0", false, bold);
            percent = numberStyle(workbook, "0.0%", false, bold);
            dateTime = workbook.createCellStyle();
            dateTime.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));
            dateTime.setAlignment(HorizontalAlignment.LEFT);
            dateTime.setVerticalAlignment(VerticalAlignment.TOP);

            totalLabel = workbook.createCellStyle();
            totalLabel.setFont(bold);
            totalLabel.setBorderTop(BorderStyle.THIN);
            totalInteger = numberStyle(workbook, "#,##0", true, bold);
            totalMoney = numberStyle(workbook, "#,##0", true, bold);
        }

        private static CellStyle numberStyle(Workbook workbook, String format, boolean total, Font bold) {
            CellStyle style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat(format));
            style.setVerticalAlignment(VerticalAlignment.TOP);
            if (total) {
                style.setFont(bold);
                style.setBorderTop(BorderStyle.THIN);
            }
            return style;
        }
    }
}
