package com.ebike.dto.admin.request;

import java.math.BigDecimal;

/** One bike as the admin form sends it. Stock is not here: it only changes through stock receipts and orders. */
public record CatalogProductRequest(
    String name,
    String slug,
    String description,
    Integer categoryId,
    BigDecimal price,
    BigDecimal discountPrice,
    Boolean featured,
    Boolean active,
    String brand,
    String modelCode,
    String vehicleType,
    String batteryType,
    BigDecimal batteryCapacityAh,
    BigDecimal batteryVoltageV,
    BigDecimal maxSpeedKmh,
    BigDecimal maxRangeKm,
    Integer motorPowerWatts,
    Integer warrantyMonths
) {
}
