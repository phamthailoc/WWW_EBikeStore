package com.ebike.productModule.dto.response;

import java.math.BigDecimal;

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
    Boolean defaultVariant
) {
}
