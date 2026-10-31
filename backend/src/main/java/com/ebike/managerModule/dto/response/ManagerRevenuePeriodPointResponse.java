package com.ebike.managerModule.dto.response;

import java.math.BigDecimal;

public record ManagerRevenuePeriodPointResponse(
    String label,
    long orderCount,
    BigDecimal revenue
) {
}
