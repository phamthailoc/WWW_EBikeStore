package com.ebike.dto.product.response;

import java.math.BigDecimal;
import java.util.List;

public record ProductVariantDto(
    Long id,
    String sku,
    String variantName,
    String colorName,
    String colorHex,
    String imageUrl,
    BigDecimal batteryCapacityAh,
    BigDecimal additionalPrice,
    Integer stockQuantity,
    Boolean defaultVariant,
    List<String> images
) {
}
