package com.ebike.adminModule.dto.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PromotionCreateRequest(
    String code,
    String campaignName,
    String discountType,
    BigDecimal discountValue,
    BigDecimal maxDiscountAmount,
    Integer usageLimit,
    String status,
    LocalDateTime startsAt,
    LocalDateTime endsAt
) {
}
