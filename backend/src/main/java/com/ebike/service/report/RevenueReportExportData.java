package com.ebike.service.report;

import com.ebike.dto.manager.response.ManagerRevenueReportResponse;

/**
 * V28: what the revenue workbook is made from: the report shown on the page, plus the number of orders placed in the period and how many of them were cancelled
 * (the page's "total orders" leaves cancelled orders out, so the cancellation rate cannot be worked out from it).
 */
public record RevenueReportExportData(ManagerRevenueReportResponse report, long placedOrders, long cancelledOrders) {

    /** Cancelled over placed, from 0 to 1; zero when nothing was placed. */
    public double cancellationRate() {
        return placedOrders <= 0 ? 0d : (double) cancelledOrders / placedOrders;
    }
}
