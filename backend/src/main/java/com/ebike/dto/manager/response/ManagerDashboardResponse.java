package com.ebike.dto.manager.response;

import com.ebike.dto.order.response.OrderResponse;
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
