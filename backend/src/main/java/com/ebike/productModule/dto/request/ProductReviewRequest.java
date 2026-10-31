package com.ebike.productModule.dto.request;

public record ProductReviewRequest(
    Integer rating,
    String title,
    String comment
) {
}
