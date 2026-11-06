package com.ebike.dto.manager.response;

import java.math.BigDecimal;

public record ManagerRevenuePeriodPointResponse(
    String label,
    long orderCount,
    BigDecimal revenue
) {
}
