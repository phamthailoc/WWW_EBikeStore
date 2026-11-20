package com.ebike.dto.product.response;

import java.time.LocalDateTime;

/** {@code onSale} is false when the colour or the bike was withdrawn: the customer can still see and remove the request. */
public record StockAlertResponse(
    Long id,
    Long productId,
    String productName,
    String productSlug,
    Long variantId,
    String colorName,
    Integer stockQuantity,
    LocalDateTime createdAt,
    LocalDateTime notifiedAt,
    boolean onSale
) {
}
