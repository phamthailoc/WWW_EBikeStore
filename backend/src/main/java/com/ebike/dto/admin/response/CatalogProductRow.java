package com.ebike.dto.admin.response;

import java.math.BigDecimal;

/** A line of the admin product list. {@code canDelete} is false once the bike is in an order or a test ride. */
public record CatalogProductRow(
    Long id,
    String name,
    String slug,
    Integer categoryId,
    String categoryName,
    BigDecimal price,
    BigDecimal discountPrice,
    Integer stockQuantity,
    boolean featured,
    boolean active,
    long orderCount,
    boolean canDelete
) {
}
