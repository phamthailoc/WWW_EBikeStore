package com.ebike.dto.product.response;

/** Whether the current visitor may review a product, and if not, why (shown in place of the form). */
public record ReviewEligibilityDto(boolean canReview, String reason) {
}
