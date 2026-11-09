package com.ebike.dto.order.response;

import java.math.BigDecimal;
import java.util.List;

public record OrderItemResponse(
    Long id,
    Long productId,
    String productName,
    BigDecimal unitPrice,
    Integer quantity,
    BigDecimal lineTotal,
    Long productVariantId,
    String colorName,
    // V18: true for vehicles, which are sold with a frame and engine number each; units holds those entered so far
    Boolean requiresUnitNumbers,
    List<OrderItemUnitResponse> units
) {
    public OrderItemResponse(Long id, Long productId, String productName, BigDecimal unitPrice, Integer quantity, BigDecimal lineTotal) {
        this(id, productId, productName, unitPrice, quantity, lineTotal, null, null, false, List.of());
    }
}
