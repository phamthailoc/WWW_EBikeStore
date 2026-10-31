package com.ebike.orderModule.dto.response;

import java.math.BigDecimal;

public record OrderItemResponse(
    Long id,
    Long productId,
    String productName,
    BigDecimal unitPrice,
    Integer quantity,
    BigDecimal lineTotal,
    Long productVariantId,
    String colorName
) {
    public OrderItemResponse(Long id, Long productId, String productName, BigDecimal unitPrice, Integer quantity, BigDecimal lineTotal) {
        this(id, productId, productName, unitPrice, quantity, lineTotal, null, null);
    }
}
