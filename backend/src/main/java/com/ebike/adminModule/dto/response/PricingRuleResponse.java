package com.ebike.adminModule.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PricingRuleResponse(
    Long id,
    String code,
    String name,
    String description,
    BigDecimal amount,
    String unit,
    String status,
    LocalDateTime effectiveFrom,
    LocalDateTime updatedAt
) {
}
