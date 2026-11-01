package com.ebike.dto.product.response;

import java.math.BigDecimal;
import java.util.List;

public record ProductSummaryDto(
    Long id,
    String name,
    String slug,
    String description,
    BigDecimal price,
    BigDecimal discountPrice,
    BigDecimal rating,
    Integer reviewCount,
    Integer stockQuantity,
    Boolean featured,
    CategorySummaryDto category,
    List<String> images
) {
}
