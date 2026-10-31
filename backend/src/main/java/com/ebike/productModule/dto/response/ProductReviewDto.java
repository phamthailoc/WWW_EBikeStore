package com.ebike.productModule.dto.response;

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
    Boolean editableByCurrentUser
) {
}
