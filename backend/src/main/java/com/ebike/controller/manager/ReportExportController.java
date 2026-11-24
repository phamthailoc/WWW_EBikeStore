package com.ebike.controller.manager;

import com.ebike.common.constants.PermissionConstants;
import com.ebike.service.report.ReportExportService;
import com.ebike.service.report.ReportText;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * V28: Excel downloads of the Manager's revenue report and order list. The filters are the ones the pages send to their own lists. The file is in the page
 * language (cookie or account) unless {@code lang=vi|en} says otherwise; the texts of an error stay in the page language. A file carries customers' personal
 * data, so it is never cached.
 */
@RestController
@RequestMapping("/api/v1/manager")
public class ReportExportController {

    public static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportExportService exports;

    public ReportExportController(ReportExportService exports) {
        this.exports = exports;
    }

    @GetMapping("/revenue-report/export")
    @PreAuthorize("hasAuthority('" + PermissionConstants.Reporting.REPORT_VIEW + "')")
    public ResponseEntity<byte[]> revenueReport(
        @RequestParam(defaultValue = "day") String period,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to
    ) {
        return xlsx(exports.revenueReport(period, from, to));
    }

    @GetMapping("/orders/export")
    @PreAuthorize("hasAuthority('" + PermissionConstants.OrderManagement.ORDER_VIEW_ALL + "')")
    public ResponseEntity<byte[]> orders(
        Authentication authentication,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String paymentStatus,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) Long showroomId
    ) {
        return xlsx(exports.orders(authentication, status, paymentStatus, search, showroomId));
    }

    private static ResponseEntity<byte[]> xlsx(ReportExportService.ExportFile file) {
        return ResponseEntity.ok()
            .contentType(XLSX)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .cacheControl(CacheControl.noStore())
            .contentLength(file.content().length)
            .body(file.content());
    }
}
