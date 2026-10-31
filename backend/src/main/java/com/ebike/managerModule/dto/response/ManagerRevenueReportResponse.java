package com.ebike.managerModule.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ManagerRevenueReportResponse(
    String periodType,
    String fromDate,
    String toDate,
    long totalOrders,
    long successfulOrders,
    BigDecimal totalRevenue,
    List<ManagerRevenuePeriodPointResponse> breakdown,
    List<ManagerTopProductResponse> topProducts,
    List<ManagerTopProductResponse> slowProducts
) {
}
