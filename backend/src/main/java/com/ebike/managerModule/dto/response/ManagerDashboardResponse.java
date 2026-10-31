package com.ebike.managerModule.dto.response;

import com.ebike.orderModule.dto.response.OrderResponse;
import java.math.BigDecimal;
import java.util.List;

public record ManagerDashboardResponse(
    long totalOrders,
    long pendingOrders,
    long unpaidPayLaterOrders,
    BigDecimal todayRevenue,
    BigDecimal weekRevenue,
    BigDecimal monthRevenue,
    List<OrderResponse> recentOrders
) {
}
