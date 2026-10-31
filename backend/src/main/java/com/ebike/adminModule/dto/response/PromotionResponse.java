package com.ebike.adminModule.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PromotionResponse(
    Long id,
    String code,
    String campaignName,
    String discountType,
    BigDecimal discountValue,
    BigDecimal maxDiscountAmount,
    Integer usageCount,
    Integer usageLimit,
    String status,
    LocalDateTime startsAt,
    LocalDateTime endsAt,
    LocalDateTime updatedAt
) {
}
