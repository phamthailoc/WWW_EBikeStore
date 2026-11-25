package com.ebike.dto.admin.response;

import java.math.BigDecimal;

public record CatalogProductDetail(
    Long id,
    String name,
    String slug,
    String description,
    Integer categoryId,
    String categoryName,
    BigDecimal price,
    BigDecimal discountPrice,
    Integer stockQuantity,
    boolean featured,
    boolean active,
    String brand,
    String modelCode,
    String vehicleType,
    String batteryType,
    BigDecimal batteryCapacityAh,
    BigDecimal batteryVoltageV,
    BigDecimal maxSpeedKmh,
    BigDecimal maxRangeKm,
    Integer motorPowerWatts,
    Integer warrantyMonths,
    long orderCount,
    boolean canDelete
) {
}
