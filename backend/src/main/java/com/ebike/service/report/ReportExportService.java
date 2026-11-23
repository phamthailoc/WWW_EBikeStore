package com.ebike.service.report;

import com.ebike.common.i18n.RequestLanguage;
import com.ebike.dto.order.response.OrderResponse;
import com.ebike.service.admin.AuditLogService;
import com.ebike.service.manager.ManagerService;
import com.ebike.service.order.OrderService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * V28: the Excel files of the Manager's revenue report and order list. Both read exactly what the pages read (same filters, same rules), so a file always
 * matches the screen it was exported from. Nothing is stored: the workbook is built on request and sent. An export carries customers' names, phones and emails,
 * so each one leaves a line in the audit log (who, when, which file, how many orders; never the search text, which may be an email address).
 */
@Service
public class ReportExportService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final ManagerService managerService;
    private final OrderService orderService;
    private final AuditLogService auditLog;

    public ReportExportService(ManagerService managerService, OrderService orderService, AuditLogService auditLog) {
        this.managerService = managerService;
        this.orderService = orderService;
        this.auditLog = auditLog;
    }

    /** A finished workbook and the name it is saved under (plain ASCII, safe in a header and on any file system). */
    public record ExportFile(String filename, byte[] content) {
    }

    public ExportFile revenueReport(String period, String from, String to, String language) {
        RevenueReportExportData data = managerService.getRevenueReportForExport(period, from, to);
        byte[] content = ReportWorkbook.revenueReport(data, language, LocalDateTime.now());
        String fromDate = data.report().fromDate();
        String toDate = data.report().toDate();
        auditLog.record("xuất Excel báo cáo doanh thu", fromDate + " đến " + toDate + ", gộp theo " + data.report().periodType());
        return new ExportFile("revenue-report_" + fromDate + "_" + toDate + ".xlsx", content);
    }

    public ExportFile orders(Authentication authentication, String status, String paymentStatus, String search, Long showroomId, String language) {
        List<OrderResponse> orders = orderService.getOrders(authentication, null, status, paymentStatus, search, showroomId);
        if (orders.size() > ReportWorkbook.MAX_ORDER_ROWS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, RequestLanguage.text(
                "Có " + orders.size() + " đơn hàng, nhiều hơn mức " + ReportWorkbook.MAX_ORDER_ROWS + " đơn của một tệp Excel. Hãy lọc bớt rồi xuất lại.",
                "There are " + orders.size() + " orders, more than the " + ReportWorkbook.MAX_ORDER_ROWS + " one Excel file can hold. Narrow the filters and export again."));
        }
        LocalDateTime now = LocalDateTime.now();
        byte[] content = ReportWorkbook.orders(orders, new ReportWorkbook.OrderFilters(search, status, paymentStatus, showroomId), language, now);
        auditLog.record("xuất Excel danh sách đơn hàng", orders.size() + " đơn");
        return new ExportFile("orders_" + now.format(STAMP) + ".xlsx", content);
    }
}
