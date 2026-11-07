package com.ebike.dto.product.response;

import java.time.LocalDateTime;

public record ProductReviewDto(
    Long id,
    Long productId,
    String productSlug,
    Long userId,
    String username,
    String authorName,
    Integer rating,
    String title,
    String comment,
    LocalDateTime createdAt,
    Boolean editableByCurrentUser,
    // True when the author has had this product delivered (shown as "Đã mua hàng").
    Boolean verifiedPurchase
) {
}
