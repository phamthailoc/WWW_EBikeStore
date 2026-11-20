package com.ebike.dto.product.response;

import java.time.LocalDateTime;

public record StockAlertResponse(
    Long id,
    Long productId,
    String productName,
    String productSlug,
    Long variantId,
    String colorName,
    Integer stockQuantity,
    LocalDateTime createdAt,
    LocalDateTime notifiedAt
) {
}
