package com.ebike.dto.product.request;

public record ProductReviewRequest(
    Integer rating,
    String title,
    String comment
) {
}
