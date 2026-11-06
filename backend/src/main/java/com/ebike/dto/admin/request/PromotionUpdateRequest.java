package com.ebike.dto.admin.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PromotionUpdateRequest(
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
