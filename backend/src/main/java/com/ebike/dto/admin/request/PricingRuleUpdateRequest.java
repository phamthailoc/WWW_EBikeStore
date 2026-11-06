package com.ebike.dto.admin.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PricingRuleUpdateRequest(
    BigDecimal amount,
    String status,
    LocalDateTime effectiveFrom
) {
}
