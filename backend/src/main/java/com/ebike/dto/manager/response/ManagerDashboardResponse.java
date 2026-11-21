package com.ebike.dto.manager.response;

import com.ebike.dto.order.response.OrderResponse;
import java.math.BigDecimal;
import java.util.List;

public record ManagerDashboardResponse(
    long totalOrders,
    long pendingOrders,
    long unpaidPayLaterOrders,
    /** V24: approved instalment applications whose down payment is not collected yet. */
    long unpaidInstallmentDownPayments,
    BigDecimal todayRevenue,
    BigDecimal weekRevenue,
    BigDecimal monthRevenue,
    List<OrderResponse> recentOrders
) {
}
